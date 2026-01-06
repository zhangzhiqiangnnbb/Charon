package com.Charon.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    private RateLimitFilter rateLimitFilter;

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        rateLimitFilter = new RateLimitFilter();
    }

    @Test
    void shouldAllowRequestsUnderLimit() throws Exception {
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        for (int i = 0; i < 10; i++) {
            rateLimitFilter.doFilterInternal(request, response, filterChain);
        }

        verify(filterChain, times(10)).doFilter(request, response);
    }

    @Test
    void shouldBlockRequestsOverLimit() throws Exception {
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        // Limit is 120. Send 121 requests.
        for (int i = 0; i < 120; i++) {
            rateLimitFilter.doFilterInternal(request, response, filterChain);
        }
        
        // The 121st request should be blocked
        rateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(120)).doFilter(request, response);
        verify(response).setStatus(429);
        assertTrue(stringWriter.toString().contains("RATE_LIMIT"));
    }
}
