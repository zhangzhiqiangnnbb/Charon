package com.Charon.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final int LIMIT_PER_MINUTE = 120;
    
    // Fix: Use Caffeine Cache to automatically evict old entries (Memory Leak Fix)
    private final Cache<String, Window> windows = Caffeine.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES) // Clean up inactive IPs
            .maximumSize(10000) // Prevent DDoS from filling memory
            .build();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        long now = Instant.now().getEpochSecond();
        long currentWin = now / 60;

        Window w = windows.get(ip, k -> new Window(currentWin));
        
        // Double check for null in case cache fails (unlikely)
        if (w == null) {
             filterChain.doFilter(request, response);
             return;
        }

        synchronized (w) {
            if (w.win != currentWin) {
                w.win = currentWin;
                w.count.set(0);
            }
            if (w.count.incrementAndGet() > LIMIT_PER_MINUTE) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"RATE_LIMIT\",\"message\":\"Too many requests\",\"data\":null,\"traceId\":null}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private static class Window {
        long win;
        final AtomicInteger count = new AtomicInteger(0);
        Window(long w) { this.win = w; }
    }
}
