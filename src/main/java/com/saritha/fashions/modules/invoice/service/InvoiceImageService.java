package com.saritha.fashions.modules.invoice.service;

import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.modules.invoice.exception.InvoiceRequestValidationException;
import com.saritha.fashions.util.GcsImageUploader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Item reference images for invoices, stored in Google Cloud Storage.
 *
 * Images are matched to items by file name: item_{index}_{anything}.{ext}, where index
 * is the 0-based position of the item in the request (same convention as the old flow).
 * Unlike the old flow, a file that does not follow the convention, points to a
 * non-existent item, is not an image, or is too large is rejected with a 400 instead of
 * being silently ignored.
 */
@Service
public class InvoiceImageService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceImageService.class);

    private static final Pattern ITEM_IMAGE_NAME = Pattern.compile("^item_(\\d+)_.+");
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024; // 10 MB

    private final GcsImageUploader gcsImageUploader;

    public InvoiceImageService(GcsImageUploader gcsImageUploader) {
        this.gcsImageUploader = gcsImageUploader;
    }

    /**
     * Validates the images and maps them to item indexes. Call before anything is saved.
     *
     * @throws InvoiceRequestValidationException listing every invalid file
     */
    public Map<Integer, MultipartFile> mapImagesToItems(List<MultipartFile> images, int itemCount) {
        Map<Integer, MultipartFile> imagesByItemIndex = new HashMap<>();
        if (images == null || images.isEmpty()) {
            return imagesByItemIndex;
        }

        Map<String, String> errors = new LinkedHashMap<>();
        for (int position = 0; position < images.size(); position++) {
            MultipartFile file = images.get(position);
            String key = "images[" + position + "]";
            if (file == null || file.isEmpty()) {
                continue; // empty part: nothing to upload
            }

            String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
            Matcher matcher = ITEM_IMAGE_NAME.matcher(fileName);
            if (!matcher.matches()) {
                errors.put(key, "file name '" + fileName + "' must look like item_{index}_{name}.{ext}");
                continue;
            }

            int itemIndex = Integer.parseInt(matcher.group(1));
            if (itemIndex >= itemCount) {
                errors.put(key, "file '" + fileName + "' refers to item " + itemIndex
                        + " but the invoice has only " + itemCount + " item(s)");
            } else if (imagesByItemIndex.containsKey(itemIndex)) {
                errors.put(key, "more than one image for item " + itemIndex);
            } else if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
                errors.put(key, "file '" + fileName + "' is not an image");
            } else if (file.getSize() > MAX_IMAGE_BYTES) {
                errors.put(key, "file '" + fileName + "' is larger than 10 MB");
            } else {
                imagesByItemIndex.put(itemIndex, file);
            }
        }

        if (!errors.isEmpty()) {
            throw new InvoiceRequestValidationException(errors);
        }
        return imagesByItemIndex;
    }

    /**
     * Uploads one item image into the invoice's folder.
     *
     * @param uploadedUrls every successful upload is added here so it can be rolled back
     * @return the public image URL
     */
    public String upload(String invoiceId, String itemUniqueId, MultipartFile file, List<String> uploadedUrls) {
        try {
            String url = gcsImageUploader.uploadFile(invoiceId, itemUniqueId, file);
            uploadedUrls.add(url);
            return url;
        } catch (IOException exception) {
            throw new APIException("Failed to upload image for item " + itemUniqueId);
        }
    }

    /**
     * Best-effort delete of images uploaded for an invoice that was not saved.
     *
     * uploadFile returns the blob's media link (.../b/{bucket}/o/{object}?...), so the
     * object path is extracted with extractGcsObjectPath. (The old rollback did a
     * string replace on a different URL format, which never matched, so it never
     * deleted anything.)
     */
    public void deleteUploadedImages(List<String> uploadedUrls) {
        for (String url : new ArrayList<>(uploadedUrls)) {
            try {
                String objectPath = gcsImageUploader.extractGcsObjectPath(url);
                if (objectPath == null || objectPath.isBlank()) {
                    objectPath = url.replace("https://storage.googleapis.com/" + gcsImageUploader.bucketName + "/", "");
                }
                gcsImageUploader.deleteFile(objectPath);
            } catch (Exception exception) {
                log.error("Could not delete orphan invoice image {}: {}", url, exception.getMessage());
            }
        }
    }
}
