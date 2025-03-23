package com.saritha.fashions.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter implements Filter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String ipAddress = httpRequest.getRemoteAddr();

        Bucket bucket = buckets.computeIfAbsent(ipAddress, k -> createNewBucket());

        // Check if a token is available; otherwise, reject the request
        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            httpResponse.setStatus(429);
            httpResponse.getWriter().write("Too many requests - please try again later.");
        }
    }

    private Bucket createNewBucket() {
        // Configure the bucket to allow 20 requests per minute
        Refill refill = Refill.greedy(20, Duration.ofMinutes(1));
        Bandwidth limit = Bandwidth.classic(20, refill);
        return Bucket.builder().addLimit(limit).build();
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void destroy() {
    }
}
