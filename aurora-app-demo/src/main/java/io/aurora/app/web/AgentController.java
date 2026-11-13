package io.aurora.app.web;

import io.aurora.agent.AgentService;
import io.aurora.contract.ApiResponse;
import io.aurora.contract.AuroraErrorCode;
import io.aurora.contract.AuroraException;
import io.aurora.rbac.Principal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/chat")
    public ApiResponse<AgentService.AgentReply> chat(@RequestAttribute Principal principal,
                                                     @Valid @RequestBody ChatRequest request) {
        return ApiResponse.ok(agentService.chat(principal, request.sessionId(), request.message(),
                Boolean.TRUE.equals(request.confirm())));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<AgentService.SessionView>> sessions(@RequestAttribute Principal principal) {
        requireAgent(principal);
        return ApiResponse.ok(agentService.sessions(principal));
    }

    @PostMapping("/sessions")
    public ApiResponse<AgentService.SessionView> createSession(@RequestAttribute Principal principal,
                                                               @RequestBody(required = false) SessionRequest request) {
        requireAgent(principal);
        return ApiResponse.ok(agentService.createSession(principal,
                request == null ? null : request.title()));
    }

    @GetMapping("/sessions/{id}/messages")
    public ApiResponse<List<AgentService.MessageView>> messages(@RequestAttribute Principal principal,
                                                               @PathVariable String id) {
        requireAgent(principal);
        return ApiResponse.ok(agentService.messages(principal, id));
    }

    @GetMapping("/replay/{traceId}")
    public ApiResponse<AgentService.ReplayView> replay(@RequestAttribute Principal principal,
                                                       @PathVariable String traceId) {
        requireAgent(principal);
        return ApiResponse.ok(agentService.replayForUser(principal, traceId));
    }

    @GetMapping("/memories")
    public ApiResponse<List<AgentService.MemoryView>> memories(@RequestAttribute Principal principal) {
        return ApiResponse.ok(agentService.memories(principal));
    }

    @DeleteMapping("/memories/{id}")
    public ApiResponse<Boolean> deleteMemory(@RequestAttribute Principal principal,
                                             @PathVariable long id) {
        return ApiResponse.ok(agentService.deleteMemory(principal, id));
    }

    @GetMapping("/calls")
    public ApiResponse<List<AgentService.ToolCallView>> calls(@RequestAttribute Principal principal) {
        requireAgent(principal);
        return ApiResponse.ok(agentService.recentCalls(principal));
    }

    private void requireAgent(Principal principal) {
        if (!principal.can("agent:use")) {
            throw new AuroraException(AuroraErrorCode.PERMISSION_DENIED, "缺少 agent:use 权限");
        }
    }

    public record ChatRequest(String sessionId, @NotBlank String message, Boolean confirm) {
    }

    public record SessionRequest(String title) {
    }
}
