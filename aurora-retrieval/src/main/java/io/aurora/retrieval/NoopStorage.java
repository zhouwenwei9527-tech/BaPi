package io.aurora.retrieval;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Fallback storage when MinIO is not configured. Previews are unavailable but
 * retrieval remains fully functional.
 */
@Component
@ConditionalOnProperty(name = "aurora.storage.provider", havingValue = "none", matchIfMissing = true)
public class NoopStorage implements StoragePort {

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public boolean put(String key, byte[] data, String contentType) {
        // Intentionally no-op.
        return false;
    }

    @Override
    public boolean delete(String key) {
        return false;
    }

    @Override
    public String presign(String key, Duration ttl) {
        return null;
    }

    @Override
    public String provider() {
        return "none";
    }
}
