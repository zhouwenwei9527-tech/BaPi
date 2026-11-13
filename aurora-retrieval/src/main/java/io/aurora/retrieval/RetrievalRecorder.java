package io.aurora.retrieval;

import io.aurora.contract.UnifiedResult;
import io.aurora.rbac.Principal;

import java.util.List;

/**
 * Implemented by the host app to write a RETRIEVAL audit event without making
 * this module depend on the agent runtime (ArchUnit rule).
 */
public interface RetrievalRecorder {
    void record(Principal principal, String query, boolean imageQuery,
                List<UnifiedResult> results, int trimmed);
}
