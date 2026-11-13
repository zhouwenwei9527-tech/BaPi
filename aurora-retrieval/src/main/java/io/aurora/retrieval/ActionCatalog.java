package io.aurora.retrieval;

import io.aurora.contract.UnifiedResult;
import io.aurora.rbac.Principal;

import java.util.List;

/**
 * Actions are declared by the shell (desktop / menus / palette). Retrieval only
 * filters them by the current Principal, so all three entry points share the
 * same permission truth.
 */
public interface ActionCatalog {
    List<UnifiedResult> actions(Principal principal);
}
