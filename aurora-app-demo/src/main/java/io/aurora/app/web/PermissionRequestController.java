package io.aurora.app.web;

import io.aurora.app.permission.PermissionRequestService;
import io.aurora.contract.ApiResponse;
import io.aurora.rbac.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/permission-requests")
public class PermissionRequestController {

    private final PermissionRequestService service;

    public PermissionRequestController(PermissionRequestService service) {
        this.service = service;
    }

    @GetMapping("/roles")
    public ApiResponse<List<PermissionRequestService.RoleOption>> roles(
            @RequestAttribute Principal principal) {
        return ApiResponse.ok(service.requestableRoles(principal));
    }

    @GetMapping
    public ApiResponse<List<PermissionRequestService.PermissionRequestView>> list(
            @RequestAttribute Principal principal) {
        return ApiResponse.ok(service.list(principal));
    }

    @PostMapping
    public ApiResponse<PermissionRequestService.PermissionRequestView> create(
            @RequestAttribute Principal principal, @RequestBody CreateRequest request) {
        return ApiResponse.ok(service.create(principal, request.roleCode(), request.reason()));
    }

    @PostMapping("/{id}/decide")
    public ApiResponse<PermissionRequestService.DecisionOutcome> decide(
            @RequestAttribute Principal principal, @PathVariable String id,
            @RequestBody DecideRequest request) {
        return ApiResponse.ok(service.decide(id, principal, request.approve(), request.note()));
    }

    public record CreateRequest(String roleCode, String reason) {
    }

    public record DecideRequest(boolean approve, String note) {
    }
}
