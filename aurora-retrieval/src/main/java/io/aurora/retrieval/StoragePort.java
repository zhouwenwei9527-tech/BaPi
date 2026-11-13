package io.aurora.retrieval;

import java.time.Duration;

public interface StoragePort {

    boolean available();

    /** Stores an object and reports whether the write actually succeeded. */
    boolean put(String key, byte[] data, String contentType);

    /** Removes an object; missing objects are treated as deleted. */
    boolean delete(String key);

    String presign(String key, Duration ttl);

    String provider();
}
