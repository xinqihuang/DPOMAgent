package com.dpom.agent.web.controller;

import com.dpom.agent.core.script.ScriptArtifact;
import com.dpom.agent.core.script.ScriptArtifactService;
import com.dpom.agent.core.script.ScriptResultService;
import com.dpom.agent.core.script.ScriptType;
import com.dpom.agent.web.dto.CreateScriptRequest;
import com.dpom.agent.web.dto.ScriptResultRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 脚本工件 REST 控制器。
 */
@RestController
@RequestMapping("/api/v1")
public class ScriptArtifactController {

    private final ScriptArtifactService scriptArtifactService;
    private final ScriptResultService scriptResultService;

    /**
     * 构造器注入。
     *
     * @param scriptArtifactService 脚本工件服务
     * @param scriptResultService   脚本结果服务
     */
    public ScriptArtifactController(ScriptArtifactService scriptArtifactService,
                                    ScriptResultService scriptResultService) {
        this.scriptArtifactService = scriptArtifactService;
        this.scriptResultService = scriptResultService;
    }

    /**
     * 创建脚本工件。
     *
     * @param investigationId 调查 id
     * @param request         请求体
     * @return 脚本工件
     */
    @PostMapping("/investigations/{id}/artifacts")
    public ScriptArtifact createArtifact(@PathVariable("id") long investigationId,
                                         @RequestBody CreateScriptRequest request) {
        return scriptArtifactService.create(investigationId, ScriptType.valueOf(request.type()),
                request.language(), request.purpose(), request.riskLevel(), request.preconditions(),
                request.verification(), request.rollback(), request.content(),
                request.hypothesesToValidate(), request.expectedOutput(), request.instructions());
    }

    /**
     * 回传脚本结果并恢复调查。
     *
     * @param investigationId 调查 id
     * @param scriptId        脚本 id
     * @param request         请求体
     * @return 新观察 id
     */
    @PostMapping("/investigations/{id}/scripts/{scriptId}/result")
    public Map<String, Long> submitResult(@PathVariable("id") long investigationId,
                                          @PathVariable long scriptId,
                                          @RequestBody ScriptResultRequest request) {
        long observationId = scriptResultService.submitResult(investigationId, scriptId, request.summary());
        return Map.of("observationId", observationId);
    }

    /**
     * 查询脚本工件。
     *
     * @param scriptId 脚本 id
     * @return 脚本工件
     */
    @GetMapping("/scripts/{scriptId}")
    public ScriptArtifact getScript(@PathVariable long scriptId) {
        return scriptArtifactService.findById(scriptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "脚本不存在"));
    }
}
