package com.dpom.agent.web.controller;

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
