package com.dpom.agent.web.authorityapi;

import com.dpom.agent.core.authority.AuthorityId;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/** 鉴权、默认关闭的 Investigation 权威只读 API。 */
@RestController
@RequestMapping("/internal/v1/investigations")
public class AuthorityReadController {

    private final AuthorityReadAuthenticator authenticator;
    private final AuthorityReadService service;

    /** 创建控制器。 */
    public AuthorityReadController(AuthorityReadAuthenticator authenticator, AuthorityReadService service) {
        this.authenticator = authenticator;
        this.service = service;
    }

    /** 返回游标分页进度。 */
    @GetMapping("/{investigationId}/progress")
    public AuthorityProgressPage progress(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
            String authorization, @PathVariable String investigationId,
            @RequestParam(defaultValue = "0") long after, @RequestParam(defaultValue = "50") int limit) {
        authenticator.authenticate(authorization);
        return service.progress(authorityId(investigationId), after, limit);
    }

    /** 以审计序号作为 SSE id 返回可重放的有界进度批次。 */
    @GetMapping(path = "/{investigationId}/progress/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter progressStream(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
            String authorization, @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
            @PathVariable String investigationId, @RequestParam(defaultValue = "0") long after,
            @RequestParam(defaultValue = "50") int limit) throws IOException {
        authenticator.authenticate(authorization);
        long replayAfter = lastEventId == null ? after : parseSequence(lastEventId);
        AuthorityProgressPage page = service.progress(authorityId(investigationId), replayAfter, limit);
        SseEmitter emitter = new SseEmitter(5_000L);
        for (AuthorityProgressItem item : page.items()) {
            emitter.send(SseEmitter.event().id(Long.toString(item.sequence()))
                    .name(item.kind()).data(item));
        }
        emitter.send(SseEmitter.event().name("checkpoint").data(Map.of(
                "nextAfter", page.nextAfter(), "hasMore", page.hasMore(),
                "aggregateVersion", page.aggregateVersion(), "status", page.status())));
        emitter.complete();
        return emitter;
    }

    /** 返回校验后的不可变诊断源视图。 */
    @GetMapping("/{investigationId}/diagnosis-source")
    public DiagnosisSourceView diagnosisSource(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable String investigationId) {
        authenticator.authenticate(authorization);
        return service.diagnosisSource(authorityId(investigationId));
    }

    private static AuthorityId authorityId(String value) {
        try {
            return new AuthorityId(value);
        } catch (IllegalArgumentException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "invalid investigation id");
        }
    }

    private static long parseSequence(String value) {
        try {
            long sequence = Long.parseLong(value);
            if (sequence < 0) {
                throw new NumberFormatException();
            }
            return sequence;
        } catch (NumberFormatException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "invalid Last-Event-ID");
        }
    }
}
