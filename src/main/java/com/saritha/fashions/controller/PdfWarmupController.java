package com.saritha.fashions.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/v1/api/pdf-service")
public class PdfWarmupController {

    private static final Logger logger = LoggerFactory.getLogger(PdfWarmupController.class);

    @Autowired
    private RestTemplate restTemplate;

    private static final String CLOUD_RUN_WARMUP_URL =
            "https://docs-to-pdf-new-156538435086.asia-south1.run.app/warmup";

    @GetMapping("/warmup")
    public String warmupPdfService() {
        // Fire asynchronously, don’t block frontend
        CompletableFuture.runAsync(() -> {
            try {
                String response = restTemplate.getForObject(CLOUD_RUN_WARMUP_URL, String.class);
                logger.info("✅ Cloud Run PDF service warmed up: {}", response);
            } catch (Exception e) {
                logger.warn("⚠️ Warmup request failed: {}", e.getMessage());
            }
        });

        // Immediately return so frontend is not blocked
        return "Warmup triggered!";
    }
}
