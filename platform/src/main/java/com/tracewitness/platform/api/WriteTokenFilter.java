package com.tracewitness.platform.api;

import com.tracewitness.platform.config.LocalToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class WriteTokenFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Trace-Token";
    public static final String COOKIE = "tw_token";
    private static final Set<String> LOCAL_HOSTS = new HashSet<String>(Arrays.asList(
            "localhost", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1"));

    private final LocalToken token;

    public WriteTokenFilter(LocalToken token) {
        this.token = token;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!localHost(request.getServerName())) {
            reject(response, 403, "invalid_host");
            return;
        }
        if (isRead(request)) {
            if (request.getRequestURI().startsWith("/api") || "/".equals(request.getRequestURI())) {
                response.setHeader("Set-Cookie",
                        COOKIE + "=" + token.value() + "; Path=/; HttpOnly; SameSite=Strict");
                response.setHeader("Cache-Control", "no-store");
            }
            chain.doFilter(request, response);
            return;
        }
        if (request.getRequestURI().startsWith("/api") && !accepted(request)) {
            reject(response, 401, "unauthorized");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean accepted(HttpServletRequest request) {
        if (token.matches(request.getHeader(HEADER))) {
            return true;
        }
        String raw = request.getHeader("Cookie");
        if (raw == null || !sameOriginCookieUse(request)) {
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

    private static boolean sameOriginCookieUse(HttpServletRequest request) {
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        if (fetchSite != null && !"same-origin".equals(fetchSite) && !"none".equals(fetchSite)) {
            return false;
        }
        String origin = request.getHeader("Origin");
        if (origin == null || origin.trim().isEmpty()) {
            return true;
        }
        try {
            URI uri = URI.create(origin);
            String host = uri.getHost();
            int port = uri.getPort() < 0 ? defaultPort(uri.getScheme()) : uri.getPort();
            return request.getScheme().equalsIgnoreCase(uri.getScheme())
                    && request.getServerName().equalsIgnoreCase(host)
                    && request.getServerPort() == port;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }

    private static boolean localHost(String raw) {
        if (raw == null) {
            return false;
        }
        String host = raw.trim().toLowerCase(Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        return LOCAL_HOSTS.contains(host);
    }

    private static void reject(HttpServletResponse response, int status, String error) throws IOException {
        byte[] body = ("{\"error\":\"" + error + "\"}").getBytes(StandardCharsets.UTF_8);
        response.setStatus(status);
        response.setContentType("application/json");
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }

    private static boolean isRead(HttpServletRequest request) {
        String method = request.getMethod();
        return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
    }
}
