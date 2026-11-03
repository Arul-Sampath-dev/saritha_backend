package com.saritha.fashions.service;

import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.*;
import com.saritha.fashions.payload.owner.ShopOwnerUpdateRequest;
import com.saritha.fashions.repository.ShopRepository;
import com.saritha.fashions.repository.UserBackupHistoryRepository;
import com.saritha.fashions.repository.UserRepository;
import com.saritha.fashions.service.ShopOwnerServices.ShopOwnerService;
import com.saritha.fashions.util.AuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShopOwnerServiceImpl implements ShopOwnerService {

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final UserBackupHistoryRepository userBackupHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthUtil authUtil;

    /**
     * Update existing shop owner details with backup
     * Flow:
     * 1. Take backup of current owner details before changes
     * 2. Update owner details in user collection while keeping _id, username, assignedShops, roles, address unchanged
     * 3. Update shop contact number if phone is changed
     */
    @Override
    @Transactional
    public String updateShopOwner(ShopOwnerUpdateRequest request, String performedByUsername) {
        log.info("Starting shop owner details update process for shop: {}", request.getShopId());

        // Step 1: Validate inputs
        Shop shop = shopRepository.findById(request.getShopId())
                .orElseThrow(() -> new RuntimeException("Shop not found with id: " + request.getShopId()));
        requireAuthorizedShop(request.getShopId());

        User currentOwner = userRepository.findByUserId(request.getOldOwnerUserId()).orElseThrow(() -> new APIException("Old User Not Found in shop"));

        log.info("Updating details for existing owner: {} of shop: {}",
                currentOwner.getUserId(), shop.getShopName());

        // Step 2: Create backup of current owner before making changes
        createOwnerBackup(currentOwner, shop.getId(), request.getBackupReason(), performedByUsername);

        // Step 3: Update owner details while preserving unchanged fields
        updateOwnerDetails(currentOwner, request);

        // Step 4: Update shop information if needed
        updateShopInformation(shop, currentOwner, request);

        log.info("Successfully updated owner details for shop: {}. Owner: {}",
                shop.getShopName(), currentOwner.getUserId());

        return "Shop owner details updated successfully. Backup created for previous state.";
    }

    /**
     * Step 2: Create backup of current owner with assigned shop information
     */
    private void createOwnerBackup(User currentOwner, String shopId, String reason, String performedBy) {
        try {
            // Create a deep copy of the user object for backup
            User userBackup = createUserBackup(currentOwner);

            // Extract assigned shop information
            List<String> assignedShopIds = extractAssignedShopIds(currentOwner);
            List<UserBackupHistory.ShopInfo> assignedShopsInfo = extractAssignedShopsInfo(currentOwner);

            UserBackupHistory backup = new UserBackupHistory(
                    currentOwner.getUserId(),
                    userBackup,
                    reason,
                    performedBy,
                    shopId,
                    assignedShopIds,
                    assignedShopsInfo
            );

            userBackupHistoryRepository.save(backup);
            log.info("Created backup for owner: {} for shop: {}. Assigned shops: {}",
                    currentOwner.getUserId(), shopId, assignedShopIds.size());
        } catch (Exception e) {
            log.error("Failed to create backup for user: {}", currentOwner.getUserId(), e);
            throw new APIException("Backup creation failed: " + e.getMessage());
        }
    }

    /**
     * Create a deep copy of user for backup purposes
     */
    private User createUserBackup(User original) {
        User backup = new User();

        // Copy all fields to preserve the state before update
        backup.setUserId(original.getUserId());
        backup.setUsername(original.getUsername());
        backup.setName(original.getName());
        backup.setEmail(original.getEmail());
        backup.setPassword("[BACKUP_REDACTED]");
        backup.setRole(original.getRole());
        backup.setPhone(original.getPhone());
        backup.setSalary(original.getSalary());
        backup.setAddress(original.getAddress());
        backup.setAccountCreationDate(original.getAccountCreationDate());
        backup.setLastLoginDate(original.getLastLoginDate());
        backup.setLastOnlineDate(original.getLastOnlineDate());
        backup.setRoles(new HashSet<>(original.getRoles()));
        backup.setCreatedAt(original.getCreatedAt());
        backup.setUpdatedAt(original.getUpdatedAt());

        // Note: We intentionally don't backup DBRef fields like assignedShops,
        // authToken, allExpenses as they reference live data

        return backup;
    }

    /**
     * Extract assigned shop information for backup
     */
    private List<UserBackupHistory.ShopInfo> extractAssignedShopsInfo(User user) {
        List<UserBackupHistory.ShopInfo> shopsInfo = new ArrayList<>();

        if (user.getAssignedShops() != null) {
            for (Shop shop : user.getAssignedShops()) {
                if (shop != null) {
                    shopsInfo.add(new UserBackupHistory.ShopInfo(shop));
                }
            }
        }

        return shopsInfo;
    }

    /**
     * Extract assigned shop IDs for backup
     */
    private List<String> extractAssignedShopIds(User user) {
        List<String> shopIds = new ArrayList<>();

        if (user.getAssignedShops() != null) {
            for (Shop shop : user.getAssignedShops()) {
                if (shop != null && shop.getId() != null) {
                    shopIds.add(shop.getId());
                }
            }
        }

        return shopIds;
    }

    /**
     * Step 3: Update owner details while preserving unchanged fields
     * Unchanged fields: _id, username, assignedShops, roles, address
     */
    private void updateOwnerDetails(User owner, ShopOwnerUpdateRequest request) {
        boolean hasChanges = false;

        // Update name if provided
        if (request.getNewOwnerName() != null && !request.getNewOwnerName().trim().isEmpty()) {
            if (!request.getNewOwnerName().equals(owner.getName())) {
                owner.setName(request.getNewOwnerName().trim());
                hasChanges = true;
                log.info("Updated name for owner: {}", owner.getUserId());
            }
        }

        // Update phone if provided
        if (request.getNewOwnerPhone() != null && !request.getNewOwnerPhone().trim().isEmpty()) {
            if (!request.getNewOwnerPhone().equals(owner.getPhone())) {
                owner.setPhone(request.getNewOwnerPhone().trim());
                hasChanges = true;
                log.info("Updated phone for owner: {}", owner.getUserId());
            }
        }

        // Update email if provided
        if (request.getNewOwnerEmail() != null && !request.getNewOwnerEmail().trim().isEmpty()) {
            String newEmail = request.getNewOwnerEmail().toLowerCase().trim();
            if (!newEmail.equals(owner.getEmail())) {
                // Check if email is already used by another user
                User existingUser = userRepository.findByEmail(newEmail);
                if (existingUser != null && !existingUser.getUserId().equals(owner.getUserId())) {
                    throw new RuntimeException("Email already in use by another user");
                }
                owner.setEmail(newEmail);
                hasChanges = true;
                log.info("Updated email for owner: {}", owner.getUserId());
            }
        }

        // Update password if provided
        if (request.getNewOwnerPassword() != null && !request.getNewOwnerPassword().trim().isEmpty()) {
            String encodedPassword = passwordEncoder.encode(request.getNewOwnerPassword().trim());
            owner.setPassword(encodedPassword);
            hasChanges = true;
            log.info("Updated password for owner: {}", owner.getUserId());
        }

        // Update salary if provided
        if (request.getNewOwnerSalary() != null) {
            if (!request.getNewOwnerSalary().equals(owner.getSalary())) {
                owner.setSalary(request.getNewOwnerSalary());
                hasChanges = true;
                log.info("Updated salary for owner: {} to {}", owner.getUserId(), request.getNewOwnerSalary());
            }
        }

        // Update timestamp and save only if there are changes
        if (hasChanges) {
            owner.setUpdatedAt(new Date());
            userRepository.save(owner);
            log.info("Saved updated owner details for user: {}", owner.getUserId());
        } else {
            log.info("No changes detected for owner: {}", owner.getUserId());
        }
    }

    /**
     * Step 4: Update shop information if needed
     */
    private void updateShopInformation(Shop shop, User owner, ShopOwnerUpdateRequest request) {
        boolean hasShopChanges = false;

        // Update shop owner name if owner name was changed
        if (request.getNewOwnerName() != null && !request.getNewOwnerName().trim().isEmpty()) {
            if (!request.getNewOwnerName().equals(shop.getOwnerName())) {
                shop.setOwnerName(request.getNewOwnerName().trim());
                hasShopChanges = true;
                log.info("Updated owner name in shop: {}", shop.getShopName());
            }
        }

        // Update shop contact number if owner phone was changed
        if (request.getNewOwnerPhone() != null && !request.getNewOwnerPhone().trim().isEmpty()) {
            if (!request.getNewOwnerPhone().equals(shop.getShopContactNumber())) {
                shop.setShopContactNumber(request.getNewOwnerPhone().trim());
                hasShopChanges = true;
                log.info("Updated shop contact number for: {}", shop.getShopName());
            }
        }

        // Ensure owner is in employees list (maintain consistency)
        if (shop.getEmployees() == null) {
            shop.setEmployees(new ArrayList<>());
        }
        if (shop.getEmployeeIds() == null) {
            shop.setEmployeeIds(new ArrayList<>());
        }

        // Add owner to employees if not present
        boolean ownerInEmployees = shop.getEmployees().stream()
                .anyMatch(emp -> emp.getUserId().equals(owner.getUserId()));

        if (!ownerInEmployees) {
            shop.getEmployees().add(owner);
            shop.getEmployeeIds().add(owner.getUserId());
            hasShopChanges = true;
            log.info("Added owner to employees list for shop: {}", shop.getShopName());
        }

        // Save shop only if there are changes
        if (hasShopChanges) {
            shopRepository.save(shop);
            log.info("Saved updated shop information for: {}", shop.getShopName());
        }
    }

    /**
     * Get backup history for a user
     */
    @Override
    public List<UserBackupHistory> getUserBackupHistory(String userId) {
        return userBackupHistoryRepository.findByOriginalUserIdOrderByBackupDateDesc(userId);
    }

    /**
     * Get backup history for a shop
     */
    @Override
    public List<UserBackupHistory> getShopBackupHistory(String shopId) {
        requireAuthorizedShop(shopId);
        return userBackupHistoryRepository.findByShopIdOrderByBackupDateDesc(shopId);
    }

    private void requireAuthorizedShop(String shopId) {
        User currentUser = authUtil.loggedInUser();
        boolean platformOwner = currentUser.getRoles() != null && currentUser.getRoles().stream()
                .anyMatch(role -> role.getRoleName() == AppRole.OWNER);
        if (platformOwner) {
            return;
        }
        boolean assigned = currentUser.getAssignedShops() != null && currentUser.getAssignedShops().stream()
                .filter(java.util.Objects::nonNull)
                .anyMatch(shop -> shopId.equals(shop.getId()));
        if (!assigned) {
            throw new AccessDeniedException("You are not authorized to access this shop");
        }
    }

    /**
     * Get all backups where user was assigned to a specific shop
     */
    @Override
    public List<UserBackupHistory> getBackupsByAssignedShop(String shopId) {
        return userBackupHistoryRepository.findByAssignedShopIdsContaining(shopId);
    }
}
