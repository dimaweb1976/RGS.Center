package com.rgs.center.onlyone;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Guards application operations that can change device or process state.
 *
 * <p>Read-only HTTP methods remain available without a lease. For every other
 * method the client must send {@code X-Lock-Token}, and that token must match
 * the singleton row while its expiration time is still active.</p>
 *
 * <p>This interceptor is a second enforcement layer: the frontend may hide or
 * disable controls, but backend authorization never relies on frontend state.</p>
 */
@Component
class OnlyOneLockInterceptor implements HandlerInterceptor {

    private final OnlyOneLockService lockService;

    OnlyOneLockInterceptor(OnlyOneLockService lockService) {
        this.lockService = lockService;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws IOException {
        
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/barcode/") || uri.startsWith("/api/scales/")) {
            return true;
        }

        if (isReadOnly(request.getMethod())) {
            // Monitoring and page-loading GET requests must remain available to
            // clients that do not own the exclusive lease.
            return true;
        }

        String token = request.getHeader(OnlyOneController.LOCK_TOKEN_HEADER);
        if (token != null && !token.isBlank() && lockService.checkMe(token).isPresent()) {
            return true;
        }

        // 423 Locked distinguishes an exclusive-access conflict from ordinary
        // authentication failures handled elsewhere by Spring Security.
        response.setStatus(HttpStatus.LOCKED.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"Exclusive access lock is required\"}");
        return false;
    }

    private static boolean isReadOnly(String method) {
        return "GET".equals(method)
                || "HEAD".equals(method)
                || "OPTIONS".equals(method);
    }
}
