package com.saritha.fashions.interceptor;

import com.saritha.fashions.service.InvoiceServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ExecutionTimeInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionTimeInterceptor.class);

    private static final String START_TIME = "startTime";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        String path = request.getRequestURI();
        logger.warn("Time calculation started for request to {}: {} ms", path, System.currentTimeMillis());

        request.setAttribute(START_TIME, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        long startTime = (Long) request.getAttribute(START_TIME);
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        String path = request.getRequestURI();
        logger.warn("Total time taken for request to {}: {} ms", path, duration);
    }
}

