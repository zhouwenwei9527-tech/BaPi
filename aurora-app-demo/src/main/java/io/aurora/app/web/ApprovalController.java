package io.aurora.app.web;

import io.aurora.agent.approval.ApprovalService;
import io.aurora.agent.approval.ApprovalView;
import io.aurora.contract.Actor;
import io.aurora.contract.ApiResponse;
import io.aurora.contract.AuroraErrorCode;
import io.aurora.contract.AuroraException;
import io.aurora.rbac.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping
    public ApiResponse<List<ApprovalView>> list(@RequestAttribute Principal principal,
                                                @RequestParam(required = false) String status) {
        return ApiResponse.ok(approvalService.list(principal, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<ApprovalView> get(@RequestAttribute Principal principal,
                                         @PathVariable String id) {
        if (!principal.can("approval:read")) {
            throw new AuroraException(AuroraErrorCode.PERMISSION_DENIED, "缺少 approval:read 权限");
        }
        return ApiResponse.ok(approvalService.get(id));
    }

    @PostMapping("/{id}/decide")
    public ApiResponse<ApprovalService.DecisionOutcome> decide(
            @RequestAttribute Principal principal, @PathVariable String id,
            @RequestBody DecideRequest request) {
        return ApiResponse.ok(approvalService.decide(id, principal, Actor.user(),
                request.approve(), request.note(), request.confirmRisk()));
    }

    @PostMapping("/batch-decide")
    public ApiResponse<List<ApprovalService.DecisionOutcome>> batch(
            @RequestAttribute Principal principal, @RequestBody BatchDecideRequest request) {
        return ApiResponse.ok(approvalService.decideBatch(request.ids(), principal, Actor.user(),
                request.approve(), request.note(), request.confirmRisk()));
    }

    public record DecideRequest(boolean approve, String note, boolean confirmRisk) {
    }

    public record BatchDecideRequest(List<String> ids, boolean approve, String note,
                                     boolean confirmRisk) {
    }
}
