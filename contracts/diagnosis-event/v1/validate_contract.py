#!/usr/bin/env python3
"""离线验证 Diagnosis Event v1 schema 与 fixtures。"""

from __future__ import annotations

import copy
import hashlib
import json
import re
import sys
from datetime import datetime
from pathlib import Path
from typing import Any

try:
    from jsonschema import Draft202012Validator, FormatChecker
    from jsonschema.exceptions import SchemaError
except ImportError as exc:  # pragma: no cover - environment setup guard
    raise SystemExit("Missing offline dependency: install Python package 'jsonschema'.") from exc


CONTRACT_DIR = Path(__file__).resolve().parent
SCHEMA_PATH = CONTRACT_DIR / "diagnosis-event.schema.json"
VALID_DIR = CONTRACT_DIR / "fixtures" / "valid"
INVALID_CASES_PATH = CONTRACT_DIR / "fixtures" / "invalid" / "cases.json"
MAX_EVENT_BYTES = 64 * 1024
MAX_INLINE_BYTES = 16 * 1024

FORBIDDEN_KEYS = {
    "accesskey",
    "accesskeyid",
    "authorization",
    "broker",
    "bucket",
    "consumergroup",
    "cookie",
    "objectkey",
    "offset",
    "partition",
    "password",
    "secret",
    "secretkey",
    "sdktype",
    "token",
    "topic",
}
FORBIDDEN_SCHEMA_PROPERTIES = FORBIDDEN_KEYS | {"endpoint", "request", "response"}
WINDOWS_PATH = re.compile(r"^(?:[A-Za-z]:[\\/]|\\\\)")
PROVIDER_SDK = re.compile(r"(?:huaweicloud[.]sdk|huaweicloud-sdk|com[.]huawei[.].*[.]model[.])", re.IGNORECASE)


class ContractFailure(Exception):
    """携带稳定契约错误码的验证失败。"""

    def __init__(self, code: str, detail: str) -> None:
        super().__init__(detail)
        self.code = code


def load_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8") as stream:
        return json.load(stream)


def compact_json_bytes(value: Any) -> bytes:
    """Fixtures 使用的确定性 JSON；生产 content hash 必须使用 README 规定的 RFC 8785。"""
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def content_hash(value: Any) -> str:
    return hashlib.sha256(compact_json_bytes(value)).hexdigest()


def decode_pointer(path: str) -> list[str]:
    if not path.startswith("/"):
        raise ValueError(f"JSON Pointer must start with '/': {path}")
    return [part.replace("~1", "/").replace("~0", "~") for part in path[1:].split("/")]


def pointer_parent(document: Any, path: str) -> tuple[Any, str]:
    parts = decode_pointer(path)
    if not parts:
        raise ValueError("Mutation of the document root is not supported")
    current = document
    for part in parts[:-1]:
        current = current[int(part)] if isinstance(current, list) else current[part]
    return current, parts[-1]


def pointer_get(document: Any, path: str) -> Any:
    current = document
    for part in decode_pointer(path):
        current = current[int(part)] if isinstance(current, list) else current[part]
    return current


def pointer_set(document: Any, path: str, value: Any) -> None:
    parent, leaf = pointer_parent(document, path)
    if isinstance(parent, list):
        parent[int(leaf)] = value
    else:
        parent[leaf] = value


def pointer_remove(document: Any, path: str) -> None:
    parent, leaf = pointer_parent(document, path)
    if isinstance(parent, list):
        del parent[int(leaf)]
    else:
        del parent[leaf]


def apply_operations(document: Any, operations: list[dict[str, Any]], fixtures: dict[str, Any]) -> Any:
    mutated = copy.deepcopy(document)
    for operation in operations:
        op = operation["op"]
        if op == "remove":
            pointer_remove(mutated, operation["path"])
        elif op == "set":
            pointer_set(mutated, operation["path"], operation["value"])
        elif op == "repeat":
            pointer_set(mutated, operation["path"], operation["value"] * operation["count"])
        elif op == "copy":
            source = fixtures[operation["fromFixture"]]
            pointer_set(mutated, operation["path"], copy.deepcopy(pointer_get(source, operation["fromPath"])))
        else:
            raise ValueError(f"Unsupported fixture operation: {op}")
    return mutated


def scan_security(value: Any, path: str = "$") -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            normalized = re.sub(r"[^a-z0-9]", "", key.casefold())
            if normalized in FORBIDDEN_KEYS:
                raise ContractFailure("SECURITY_BOUNDARY_VIOLATION", f"forbidden field at {path}")
            scan_security(child, f"{path}.{key}")
    elif isinstance(value, list):
        for index, child in enumerate(value):
            scan_security(child, f"{path}[{index}]")
    elif isinstance(value, str):
        if WINDOWS_PATH.search(value) or "\\" in value or "../" in value or "..\\" in value:
            raise ContractFailure("SECURITY_BOUNDARY_VIOLATION", f"forbidden path at {path}")
        if PROVIDER_SDK.search(value):
            raise ContractFailure("SECURITY_BOUNDARY_VIOLATION", f"provider SDK type at {path}")


