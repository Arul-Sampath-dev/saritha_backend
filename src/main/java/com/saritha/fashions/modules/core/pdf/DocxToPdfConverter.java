package com.saritha.fashions.modules.core.pdf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Converts a .docx to PDF with the external Cloud Run service (same service and request as the
 * legacy ReceiptServiceImpl: multipart POST, part "file").
 *
 * Differences from the legacy call:
 * - the URL is configurable (pdf.converter.url; default = the URL the legacy code hard-codes);
 * - connect / read timeouts, so a hanging converter cannot block request threads forever;
 * - a failed or empty conversion is an error instead of a 200 with an empty body;
 * - one RestTemplate is reused instead of a new one per PDF.
 */
@Component
public class DocxToPdfConverter {

    private static final Logger log = LoggerFactory.getLogger(DocxToPdfConverter.class);

    /** Minimum time between two warm-up calls, so page loads cannot hammer the converter. */
    private static final long WARMUP_INTERVAL_MS = 60_000;

    private final RestTemplate restTemplate;
    private final String converterUrl;
    private final String warmupUrl;
    private final java.util.concurrent.atomic.AtomicLong lastWarmupAt = new java.util.concurrent.atomic.AtomicLong();

    public DocxToPdfConverter(
            @Value("${pdf.converter.url:https://docs-to-pdf-new-156538435086.asia-south1.run.app/convert}") String converterUrl,
            @Value("${pdf.converter.warmup-url:https://docs-to-pdf-new-156538435086.asia-south1.run.app/warmup}") String warmupUrl,
            @Value("${pdf.converter.connect-timeout-ms:10000}") int connectTimeoutMs,
            @Value("${pdf.converter.read-timeout-ms:90000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(requestFactory);
        this.converterUrl = converterUrl;
        this.warmupUrl = warmupUrl;
    }

    /**
     * Wakes the converter (Cloud Run scales to zero, so the first PDF after a quiet period is slow).
     * Runs in the background and returns at once; at most one call per minute is actually sent.
     * Ported from the legacy PdfWarmupController.
     *
     * @return true if a warm-up call was started, false if one was sent less than a minute ago
     */
    public boolean warmUp() {
        long now = System.currentTimeMillis();
        long last = lastWarmupAt.get();
        if (now - last < WARMUP_INTERVAL_MS || !lastWarmupAt.compareAndSet(last, now)) {
            return false;
        }
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                restTemplate.getForObject(warmupUrl, String.class);
                log.info("PDF converter warmed up");
            } catch (RestClientException exception) {
                log.warn("PDF converter warm-up failed: {}", exception.getMessage());
            }
        });
        return true;
    }

    /**
     * @param fileName name sent with the upload, e.g. "Invoice.docx" (as the legacy code sent)
     * @return the PDF bytes
     */
    public byte[] convert(byte[] docx, String fileName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(docx) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });

        ResponseEntity<byte[]> response;
        try {
            response = restTemplate.postForEntity(converterUrl, new HttpEntity<>(body, headers), byte[].class);
        } catch (RestClientException exception) {
            log.error("PDF converter call failed for {}: {}", fileName, exception.getMessage());
            throw new PdfGenerationException("PDF converter call failed", exception);
        }
        byte[] pdf = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || pdf == null || pdf.length == 0) {
            log.error("PDF converter returned {} with {} bytes for {}", response.getStatusCode(),
                    pdf == null ? 0 : pdf.length, fileName);
            throw new PdfGenerationException("PDF converter returned no PDF");
        }
        return pdf;
    }
}
