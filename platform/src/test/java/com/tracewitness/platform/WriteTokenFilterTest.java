package com.tracewitness.platform;

import com.tracewitness.platform.api.WriteTokenFilter;
import com.tracewitness.platform.config.LocalToken;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WriteTokenFilterTest {
    @Test
    void rejectsForeignHostBeforeIssuingToken() throws Exception {
        WriteTokenFilter filter = filter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setServerName("attacker.example");
        request.setServerPort(8080);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
        assertNull(response.getHeader("Set-Cookie"));
    }

    @Test
    void cookieWriteRejectsCrossOriginRequest() throws Exception {
        WriteTokenFilter filter = filter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/plugins/rollback");
        request.setServerName("127.0.0.1");
        request.setServerPort(8080);
        request.addHeader("Cookie", "tw_token=test-token");
        request.addHeader("Origin", "http://127.0.0.1:9000");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    @Test
    void cookieWriteAcceptsExactSameOrigin() throws Exception {
        WriteTokenFilter filter = filter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/plugins/rollback");
        request.setServerName("127.0.0.1");
        request.setServerPort(8080);
        request.addHeader("Cookie", "tw_token=test-token");
        request.addHeader("Origin", "http://127.0.0.1:8080");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }

    private static WriteTokenFilter filter() {
        LocalToken token = mock(LocalToken.class);
        when(token.matches("test-token")).thenReturn(true);
        return new WriteTokenFilter(token);
    }
}
