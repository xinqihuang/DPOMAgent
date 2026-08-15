package com.dpom.agent.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.dpom.agent.web.controller.GlobalExceptionHandler;
import com.dpom.agent.web.dto.ErrorResponse;
import com.dpom.agent.web.service.InvestigationConflictException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void conflictCarriesInvestigationId() {
        ResponseEntity<ErrorResponse> resp = handler.handleConflict(new InvestigationConflictException(42));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().code()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(resp.getBody().investigationId()).isEqualTo(42L);
    }

    @Test
    void capacityFullMapsToStableCode() {
        ResponseEntity<ErrorResponse> resp = handler.handleStatus(
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "capacity full"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(resp.getBody().code()).isEqualTo("CAPACITY_FULL");
    }

    @Test
    void badRequestKeepsValidationMessage() {
        ResponseEntity<ErrorResponse> resp = handler.handleStatus(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "serviceCode invalid"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().code()).isEqualTo("BAD_REQUEST");
        assertThat(resp.getBody().message()).isEqualTo("serviceCode invalid");
    }

    @Test
    void genericExceptionLeaksNothing() {
        ResponseEntity<ErrorResponse> resp = handler.handleGeneric(
                new RuntimeException("secret SELECT * FROM users at com.Foo.main(Foo.java:1)"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(resp.getBody().message()).isEqualTo("internal error");
    }

    @Test
    void genericExceptionDoesNotLogThrowableOrMessage() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            String secret = "SECRET_SENTINEL hunter2 SELECT * FROM secret_users C:\\secret\\path\\key.txt";
            ResponseEntity<ErrorResponse> resp = handler.handleGeneric(new RuntimeException(secret));
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(resp.getBody().code()).isEqualTo("INTERNAL_ERROR");

            String all = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.joining("\n"));
            assertThat(all).contains("INTERNAL_ERROR");
            assertThat(all).doesNotContain("SECRET_SENTINEL", "hunter2", "SELECT * FROM secret_users",
                    "C:\\secret", "RuntimeException", "at com.dpom");
            assertThat(appender.list).allMatch(event -> event.getThrowableProxy() == null);
        } finally {
            logger.detachAppender(appender);
        }
    }
}
