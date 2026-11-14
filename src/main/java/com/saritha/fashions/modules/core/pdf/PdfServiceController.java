package com.saritha.fashions.modules.core.pdf;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PDF converter warm-up. Same path and response as the legacy PdfWarmupController, so the
 * frontend does not change: GET /v1/api/pdf-service/warmup → "Warmup triggered!".
 *
 * Any signed-in user may call it (the "anyRequest().authenticated()" rule in WebSecurityConfig).
 */
@RestController
@RequestMapping("/v1/api/pdf-service")
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "PDF service", description = "Wake the PDF converter before the user needs a PDF")
public class PdfServiceController {

    private final DocxToPdfConverter converter;

    public PdfServiceController(DocxToPdfConverter converter) {
        this.converter = converter;
    }

    @Operation(
            summary = "Warm up the PDF converter",
            description = "Wakes the docx-to-PDF converter in the background so the next invoice / receipt PDF is "
                    + "fast. Returns immediately. Calls within a minute of the previous one are ignored. Call it e.g. "
                    + "when the app starts or the invoice screen opens.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "\"Warmup triggered!\" (also when throttled)"),
                    @ApiResponse(responseCode = "401", description = "No or invalid JWT")
            })
    @GetMapping("/warmup")
    public String warmUp() {
        converter.warmUp();
        return "Warmup triggered!";
    }
}
