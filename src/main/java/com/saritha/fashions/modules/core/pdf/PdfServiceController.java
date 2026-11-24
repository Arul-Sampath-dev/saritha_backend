package com.saritha.fashions.modules.core.pdf;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PDF warm-up. Same path and response as the legacy PdfWarmupController, so the frontend does not change:
 * GET /v1/api/pdf-service/warmup → "Warmup triggered!".
 *
 * Invoices, receipts and payment receipts are now rendered inside the backend (HtmlPdfRenderer), so there is
 * nothing to wake up: the endpoint only answers. It can be removed once the frontend stops calling it.
 *
 * Any signed-in user may call it (the "anyRequest().authenticated()" rule in WebSecurityConfig).
 */
@RestController
@RequestMapping("/v1/api/pdf-service")
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "PDF service", description = "Kept for the frontend (PDFs are generated inside the backend)")
public class PdfServiceController {


    @Operation(
            summary = "Warm up the PDF service (no-op)",
            description = "Kept for the frontend. PDFs are now generated inside the backend, so there is nothing to "
                    + "wake up; it returns immediately.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "\"Warmup triggered!\" (also when throttled)"),
                    @ApiResponse(responseCode = "401", description = "No or invalid JWT")
            })
    @GetMapping("/warmup")
    public String warmUp() {
        return "Warmup triggered!";
    }
}
