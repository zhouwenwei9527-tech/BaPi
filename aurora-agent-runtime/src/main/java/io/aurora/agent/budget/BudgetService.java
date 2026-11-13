package io.aurora.agent.budget;

import io.aurora.contract.AuroraErrorCode;
import io.aurora.contract.AuroraException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-session tool-call, token and wall-clock budget with a failure circuit
 * breaker (PRD §6 / §8, AC-27).
 */
@Service
public class BudgetService {

    private final int maxToolCalls;
    private final int maxTokens;
    private final long maxWallClockMs;
    private final int maxConsecutiveFailures;
    private final Map<String, State> states = new ConcurrentHashMap<>();

    public BudgetService(@Value("${aurora.agent.budget.maxToolCalls:20}") int maxToolCalls,
                         @Value("${aurora.agent.budget.maxTokens:60000}") int maxTokens,
                         @Value("${aurora.agent.budget.maxWallClockMs:120000}") long maxWallClockMs,
                         @Value("${aurora.agent.budget.maxConsecutiveFailures:3}") int maxConsecutiveFailures) {
        this.maxToolCalls = maxToolCalls;
        this.maxTokens = maxTokens;
        this.maxWallClockMs = maxWallClockMs;
        this.maxConsecutiveFailures = maxConsecutiveFailures;
    }

    public void check(String sessionId) {
        State state = states.computeIfAbsent(sessionId, key -> new State());
        if (state.toolCalls >= maxToolCalls) {
            throw new AuroraException(AuroraErrorCode.BUDGET_EXCEEDED,
                    "会话 Tool 调用已达上限 " + maxToolCalls);
        }
        if (state.tokens >= maxTokens) {
            throw new AuroraException(AuroraErrorCode.BUDGET_EXCEEDED,
                    "会话 Token 预算已达上限 " + maxTokens);
        }
        if (System.currentTimeMillis() - state.startedAt > maxWallClockMs) {
            throw new AuroraException(AuroraErrorCode.BUDGET_EXCEEDED,
                    "会话已超过墙钟预算 " + maxWallClockMs + "ms");
        }
        if (state.consecutiveFailures >= maxConsecutiveFailures) {
            throw new AuroraException(AuroraErrorCode.CIRCUIT_OPEN,
                    "连续失败达到 " + maxConsecutiveFailures + " 次，熔断保护已开启");
        }
    }

    public void consumeCall(String sessionId) {
        State state = states.computeIfAbsent(sessionId, key -> new State());
        state.toolCalls++;
    }

    public void recordTokens(String sessionId, int tokens) {
        State state = states.computeIfAbsent(sessionId, key -> new State());
        state.tokens += Math.max(0, tokens);
    }

    public void recordSuccess(String sessionId) {
        State state = states.computeIfAbsent(sessionId, key -> new State());
        state.consecutiveFailures = 0;
    }

    public void recordFailure(String sessionId) {
        State state = states.computeIfAbsent(sessionId, key -> new State());
        state.consecutiveFailures++;
    }

    public BudgetState state(String sessionId) {
        State state = states.computeIfAbsent(sessionId, key -> new State());
        return new BudgetState(state.toolCalls, maxToolCalls, state.tokens, maxTokens,
                System.currentTimeMillis() - state.startedAt, maxWallClockMs,
                state.consecutiveFailures, maxConsecutiveFailures);
    }

    public record BudgetState(int toolCalls, int maxToolCalls, int tokens, int maxTokens,
                              long wallClockMs, long maxWallClockMs, int consecutiveFailures,
                              int maxConsecutiveFailures) {
        public boolean exhausted() {
            return toolCalls >= maxToolCalls || tokens >= maxTokens
                    || wallClockMs >= maxWallClockMs || consecutiveFailures >= maxConsecutiveFailures;
        }
    }

    private static final class State {
        final long startedAt = System.currentTimeMillis();
        int toolCalls;
        int tokens;
        int consecutiveFailures;
    }
}
