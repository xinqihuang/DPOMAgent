package com.dpom.agent.web.controller;

import com.dpom.agent.core.handoff.HandoffErrorCode;
import com.dpom.agent.core.handoff.HandoffException;
import com.dpom.agent.core.diagnosisevent.DiagnosisReplayException;
import com.dpom.agent.web.diagnosisevent.ReplayAuthenticationException;
import com.dpom.agent.web.authorityapi.AuthorityAuthenticationException;
import com.dpom.agent.web.dto.ErrorResponse;
import com.dpom.agent.web.service.InvestigationConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 统一异常处理：稳定 code/message/investigationId，不泄漏类名、堆栈、SQL、路径、secret。
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvestigationConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(InvestigationConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("IDEMPOTENCY_CONFLICT", "idempotencyKey payload mismatch",
                        ex.investigationId()));
    }

    @ExceptionHandler(HandoffException.class)
    public ResponseEntity<ErrorResponse> handleHandoff(HandoffException ex) {
        HttpStatus status = statusFor(ex.code());
        return ResponseEntity.status(status).body(ErrorResponse.of(ex.code().name(), messageFor(ex.code())));
    }

    /** 返回不含认证细节的统一 401。 */
    @ExceptionHandler(ReplayAuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleReplayAuthentication(ReplayAuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("REPLAY_AUTHENTICATION_FAILED", "authentication failed"));
    }

    /** 返回不含 Authority token 配置或比对细节的统一 401。 */
    @ExceptionHandler(AuthorityAuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthorityAuthentication(
            AuthorityAuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("AUTHORITY_AUTHENTICATION_FAILED", "authentication failed"));
    }

    /** 映射重放的稳定业务错误。 */
    @ExceptionHandler(DiagnosisReplayException.class)
    public ResponseEntity<ErrorResponse> handleDiagnosisReplay(DiagnosisReplayException exception) {
        HttpStatus status = "REPLAY_EVENT_NOT_FOUND".equals(exception.getMessage())
                ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(ErrorResponse.of(exception.getMessage(), "replay rejected"));
    }

    private HttpStatus statusFor(HandoffErrorCode code) {
        return switch (code) {
            case NOT_APPROVED, APPROVAL_EXPIRED -> HttpStatus.FORBIDDEN;
            case NOT_ELIGIBLE -> HttpStatus.CONFLICT;
            case OBS_DISABLED, OBS_ADAPTER_UNAVAILABLE, OBS_NOT_CONFIGURED -> HttpStatus.SERVICE_UNAVAILABLE;
            case STORE_FAILURE -> HttpStatus.BAD_GATEWAY;
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
    }

    private String messageFor(HandoffErrorCode code) {
        return switch (code) {
            case NOT_APPROVED -> "explicit approval required";
            case APPROVAL_EXPIRED -> "approval expired";
            case NOT_ELIGIBLE -> "investigation not eligible for handoff";
            case OBS_DISABLED -> "obs transport disabled";
            case OBS_ADAPTER_UNAVAILABLE -> "obs adapter unavailable";
            case OBS_NOT_CONFIGURED -> "obs allow-list not configured";
            case STORE_FAILURE -> "transport failure";
            case SCHEMA_UNSUPPORTED -> "unsupported schema version";
            case CHECKSUM_MISMATCH -> "checksum mismatch";
            case SIZE_EXCEEDED -> "size limit exceeded";
            case ENTRIES_EXCEEDED -> "entries limit exceeded";
            case FORBIDDEN_CONTENT -> "forbidden content detected";
            case VERSION_MISMATCH -> "service/release/commit mismatch";
            case PACKAGE_INVALID -> "invalid package";
            case INVALID_ARGUMENT -> "invalid argument";
        };
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return ResponseEntity.status(status).body(ErrorResponse.of(codeFor(status), messageFor(status, ex)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        LOG.error("unhandled exception errorCode={}", "INTERNAL_ERROR");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "internal error"));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return new ResponseEntity<>(ErrorResponse.of(codeFor(status), defaultMessage(status)), headers, status);
    }

    private String codeFor(HttpStatus status) {
        return switch (status.value()) {
            case 400 -> "BAD_REQUEST";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            case 503 -> "CAPACITY_FULL";
            default -> "ERROR";
        };
    }

    private String messageFor(HttpStatus status, ResponseStatusException ex) {
        if (status == HttpStatus.BAD_REQUEST && ex.getReason() != null) {
            return ex.getReason();
        }
        return defaultMessage(status);
    }

    private String defaultMessage(HttpStatus status) {
        return switch (status.value()) {
            case 400 -> "bad request";
            case 404 -> "not found";
            case 405 -> "method not allowed";
            case 415 -> "unsupported media type";
            case 503 -> "capacity full";
            default -> "error";
        };
    }
}
