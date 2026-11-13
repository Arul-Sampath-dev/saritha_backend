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

    private final RestTemplate restTemplate;
    private final String converterUrl;

    public DocxToPdfConverter(
            @Value("${pdf.converter.url:https://docs-to-pdf-new-156538435086.asia-south1.run.app/convert}") String converterUrl,
            @Value("${pdf.converter.connect-timeout-ms:10000}") int connectTimeoutMs,
            @Value("${pdf.converter.read-timeout-ms:90000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(requestFactory);
        this.converterUrl = converterUrl;
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
