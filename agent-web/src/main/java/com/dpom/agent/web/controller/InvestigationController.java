package com.dpom.agent.web.controller;

import com.dpom.agent.web.dto.ConclusionResponse;
import com.dpom.agent.web.dto.EvidenceResponse;
import com.dpom.agent.web.dto.InvestigationResponse;
import com.dpom.agent.web.dto.InvestigationResponseMapper;
import com.dpom.agent.web.dto.InvestigationSubmitRequest;
import com.dpom.agent.web.dto.StepResponse;
import com.dpom.agent.web.service.InvestigationApplicationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 调查服务 REST 控制器。领域对象一律经 Mapper 转 DTO，不泄漏领域类型。
 */
@RestController
@RequestMapping("/api/v1/investigations")
public class InvestigationController {

    private final InvestigationApplicationService service;
    private final InvestigationResponseMapper mapper;
    private final boolean admissionEnabled;

    public InvestigationController(InvestigationApplicationService service, InvestigationResponseMapper mapper) {
        this(service, mapper, true);
    }

    @Autowired
    public InvestigationController(InvestigationApplicationService service, InvestigationResponseMapper mapper,
            @Value("${dpom.investigation.legacy-admission-enabled:false}") boolean admissionEnabled) {
        this.service = service;
        this.mapper = mapper;
        this.admissionEnabled = admissionEnabled;
    }

    @PostMapping
    public ResponseEntity<InvestigationResponse> submit(@RequestBody InvestigationSubmitRequest request) {
        if (!admissionEnabled) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.GONE,
                    "LEGACY_AUTHORITY_RETIRED");
        }
        long id = service.submit(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.summary(id));
    }

    @GetMapping("/{id}")
    public InvestigationResponse get(@PathVariable("id") long id) {
        return service.summary(id);
    }

    @GetMapping("/{id}/steps")
    public List<StepResponse> steps(@PathVariable("id") long id) {
        return service.steps(id).stream().map(mapper::toStep).toList();
    }

    @GetMapping("/{id}/evidence")
    public EvidenceResponse evidence(@PathVariable("id") long id) {
        return service.evidence(id).map(mapper::toEvidence).orElseGet(EvidenceResponse::notReady);
    }

    @GetMapping("/{id}/conclusion")
    public ConclusionResponse conclusion(@PathVariable("id") long id) {
        return service.conclusion(id).map(mapper::toConclusion).orElseGet(mapper::notReadyConclusion);
    }
}
