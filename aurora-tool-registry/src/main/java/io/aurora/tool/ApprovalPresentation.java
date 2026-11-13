package io.aurora.tool;

import java.util.List;

/**
 * Human-readable approval context plus a risk/operability score.
 * operability: 0..100 where 100 = safe and routine, 0 = extreme risk.
 */
public record ApprovalPresentation(
        String intent,
        int operability,
        String riskLevel,
        List<String> riskReasons,
        boolean requiresConfirmation
) {

    public ApprovalPresentation {
        operability = Math.max(0, Math.min(100, operability));
        riskLevel = riskLevel == null ? levelFor(operability) : riskLevel;
        riskReasons = riskReasons == null ? List.of() : List.copyOf(riskReasons);
        requiresConfirmation = requiresConfirmation || operability < 60;
    }

    public static ApprovalPresentation generic() {
        return new ApprovalPresentation("执行一次受控操作", 80, levelFor(80), List.of(), false);
    }

    public static ApprovalPresentation of(String intent, int operability, List<String> reasons) {
        return new ApprovalPresentation(intent, operability, levelFor(operability), reasons,
                operability < 60);
    }

    public static String levelFor(int operability) {
        if (operability >= 85) return "LOW";
        if (operability >= 60) return "MEDIUM";
        if (operability >= 30) return "HIGH";
        return "CRITICAL";
    }
}
