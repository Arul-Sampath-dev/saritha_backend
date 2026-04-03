package com.saritha.fashions.controller.OwnerControllerBox;

import com.saritha.fashions.payload.owner.ShopOwnerUpdateRequest;
import com.saritha.fashions.service.ShopOwnerServices.ShopOwnerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/v1/api/owner/shop")
@RequiredArgsConstructor
public class ShopOwnerUpdateController {

    private final ShopOwnerService shopOwnerService;

    @PutMapping("/update-owner-details")
    @PreAuthorize("hasAuthority('OWNER') or hasAuthority('ADMIN')")
    public ResponseEntity<?> updateShopOwnerDetails(
            @Valid @RequestBody ShopOwnerUpdateRequest request,
            Authentication authentication) {

        try {
            String performedBy = authentication.getName();
            String result = shopOwnerService.updateShopOwner(request, performedBy);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", result,
                    "shopId", request.getShopId()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to update shop owner details: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/backup-history/user/{userId}")
    @PreAuthorize("hasAuthority('OWNER') or hasAuthority('ADMIN')")
    public ResponseEntity<?> getUserBackupHistory(@PathVariable String userId) {
        try {
            return ResponseEntity.ok(shopOwnerService.getUserBackupHistory(userId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to fetch backup history: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/backup-history/shop/{shopId}")
    @PreAuthorize("hasAuthority('OWNER') or hasAuthority('ADMIN')")
    public ResponseEntity<?> getShopBackupHistory(@PathVariable String shopId) {
        try {
            return ResponseEntity.ok(shopOwnerService.getShopBackupHistory(shopId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to fetch backup history: " + e.getMessage()
            ));
        }
    }
}