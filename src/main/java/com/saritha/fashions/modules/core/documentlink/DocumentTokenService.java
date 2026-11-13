package com.saritha.fashions.modules.core.documentlink;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Turns a document id into an opaque, tamper-evident token for public download links, and back.
 * Replaces the legacy CustomerDocumentTokenService for the modules.
 *
 * Format (unchanged, so links already sent to customers and the legacy download endpoints keep
 * working): base64url-without-padding( nonce[12] || AES-256-GCM( "{label}\n{documentId}" ) ),
 * 128-bit tag, key = the 32 UTF-8 bytes of customer.document-link.secret.
 *
 * GCM authenticates the payload, so an altered token, a token made with another key, or a token
 * for another kind of document is rejected.
 */
@Service
public class DocumentTokenService {

    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    /** Upper bound for a token from a URL; real tokens are well under 200 characters. */
    private static final int MAX_TOKEN_LENGTH = 1024;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public DocumentTokenService(@Value("${customer.document-link.secret}") String secret) {
        byte[] keyBytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("customer.document-link.secret must be exactly 32 UTF-8 bytes");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    /** A new token for the document (a fresh random nonce each time, so tokens differ per call). */
    public String createToken(DocumentKind kind, String documentId) {
        if (kind == null || documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("Document kind and id are required");
        }
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal((kind.label() + "\n" + documentId).getBytes(StandardCharsets.UTF_8));
            byte[] token = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, token, 0, nonce.length);
            System.arraycopy(ciphertext, 0, token, nonce.length, ciphertext.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create customer document token", exception);
        }
    }

    /**
     * The document id inside the token.
     *
     * @throws InvalidDocumentTokenException if the token is malformed, altered, made with another
     *                                       key, or for another kind of document
     */
    public String readToken(DocumentKind expectedKind, String token) {
        if (expectedKind == null || token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            throw new InvalidDocumentTokenException();
        }
        try {
            byte[] tokenBytes = Base64.getUrlDecoder().decode(token);
            if (tokenBytes.length <= NONCE_BYTES) {
                throw new InvalidDocumentTokenException();
            }
            byte[] nonce = Arrays.copyOfRange(tokenBytes, 0, NONCE_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(tokenBytes, NONCE_BYTES, tokenBytes.length);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            String payload = new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
            int separator = payload.indexOf('\n');
            if (separator < 0 || !expectedKind.label().equals(payload.substring(0, separator))) {
                throw new InvalidDocumentTokenException();
            }
            String documentId = payload.substring(separator + 1);
            if (documentId.isBlank()) {
                throw new InvalidDocumentTokenException();
            }
            return documentId;
        } catch (InvalidDocumentTokenException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidDocumentTokenException();
        }
    }
}
