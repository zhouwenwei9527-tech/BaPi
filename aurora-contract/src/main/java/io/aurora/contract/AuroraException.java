package io.aurora.contract;

/**
 * Domain error carrying a stable machine-readable code and a safe message.
 */
public class AuroraException extends RuntimeException {

    private final AuroraErrorCode code;

    public AuroraException(AuroraErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public AuroraException(AuroraErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public AuroraErrorCode code() {
        return code;
    }
}
