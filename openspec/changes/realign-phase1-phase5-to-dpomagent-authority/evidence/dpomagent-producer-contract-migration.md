# DPOMAgent producer-owned contract migration

Date: 2026-08-27

DPOMAgent now contains repository-local, versioned assets for Diagnosis Event
v1/v2, Diagnosis Progress v1, Evidence Manifest v1, Diagnostic Report v1 and
their Kafka transport semantics under `contracts/`.

The import records the former `AISREPlatformContracts` source commit, all 38
asset SHA-256 values and one aggregate SHA-256 over the checksum inventory.
During migration, superseded v2 producer/source-authority values, progress
authority, report provenance and Kafka ACL/authority text were corrected from
DPOMBase to DPOMAgent. Fixture canonical byte counts, canonical SHA-256 values
and report digests were regenerated after the correction.

Focused conformance result: PASS; 7 tests, 0 failures, 0 errors. Coverage
includes all v1 positive/negative Diagnosis Event cases, all v2 Event/Progress/
Evidence Manifest positive and negative corpora, RFC 8785 canonical vectors,
HTTP/Kafka transport neutrality, DPOMAgent source-authority provenance and all
38 repository asset checksums. Maven Checkstyle reported 0 violations.
