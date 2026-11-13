package io.aurora.retrieval;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * MinIO object storage. AI access goes through short-lived presigned URLs
 * (default 5 minutes), never a permanent direct link (PRD §5.6).
 */
@Component
@ConditionalOnProperty(name = "aurora.storage.provider", havingValue = "minio")
public class MinioStorage implements StoragePort {

    private static final Logger log = LoggerFactory.getLogger(MinioStorage.class);

    private final String endpoint;
    private final String accessKey;
    private final String secretKey;
    private final String bucket;
    private MinioClient client;
    private volatile boolean available;

    public MinioStorage(@Value("${aurora.storage.minio.endpoint:http://localhost:9000}") String endpoint,
                        @Value("${aurora.storage.minio.access-key:aurora_minio}") String accessKey,
                        @Value("${aurora.storage.minio.secret-key:aurora_minio_pw}") String secretKey,
                        @Value("${aurora.storage.minio.bucket:aurora-files}") String bucket) {
        this.endpoint = endpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.bucket = bucket;
    }

    @PostConstruct
    void init() {
        try {
            client = MinioClient.builder().endpoint(endpoint)
                    .credentials(accessKey, secretKey).build();
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            available = true;
            log.info("MinIO storage ready at {} bucket={}", endpoint, bucket);
        } catch (Exception e) {
            available = false;
            log.warn("MinIO unavailable ({}), file previews will be disabled: {}", endpoint, e.getMessage());
        }
    }

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public boolean put(String key, byte[] data, String contentType) {
        if (!available) {
            return false;
        }
        try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
                    .stream(in, data.length, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
            return true;
        } catch (Exception e) {
            log.warn("MinIO put failed for {}: {}", key, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean delete(String key) {
        if (!available || key == null || key.isBlank()) {
            return false;
        }
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
            return true;
        } catch (Exception e) {
            log.warn("MinIO delete failed for {}: {}", key, e.getMessage());
            return false;
        }
    }

    @Override
    public String presign(String key, Duration ttl) {
        if (!available || key == null || key.isBlank()) {
            return null;
        }
        try {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET).bucket(bucket).object(key)
                    .expiry((int) Math.max(1, ttl.toMinutes()), TimeUnit.MINUTES)
                    .build());
        } catch (Exception e) {
            log.warn("MinIO presign failed for {}: {}", key, e.getMessage());
            return null;
        }
    }

    @Override
    public String provider() {
        return "minio";
    }
}
