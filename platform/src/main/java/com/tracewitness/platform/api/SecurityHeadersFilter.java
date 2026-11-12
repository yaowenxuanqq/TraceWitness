package com.tracewitness.platform.api;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Baseline browser hardening for the local demo. All resources are served from
 * the same origin, so the policy stays intentionally narrow.
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {
    private static final String CSP = "default-src 'self'; img-src 'self' data:; media-src 'self'; "
            + "script-src 'self'; style-src 'self'; connect-src 'self'; object-src 'none'; "
            + "base-uri 'none'; frame-ancestors 'none'";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Content-Security-Policy", CSP);
        chain.doFilter(request, response);
    }
}
