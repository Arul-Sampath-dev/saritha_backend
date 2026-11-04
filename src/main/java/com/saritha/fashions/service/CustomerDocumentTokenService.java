package com.saritha.fashions.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** Encrypts document type and ID into opaque, tamper-evident customer links. */
@Service
public class CustomerDocumentTokenService {
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public CustomerDocumentTokenService(@Value("${customer.document-link.secret}") String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("customer.document-link.secret must be exactly 32 UTF-8 bytes");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public String createToken(String documentType, String documentId) {
        if (documentType == null || documentType.isBlank() || documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("Document type and ID are required");
        }
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal((documentType + "\n" + documentId).getBytes(StandardCharsets.UTF_8));
            byte[] token = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, token, 0, nonce.length);
            System.arraycopy(ciphertext, 0, token, nonce.length, ciphertext.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        } catch (Exception e) {
            throw new IllegalStateException("Could not create customer document token", e);
        }
    }

    public String decryptToken(String expectedType, String token) {
        try {
            byte[] tokenBytes = Base64.getUrlDecoder().decode(token);
            if (tokenBytes.length <= NONCE_BYTES) {
                throw new IllegalArgumentException("Invalid customer document token");
            }
            byte[] nonce = Arrays.copyOfRange(tokenBytes, 0, NONCE_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(tokenBytes, NONCE_BYTES, tokenBytes.length);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            String payload = new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
            int separator = payload.indexOf('\n');
            if (separator < 0 || !expectedType.equals(payload.substring(0, separator))) {
                throw new IllegalArgumentException("Invalid customer document token");
            }
            String documentId = payload.substring(separator + 1);
            if (documentId.isBlank()) {
                throw new IllegalArgumentException("Invalid customer document token");
            }
            return documentId;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid customer document token");
        }
    }
}
