package com.saritha.fashions.modules.core.pdf;

/**
 * The PDF could not be produced (template problem, converter unreachable / failing / slow, or an
 * empty result). The cause is logged; callers return a generic "please try again" message.
 */
public class PdfGenerationException extends RuntimeException {

    public PdfGenerationException(String message) {
        super(message);
    }

    public PdfGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
