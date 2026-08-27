package com.dpom.agent.web.authority;

import com.dpom.agent.core.authority.AuthorityId;
import com.dpom.agent.core.authority.DiagnosisSourceProjection;
import com.dpom.agent.core.authority.InvestigationAuthority;
import com.dpom.agent.core.investigation.InvestigationStatus;
import com.dpom.agent.core.persistence.authority.AuthorityAuditViewRow;
import com.dpom.agent.core.persistence.authority.AuthorityHeadRow;
import com.dpom.agent.core.persistence.authority.AuthorityTerminalDao;
import com.dpom.agent.core.persistence.authority.DiagnosisSourceRow;
import com.dpom.agent.core.persistence.authority.InvestigationAuthorityDao;
import com.dpom.agent.web.authorityapi.AuthorityReadAuthenticator;
import com.dpom.agent.web.authorityapi.AuthorityReadController;
import com.dpom.agent.web.authorityapi.AuthorityReadService;
import com.dpom.agent.web.controller.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Authority 只读 API 的鉴权、分页、SSE 重放和脱敏测试。 */
class AuthorityReadControllerTest {

    private static final String TOKEN = "authority-read-contract-token-00000001";
    private static final AuthorityId INVESTIGATION_ID = AuthorityId.derive("investigation", "api-test");

    MockMvc mockMvc;

    ObjectMapper objectMapper;

    InvestigationAuthorityDao authorityDao;

    AuthorityTerminalDao terminalDao;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        authorityDao = mock(InvestigationAuthorityDao.class);
        terminalDao = mock(AuthorityTerminalDao.class);
        AuthorityReadService service = new AuthorityReadService(authorityDao, terminalDao, objectMapper);
        AuthorityReadController controller = new AuthorityReadController(
                new AuthorityReadAuthenticator(TOKEN, true), service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void rejectsMissingAuthenticationBeforeDatabaseAccess() throws Exception {
        mockMvc.perform(get(path("/progress")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHORITY_AUTHENTICATION_FAILED"));

        verifyNoInteractions(authorityDao, terminalDao);
    }

    @Test
    void rejectsUnboundedPagination() throws Exception {
        mockMvc.perform(get(path("/progress")).header(HttpHeaders.AUTHORIZATION, bearer())
                        .queryParam("limit", "101"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authorityDao, terminalDao);
    }

    @Test
    void returnsBoundedCursorPage() throws Exception {
        when(authorityDao.findHead(INVESTIGATION_ID.value())).thenReturn(Optional.of(head(3L)));
        when(authorityDao.findAuditPage(INVESTIGATION_ID.value(), 0L, 3))
                .thenReturn(List.of(audit(1L), audit(2L), audit(3L)));

        mockMvc.perform(get(path("/progress")).header(HttpHeaders.AUTHORIZATION, bearer())
                        .queryParam("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.nextAfter").value(2))
                .andExpect(jsonPath("$.hasMore").value(true));
    }

    @Test
    void replaysSseAfterLastEventIdWithStableEventIds() throws Exception {
        when(authorityDao.findHead(INVESTIGATION_ID.value())).thenReturn(Optional.of(head(2L)));
        when(authorityDao.findAuditPage(INVESTIGATION_ID.value(), 1L, 3))
                .thenReturn(List.of(audit(2L)));

        MvcResult pending = mockMvc.perform(get(path("/progress/stream"))
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .header("Last-Event-ID", "1").queryParam("limit", "2"))
                .andExpect(request().asyncStarted()).andReturn();

        mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id:2")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:checkpoint")));
        verify(authorityDao).findAuditPage(INVESTIGATION_ID.value(), 1L, 3);
    }

    @Test
    void verifiesImmutableDocumentAndRedactsCredentialText() throws Exception {
        DiagnosisSourceProjection source = sourceWithCredentialText();
        String json = objectMapper.writeValueAsString(source);
        DiagnosisSourceRow row = new DiagnosisSourceRow(source.sourceId().value(), INVESTIGATION_ID.value(),
                source.aggregateVersion(), source.contractVersion(), json, source.sourceDigest(),
                sha256(json), LocalDateTime.of(2026, 8, 27, 1, 0));
        when(terminalDao.findSource(INVESTIGATION_ID.value())).thenReturn(Optional.of(row));

        mockMvc.perform(get(path("/diagnosis-source")).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redacted").value(true))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("[REDACTED]")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("super-secret-token"))))
                .andExpect(jsonPath("$.source.sourceDigest").value("a".repeat(64)));
    }

    @Test
    void rejectsTamperedSourceWithoutEchoingStoredContent() throws Exception {
        DiagnosisSourceProjection source = sourceWithCredentialText();
        String json = objectMapper.writeValueAsString(source);
        DiagnosisSourceRow row = new DiagnosisSourceRow(source.sourceId().value(), INVESTIGATION_ID.value(),
                source.aggregateVersion(), source.contractVersion(), json + " ", source.sourceDigest(),
                sha256(json), LocalDateTime.of(2026, 8, 27, 1, 0));
        when(terminalDao.findSource(INVESTIGATION_ID.value())).thenReturn(Optional.of(row));

        mockMvc.perform(get(path("/diagnosis-source")).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("super-secret-token"))));
    }

    private static AuthorityHeadRow head(long version) {
        LocalDateTime at = LocalDateTime.of(2026, 8, 27, 1, 0);
        return new AuthorityHeadRow(INVESTIGATION_ID.value(), AuthorityId.derive("incident", "api-test").value(),
                version, "RESEARCHING", null, 0, 0, 0, "{}", "b".repeat(64), at, at);
    }

    private static AuthorityAuditViewRow audit(long sequence) {
        return new AuthorityAuditViewRow(AuthorityId.derive("audit", Long.toString(sequence)).value(),
                INVESTIGATION_ID.value(), sequence, sequence, "STATUS_CHANGED", INVESTIGATION_ID.value(),
                "RESEARCHING", LocalDateTime.of(2026, 8, 27, 1, 0).plusSeconds(sequence));
    }

    private static DiagnosisSourceProjection sourceWithCredentialText() {
        AuthorityId incidentId = AuthorityId.derive("incident", "api-test");
        AuthorityId sourceId = AuthorityId.derive("diagnosis-source", INVESTIGATION_ID.value(), "6");
        AuthorityId runId = AuthorityId.derive("run", INVESTIGATION_ID.value(), "1");
        AuthorityId conclusionId = AuthorityId.derive("conclusion", INVESTIGATION_ID.value(), "1");
        return new DiagnosisSourceProjection(sourceId, "diagnosis-source/v1", INVESTIGATION_ID, incidentId,
                6L, InvestigationStatus.COMPLETED, runId, conclusionId,
                InvestigationAuthority.ConclusionDisposition.CONFIRMED,
                "Authorization: Bearer super-secret-token", List.of(), List.of(), List.of(),
                List.of(new DiagnosisSourceProjection.ComponentProvenance("DPOMAgent", "authority-v1")),
                Instant.parse("2026-08-27T01:00:00Z"), "a".repeat(64));
    }

    private static String path(String suffix) {
        return "/internal/v1/investigations/" + INVESTIGATION_ID.value() + suffix;
    }

    private static String bearer() {
        return "Bearer " + TOKEN;
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
