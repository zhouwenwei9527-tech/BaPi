package io.aurora.tool;

public enum ApprovalPolicy {
    /** Execute without approval; only legal for READ + idempotent (INV-4 / §5.2). */
    AUTO,
    /**
     * The caller already holds the required permission. Routine writes execute
     * immediately; low-operability actions ask the caller to confirm once.
     * No second person is involved.
     */
    SELF_CONFIRM,
    /** Dual control: a different human must approve (used for role escalation). */
    ASK,
    /** Registered but denied for everyone until an administrator opts in. */
    DENY_BY_DEFAULT
}
