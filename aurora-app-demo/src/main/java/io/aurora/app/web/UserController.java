package io.aurora.app.web;

import io.aurora.agent.audit.AuditDraft;
import io.aurora.agent.audit.AuditService;
import io.aurora.contract.Actor;
import io.aurora.contract.ApiResponse;
import io.aurora.contract.Json;
import io.aurora.contract.AuroraErrorCode;
import io.aurora.contract.AuroraException;
import io.aurora.rbac.Principal;
import io.aurora.rbac.RbacViews;
import io.aurora.rbac.UserQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserQueryService userQueryService;
    private final AuditService auditService;

    public UserController(UserQueryService userQueryService, AuditService auditService) {
        this.userQueryService = userQueryService;
        this.auditService = auditService;
    }

    /** Self-service profile changes are direct; no approval is required. */
    @PatchMapping("/me/profile")
    public ApiResponse<RbacViews.UserView> updateMyProfile(@RequestAttribute Principal principal,
                                                           @RequestBody ProfileRequest request) {
        auditService.record(AuditDraft.of(AuditService.TYPE_PERMISSION)
                .trace("profile-" + java.util.UUID.randomUUID()).session("profile")
                .actor(Actor.user()).onBehalfOf(principal)
                .decision("PROFILE_UPDATED").status("OK")
                .refs(java.util.List.of(java.util.Map.of("objectType", "user",
                        "objectId", String.valueOf(principal.userId())))));
        return ApiResponse.ok(userQueryService.updateOwnProfile(principal, request.displayName(),
                request.title()));
    }

    public record ProfileRequest(String displayName, String title) {
    }

    @GetMapping
    public ApiResponse<UserQueryService.UserPage> list(@RequestAttribute Principal principal,
                                                       @RequestParam(defaultValue = "") String q,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        requireRead(principal);
        return ApiResponse.ok(userQueryService.searchPage(principal, q, page, size,
                principal.can("user:update")));
    }

    @GetMapping("/meta")
    public ApiResponse<UserQueryService.UserMeta> meta(@RequestAttribute Principal principal) {
        requireRead(principal);
        return ApiResponse.ok(userQueryService.meta(principal));
    }

    private void requireRead(Principal principal) {
        if (!principal.can("user:read")) {
            throw new AuroraException(AuroraErrorCode.PERMISSION_DENIED, "缺少 user:read 权限");
        }
    }
}
