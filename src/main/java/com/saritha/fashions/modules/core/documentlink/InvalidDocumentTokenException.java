package com.saritha.fashions.modules.core.documentlink;

/**
 * A download token that is malformed, was altered, was made with another secret, or is for a
 * different kind of document. Deliberately carries no detail about which check failed.
 */
public class InvalidDocumentTokenException extends RuntimeException {

    public InvalidDocumentTokenException() {
        super("Invalid customer document token");
    }
}
