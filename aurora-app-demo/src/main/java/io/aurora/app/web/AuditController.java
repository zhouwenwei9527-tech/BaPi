package io.aurora.app.web;

import io.aurora.agent.AgentService;
import io.aurora.agent.audit.AuditService;
import io.aurora.agent.trace.TraceService;
import io.aurora.contract.ApiResponse;
import io.aurora.contract.AuroraErrorCode;
import io.aurora.contract.AuroraException;
import io.aurora.rbac.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;
    private final TraceService traceService;
    private final AgentService agentService;

    public AuditController(AuditService auditService, TraceService traceService,
                           AgentService agentService) {
        this.auditService = auditService;
        this.traceService = traceService;
        this.agentService = agentService;
    }

    @GetMapping
    public ApiResponse<List<AuditService.AuditEventView>> recent(
            @RequestAttribute Principal principal, @RequestParam(defaultValue = "80") int limit) {
        require(principal);
        return ApiResponse.ok(auditService.recent(limit));
    }

    @GetMapping("/verify")
    public ApiResponse<AuditService.ChainVerification> verify(@RequestAttribute Principal principal) {
        require(principal);
        return ApiResponse.ok(auditService.verifyChain());
    }

    @GetMapping("/trace/{traceId}")
    public ApiResponse<List<TraceService.TraceStep>> trace(@RequestAttribute Principal principal,
                                                          @PathVariable String traceId) {
        require(principal);
        return ApiResponse.ok(traceService.timeline(traceId));
    }

    @GetMapping("/replay/{traceId}")
    public ApiResponse<AgentService.ReplayView> replay(@RequestAttribute Principal principal,
                                                      @PathVariable String traceId) {
        require(principal);
        return ApiResponse.ok(agentService.replay(principal, traceId));
    }

    @GetMapping("/traces")
    public ApiResponse<List<TraceService.TraceSummary>> traces(@RequestAttribute Principal principal,
                                                              @RequestParam(defaultValue = "20") int limit) {
        require(principal);
        return ApiResponse.ok(traceService.recentTraces(limit));
    }

    private void require(Principal principal) {
        if (!principal.can("audit:read")) {
            throw new AuroraException(AuroraErrorCode.PERMISSION_DENIED, "缺少 audit:read 权限");
        }
    }
}
