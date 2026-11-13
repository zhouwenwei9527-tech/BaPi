package io.aurora.app.web;

import io.aurora.rbac.Principal;
import jakarta.servlet.http.HttpServletRequest;

public final class RequestContext {

    private RequestContext() {
    }

    public static Principal principal(HttpServletRequest request) {
        return (Principal) request.getAttribute("principal");
    }

    public static String token(HttpServletRequest request) {
        return (String) request.getAttribute("token");
    }
}
