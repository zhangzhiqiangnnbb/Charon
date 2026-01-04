package com.Charon.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final int LIMIT_PER_MINUTE = 120;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        long now = Instant.now().getEpochSecond();
        Window w = windows.computeIfAbsent(ip, k -> new Window(now / 60));
        synchronized (w) {
            long currentWin = now / 60;
            if (w.win != currentWin) {
                w.win = currentWin;
                w.count = 0;
            }
            w.count++;
            if (w.count > LIMIT_PER_MINUTE) {
                response.setStatus(429);
                response.getWriter().write("{\"code\":\"RATE_LIMIT\",\"message\":\"Too many requests\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private static class Window {
        long win;
        int count;
        Window(long w) { this.win = w; this.count = 0; }
    }
}

