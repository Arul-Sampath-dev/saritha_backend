package com.saritha.fashions.util;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class GcsImageUploader {
    private static final Logger logger = LoggerFactory.getLogger(GcsImageUploader.class);

    @Value("${gcp.bucket.name}")
    public String bucketName;
    private final Storage storage;

    public GcsImageUploader() throws IOException {
        this.storage = initializeStorage();
    }

    private Storage initializeStorage() throws IOException {
        try {
            // 1. First try Application Default Credentials (for Cloud Run)
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
            logger.info("Using Application Default Credentials");
            return StorageOptions.newBuilder()
                    .setCredentials(credentials)
                    .build()
                    .getService();
        } catch (IOException e) {
            logger.warn("ADC failed, trying service account key");
            try {
                // 2. Fallback to service account key (for local development)
                InputStream keyStream = new ClassPathResource("gcp-key.json").getInputStream();
                GoogleCredentials credentials = GoogleCredentials.fromStream(keyStream);
                return StorageOptions.newBuilder()
                        .setCredentials(credentials)
                        .build()
                        .getService();
            } catch (IOException ex) {
                logger.error("Both ADC and service account key failed");
                throw new IOException("Failed to initialize GCS client", ex);
            }
        }
    }

    public String uploadFile(String invoiceId, String itemId, MultipartFile file) throws IOException {
        try {
            // Validate input
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("File is empty or null");
            }

            // Create safe filename
            String safeItemId = itemId.replaceAll("[^a-zA-Z0-9.-]", "_");
            String safeFilename = file.getOriginalFilename().replaceAll("[^a-zA-Z0-9.-]", "_");
            String fileName = String.format("invoices/%s/%s_%s",
                    invoiceId, safeItemId, safeFilename);

            // Configure blob with metadata
            BlobId blobId = BlobId.of(bucketName, fileName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(file.getContentType())
                    .setMetadata(Map.of(
                            "originalFilename", file.getOriginalFilename(),
                            "uploadedBy", "SarithaFashions"
                    ))
                    .build();

            // Upload with timeout and public access
            Blob blob = storage.create(blobInfo, file.getBytes());

            logger.info("Successfully uploaded {} to GCS", fileName);
            return blob.getMediaLink();

        } catch (StorageException e) {
            throw new IOException(String.format(
                    "Failed to upload %s: %s (Code: %d)",
                    file.getOriginalFilename(),
                    e.getMessage(),
                    e.getCode()), e);
        }
    }

    public void deleteFile(String fileName) {
        try {
            storage.delete(bucketName, fileName);
            logger.info("Deleted file {} from GCS", fileName);
        } catch (StorageException e) {
            logger.error("Failed to delete file {}: {}", fileName, e.getMessage());
            throw new RuntimeException("GCS deletion failed", e);
        }
    }
}