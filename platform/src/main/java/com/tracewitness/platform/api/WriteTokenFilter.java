package com.tracewitness.platform.api;

import com.tracewitness.platform.config.LocalToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class WriteTokenFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Trace-Token";
    public static final String COOKIE = "tw_token";

    private final LocalToken token;

    public WriteTokenFilter(LocalToken token) {
        this.token = token;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isRead(request)) {
            if (request.getRequestURI().startsWith("/api") || "/".equals(request.getRequestURI())) {
                Cookie cookie = new Cookie(COOKIE, token.value());
                cookie.setPath("/");
                cookie.setHttpOnly(true);
                response.addCookie(cookie);
                response.setHeader("Set-Cookie", COOKIE + "=" + token.value() + "; Path=/; HttpOnly; SameSite=Strict");
            }
            chain.doFilter(request, response);
            return;
        }
        if (request.getRequestURI().startsWith("/api") && !accepted(request)) {
            byte[] body = "{\"error\":\"unauthorized\"}".getBytes(StandardCharsets.UTF_8);
            response.setStatus(401);
            response.setContentType("application/json");
            response.setContentLength(body.length);
            response.getOutputStream().write(body);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean accepted(HttpServletRequest request) {
        if (token.matches(request.getHeader(HEADER))) {
            return true;
        }
        String raw = request.getHeader("Cookie");
        if (raw == null) {
            return false;
        }
        String[] parts = raw.split(";");
        for (String part : parts) {
            String item = part.trim();
            if (item.startsWith(COOKIE + "=") && token.matches(item.substring(COOKIE.length() + 1))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRead(HttpServletRequest request) {
        String method = request.getMethod();
        return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
    }
}
