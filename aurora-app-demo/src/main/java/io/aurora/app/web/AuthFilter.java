package io.aurora.app.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aurora.contract.ApiResponse;
import io.aurora.contract.AuroraErrorCode;
import io.aurora.contract.AuroraException;
import io.aurora.rbac.AuthService;
import io.aurora.rbac.Principal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Opaque bearer tokens, revoked server-side. No third-party auth library is
 * needed; the token is never stored in plaintext (only SHA-256).
 */
@Component
@Order(1)
public class AuthFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_API = Set.of("/api/auth/login", "/api/health");

    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public AuthFilter(AuthService authService, ObjectMapper objectMapper) {
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || PUBLIC_API.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : null;
        try {
            Principal principal = authService.resolve(token);
            request.setAttribute("principal", principal);
            request.setAttribute("token", token);
            filterChain.doFilter(request, response);
        } catch (AuroraException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            objectMapper.writeValue(response.getWriter(),
                    ApiResponse.fail(AuroraErrorCode.UNAUTHORIZED, e.getMessage()));
        }
    }
}