def validate_bounds(event: dict[str, Any]) -> None:
    if len(compact_json_bytes(event)) > MAX_EVENT_BYTES:
        raise ContractFailure("PAYLOAD_TOO_LARGE", "event exceeds 64 KiB")
    inline = event.get("inlinePayload")
    if inline is not None and len(compact_json_bytes(inline)) > MAX_INLINE_BYTES:
        raise ContractFailure("PAYLOAD_TOO_LARGE", "inline payload exceeds 16 KiB")


def validate_timestamp(value: Any, field: str) -> None:
    if not isinstance(value, str):
        raise ContractFailure("CONTRACT_VALIDATION_FAILED", f"{field} must be a timestamp")
    normalized = value[:-1] + "+00:00" if value.endswith("Z") else value
    try:
        parsed = datetime.fromisoformat(normalized)
    except ValueError as exc:
        raise ContractFailure("CONTRACT_VALIDATION_FAILED", f"{field} is not ISO-8601") from exc
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        raise ContractFailure("CONTRACT_VALIDATION_FAILED", f"{field} must include an offset")


def validate_event(event: dict[str, Any], validator: Draft202012Validator) -> None:
    version = event.get("schemaVersion")
    if isinstance(version, str) and not version.startswith("1."):
        raise ContractFailure("UNSUPPORTED_SCHEMA", "unsupported schema major")
    scan_security(event)
    validate_bounds(event)
    validate_timestamp(event.get("occurredAt"), "occurredAt")
    artifact = event.get("artifactRef")
    if isinstance(artifact, dict):
        validate_timestamp(artifact.get("createdAt"), "artifactRef.createdAt")
    errors = sorted(validator.iter_errors(event), key=lambda error: list(error.absolute_path))
    if errors:
        raise ContractFailure("CONTRACT_VALIDATION_FAILED", errors[0].message)


def collect_schema_property_names(value: Any) -> set[str]:
    names: set[str] = set()
    if isinstance(value, dict):
        properties = value.get("properties")
        if isinstance(properties, dict):
            names.update(key.casefold() for key in properties)
        for child in value.values():
            names.update(collect_schema_property_names(child))
    elif isinstance(value, list):
        for child in value:
            names.update(collect_schema_property_names(child))
    return names


def verify_neutral_schema(schema: dict[str, Any]) -> None:
    property_names = collect_schema_property_names(schema)
    forbidden = sorted(property_names & FORBIDDEN_SCHEMA_PROPERTIES)
    if forbidden:
        raise AssertionError(f"schema exposes forbidden transport/provider/storage fields: {forbidden}")
    serialized = json.dumps(schema, ensure_ascii=False).casefold()
    for marker in ("huaweicloud.sdk", "com.huawei", "kafkarecord", "showmetricdatarequest"):
        if marker in serialized:
            raise AssertionError(f"schema contains provider or broker marker: {marker}")


def run() -> int:
    schema = load_json(SCHEMA_PATH)
    try:
        Draft202012Validator.check_schema(schema)
    except SchemaError as exc:
        raise AssertionError(f"invalid JSON Schema: {exc.message}") from exc

    validator = Draft202012Validator(schema, format_checker=FormatChecker())
    fixtures = {path.name: load_json(path) for path in sorted(VALID_DIR.glob("*.json"))}
    for name, event in fixtures.items():
        try:
            validate_event(event, validator)
        except ContractFailure as exc:
            raise AssertionError(f"positive fixture {name} failed with {exc.code}: {exc}") from exc

    invalid_manifest = load_json(INVALID_CASES_PATH)
    invalid_cases = invalid_manifest["cases"]
    for case in invalid_cases:
        original = fixtures[case["base"]]
        mutated = apply_operations(original, case["operations"], fixtures)
        actual_error: str | None = None
        if case.get("mode") == "idempotency-conflict":
            validate_event(original, validator)
            validate_event(mutated, validator)
            if original["idempotencyKey"] == mutated["idempotencyKey"] \
                    and content_hash(original) != content_hash(mutated):
                actual_error = "IDEMPOTENCY_CONFLICT"
        else:
            try:
                validate_event(mutated, validator)
            except ContractFailure as exc:
                actual_error = exc.code
        if actual_error != case["expectedError"]:
            raise AssertionError(
                f"negative fixture {case['name']} expected {case['expectedError']}, got {actual_error or 'ACCEPTED'}"
            )

    verify_neutral_schema(schema)
    print(
        "Diagnosis Event v1 validation passed: "
        f"schema=1, positive={len(fixtures)}, negative={len(invalid_cases)}, staticChecks=1"
    )
    return 0


if __name__ == "__main__":
    try:
        sys.exit(run())
    except (AssertionError, OSError, ValueError, json.JSONDecodeError) as error:
        print(f"Diagnosis Event v1 validation failed: {error}", file=sys.stderr)
        sys.exit(1)
