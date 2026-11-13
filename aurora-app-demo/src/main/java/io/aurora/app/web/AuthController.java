package io.aurora.app.web;

import com.fasterxml.jackson.databind.JsonNode;
import io.aurora.agent.audit.AuditDraft;
import io.aurora.agent.audit.AuditService;
import io.aurora.contract.Actor;
import io.aurora.contract.ApiResponse;
import io.aurora.contract.Json;
import io.aurora.rbac.AuthService;
import io.aurora.rbac.Principal;
import io.aurora.rbac.RbacViews;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuditService auditService;

    public AuthController(AuthService authService, AuditService auditService) {
        this.authService = authService;
        this.auditService = auditService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginView> login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request.username(), request.password());
        return ApiResponse.ok(new LoginView(result.token(), result.expiresAt(),
                RbacViews.PrincipalView.from(result.principal()), result.roles()));
    }

    @GetMapping("/me")
    public ApiResponse<RbacViews.SessionView> me(@RequestAttribute("token") String token) {
        return ApiResponse.ok(authService.session(token));
    }

    @PostMapping("/switch-role")
    public ApiResponse<RbacViews.SessionView> switchRole(@RequestAttribute Principal principal,
                                                        @RequestAttribute("token") String token,
                                                        @RequestBody SwitchRoleRequest request) {
        // Permission changes are audited before the session is mutated.
        auditService.record(AuditDraft.of(AuditService.TYPE_PERMISSION)
                .trace("role-" + java.util.UUID.randomUUID()).session("auth")
                .actor(Actor.user()).onBehalfOf(principal)
                .decision("SWITCH_ROLE").status("OK")
                .refs(List.of(java.util.Map.of("objectType", "role",
                        "objectId", request.roleCode()))));
        return ApiResponse.ok(authService.switchRole(token, request.roleCode()));
    }

    @PostMapping("/logout")
    public ApiResponse<Boolean> logout(@RequestAttribute("token") String token) {
        authService.logout(token);
        return ApiResponse.ok(true);
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record SwitchRoleRequest(@NotBlank String roleCode) {
    }

    public record LoginView(String token, Instant expiresAt, RbacViews.PrincipalView principal,
                            List<RbacViews.RoleView> roles) {
    }
}
