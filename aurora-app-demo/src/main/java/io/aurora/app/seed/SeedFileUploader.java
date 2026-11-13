package io.aurora.app.seed;

import io.aurora.retrieval.DocumentRecord;
import io.aurora.retrieval.DocumentService;
import io.aurora.retrieval.StoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Uploads seeded document bodies to MinIO so presigned preview links work out
 * of the box. No-op when object storage is not configured.
 */
@Component
@Order(16)
public class SeedFileUploader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedFileUploader.class);

    private final DocumentService documentService;

    public SeedFileUploader(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Override
    public void run(ApplicationArguments args) {
        StoragePort storage = documentService.storage();
        if (!storage.available()) {
            log.info("Object storage disabled; skipping seed file upload");
            return;
        }
        int uploaded = 0;
        for (DocumentRecord doc : documentService.all()) {
            if (doc.storageKey() == null || doc.storageKey().isBlank()) {
                continue;
            }
            String body = "# " + doc.title() + "\n\n" + doc.summary() + "\n\n" + doc.content() + "\n";
            storage.put(doc.storageKey(), body.getBytes(StandardCharsets.UTF_8), doc.mimeType());
            uploaded++;
        }
        log.info("Uploaded {} seed documents to {}", uploaded, storage.provider());
    }
}
