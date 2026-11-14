package com.saritha.fashions.modules.shop.service;

import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.AppRole;
import com.saritha.fashions.model.User;
import com.saritha.fashions.modules.authentication.exception.EmailAlreadyRegisteredException;
import com.saritha.fashions.modules.authentication.payload.RegisterUserRequest;
import com.saritha.fashions.modules.authentication.payload.RegisteredUserResponse;
import com.saritha.fashions.modules.authentication.service.UserRegistrationService;
import com.saritha.fashions.modules.shop.model.ManagedShop;
import com.saritha.fashions.modules.shop.model.PettyCashEntry;
import com.saritha.fashions.modules.shop.payload.AddPettyCashRequest;
import com.saritha.fashions.modules.shop.payload.CreateShopRequest;
import com.saritha.fashions.modules.shop.payload.PettyCashTopUpResponse;
import com.saritha.fashions.modules.shop.payload.ShopOwnerUpdateResponse;
import com.saritha.fashions.modules.shop.payload.UpdateShopOwnerRequest;
import com.saritha.fashions.modules.shop.payload.UpdateShopRequest;
import com.saritha.fashions.modules.shop.repository.ShopRepository;
import com.saritha.fashions.util.AuthUtil;
import com.saritha.fashions.util.DateTimeUtils;
import org.bson.Document;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Write-side implementation for shop management
 * (create / update / delete / petty cash top-ups).
 */
@Service
public class ShopWriteServiceImpl implements ShopWriteService {

    private static final Logger log = LoggerFactory.getLogger(ShopWriteServiceImpl.class);

    private final ShopRepository shopRepository;
    private final ModelMapper modelMapper;
    private final UserRegistrationService registrationService;
    private final AuthUtil authUtil;
    private final PasswordEncoder passwordEncoder;

    public ShopWriteServiceImpl(ShopRepository shopRepository,
                                          ModelMapper modelMapper,
                                          UserRegistrationService registrationService,
                                          AuthUtil authUtil,
                                          PasswordEncoder passwordEncoder) {
        this.shopRepository = shopRepository;
        this.modelMapper = modelMapper;
        this.registrationService = registrationService;
        this.authUtil = authUtil;
        this.passwordEncoder = passwordEncoder;
    }

    // ================================================================ shop owner details

    private static final String DEFAULT_BACKUP_REASON = "Shop Owner Details Update";

    /**
     * Updates the shop owner's account. Ported from the legacy ShopOwnerServiceImpl.updateShopOwner
     * (PUT /v1/api/owner/shop/update-owner-details), with the same effects:
     *  1. back up the owner's current details in user_backup_history (password redacted, same
     *     document shape as the legacy UserBackupHistory);
     *  2. update name / phone / email / password / salary on the user;
     *  3. mirror a new name to shop.ownerName and a new phone to shop.shopContactNumber, and make
     *     sure the owner is in the shop's employeeIds.
     *
     * Fixes vs the legacy code:
     * - fields are validated (10-digit phone, email format, password policy, salary range);
     * - the owner is found automatically when ownerUserId is left out, and a given id must be a
     *   SHOP_OWNER of this shop (legacy: any user id was accepted, for any shop);
     * - a taken email is a clear 400, also when two requests race (unique index);
     * - single-field atomic updates instead of re-saving the whole user and shop documents;
     * - an email or password change signs the owner out everywhere (tokens revoked);
     * - nothing is written (no backup either) when no value actually changes.
     */
    @Override
    public ShopOwnerUpdateResponse updateShopOwner(String shopId, UpdateShopOwnerRequest request) {
        requirePlatformOwner();
        String actorId = authUtil.loggedInUser().getUserId();

        Document shop = shopRepository.findShopDocument(shopId)
                .orElseThrow(() -> new APIException("Shop Not Found"));
        Document owner = resolveShopOwner(shop, blankToNull(request.getOwnerUserId()));
        String ownerId = owner.get("_id").toString();

        String name = blankToNull(request.getName());
        String phone = blankToNull(request.getPhone());
        String email = blankToNull(request.getEmail()) == null ? null : request.getEmail().trim().toLowerCase(Locale.ROOT);
        String password = blankToNull(request.getPassword());
        Double salary = request.getSalary();

        Update update = new Update();
        List<String> changed = new ArrayList<>();
        if (name != null && !name.equals(owner.getString("name"))) {
            update.set("name", name);
            changed.add("name");
        }
        if (phone != null && !phone.equals(owner.getString("phone"))) {
            update.set("phone", phone);
            changed.add("phone");
        }
        if (email != null && !email.equalsIgnoreCase(String.valueOf(owner.get("email")))) {
            if (shopRepository.emailUsedByOtherUser(email, ownerId)) {
                throw new APIException("Email is already in use: " + email);
            }
            update.set("email", email);
            changed.add("email");
        }
        if (password != null) {
            update.set("password", passwordEncoder.encode(password));
            changed.add("password");
        }
        Object storedSalary = owner.get("salary");
        if (salary != null && !(storedSalary instanceof Number number && number.doubleValue() == salary)) {
            update.set("salary", salary);
            changed.add("salary");
        }

        if (changed.isEmpty()) {
            return ownerResponse(shopId, owner, List.of(), false, "Nothing changed: the values are the same as before.");
        }

        boolean signOut = changed.contains("email") || changed.contains("password");
        if (signOut) {
            update.inc("tokenVersion", 1);
        }
        update.set("updatedAt", new Date());

        shopRepository.insertUserBackup(ownerBackup(owner, shop, actorId,
                firstNonBlank(request.getReason(), DEFAULT_BACKUP_REASON)));
        try {
            if (!shopRepository.updateUser(ownerId, update)) {
                throw new APIException("No user found");
            }
        } catch (DuplicateKeyException exception) {
            throw new APIException("Email is already in use: " + email);
        }
        shopRepository.updateShopOwnerInfo(shopId, ownerId,
                changed.contains("name") ? name : null, changed.contains("phone") ? phone : null);

        log.info("Shop owner {} of shop {} updated by {}: {}", ownerId, shopId, actorId, changed);
        Document updated = shopRepository.findUserDocuments(List.of(ownerId)).stream().findFirst().orElse(owner);
        return ownerResponse(shopId, updated, changed, signOut,
                "Shop owner details updated. The previous details were backed up."
                        + (signOut ? " The owner was signed out and must sign in again." : ""));
    }

    /** The given id must be a SHOP_OWNER of this shop; without an id, the shop's only SHOP_OWNER. */
    private Document resolveShopOwner(Document shop, String requestedOwnerId) {
        List<String> employeeIds = new ArrayList<>();
        if (shop.get("employeeIds") instanceof List<?> ids) {
            ids.stream().filter(Objects::nonNull).forEach(id -> employeeIds.add(id.toString()));
        }
        List<Document> shopOwners = shopRepository.findUserDocuments(employeeIds).stream()
                .filter(ShopWriteServiceImpl::isShopOwner)
                .toList();
        if (requestedOwnerId != null) {
            return shopOwners.stream()
                    .filter(user -> requestedOwnerId.equals(user.get("_id").toString()))
                    .findFirst()
                    .orElseThrow(() -> new APIException("User " + requestedOwnerId + " is not the shop owner of this shop"));
        }
        if (shopOwners.isEmpty()) {
            throw new APIException("This shop has no shop owner account");
        }
        if (shopOwners.size() > 1) {
            throw new APIException("This shop has more than one shop owner; send ownerUserId");
        }
        return shopOwners.get(0);
    }

    private static boolean isShopOwner(Document user) {
        return user.get("roles") instanceof List<?> roles && roles.stream()
                .anyMatch(role -> role instanceof Document document
                        && AppRole.SHOP_OWNER.name().equals(String.valueOf(document.get("roleName"))));
    }

    /** Same document shape as the legacy UserBackupHistory (DBRef fields and password left out). */
    private static Document ownerBackup(Document owner, Document shop, String performedBy, String reason) {
        Document userData = new Document(owner);
        userData.put("password", "[BACKUP_REDACTED]");
        userData.remove("assignedShops");
        userData.remove("allExpenses");
        userData.remove("authToken");
        userData.remove("tokenVersion");
        Date now = new Date();
        String shopId = shop.get("_id").toString();
        Document shopInfo = new Document("shopId", shopId)
                .append("shopName", shop.getString("shopName"))
                .append("shopType", shop.getString("shopType"))
                .append("assignedDate", now);
        return new Document("originalUserId", owner.get("_id").toString())
                .append("userData", userData)
                .append("backupReason", reason)
                .append("performedBy", performedBy)
                .append("backupDate", now)
                .append("shopId", shopId)
                .append("assignedShopIds", List.of(shopId))
                .append("assignedShopsInfo", List.of(shopInfo));
    }

    private static ShopOwnerUpdateResponse ownerResponse(String shopId, Document owner, List<String> changed,
                                                         boolean signedOut, String message) {
        Object salary = owner.get("salary");
        return new ShopOwnerUpdateResponse(shopId, owner.get("_id").toString(), owner.getString("name"),
                owner.get("email") == null ? null : owner.get("email").toString(), owner.getString("phone"),
                salary instanceof Number number ? number.doubleValue() : null, changed, signedOut, message);
    }

    private static String firstNonBlank(String value, String fallback) {
        return value != null && !value.isBlank() ? value.trim() : fallback;
    }

    /**
     * Creates a shop and its SHOP_OWNER account.
     *
     * The owner account is created by the authentication module (UserRegistrationService), which
     * also links the owner to the shop (employeeIds) atomically. Previously this called the legacy
     * AuthController.registerUser and parsed its error text.
     *
     * Order (MongoDB has no transaction here, so failures are undone by hand):
     *  1. The request was validated by @Valid (same owner rules as registration).
     *  2. Platform OWNER check, and an early "email already in use" check, so the usual failures
     *     happen before anything is saved.
     *  3. Save the shop (registration needs an existing shop id).
     *  4. Register the owner with that shop. If it fails for any reason (including an email taken
     *     meanwhile), the shop is deleted again and the error is returned.
     *
     * @throws AccessDeniedException if the caller is not the platform OWNER
     * @throws APIException          email already in use, or the owner could not be created
     */
    @Override
    public ManagedShop create(CreateShopRequest shopRequest) {
        requirePlatformOwner();
        String email = shopRequest.getOwnerEmailId().trim();
        if (registrationService.isEmailRegistered(email)) {
            throw new APIException("Email is already in use: " + email.toLowerCase(Locale.ROOT));
        }

        ManagedShop shop = ManagedShop.builder()
                .shopName(shopRequest.getShopName().trim())
                .ownerName(shopRequest.getOwnerName().trim())
                .shopAddress(blankToNull(shopRequest.getShopAddress()))
                .shopContactNumber(blankToNull(shopRequest.getOwnerNumber()))
                .gstNumber(blankToNull(shopRequest.getGstNumber()))
                .build();
        ManagedShop savedShop = shopRepository.save(shop);

        RegisterUserRequest owner = new RegisterUserRequest();
        owner.setName(shopRequest.getOwnerName().trim());
        owner.setEmail(email);
        owner.setPassword(shopRequest.getPassword());
        owner.setRoles(Set.of(AppRole.SHOP_OWNER.name()));
        owner.setShopId(savedShop.getId());
        owner.setPhone(blankToNull(shopRequest.getOwnerNumber()));
        owner.setSalary(shopRequest.getSalary());
        owner.setAddress(blankToNull(shopRequest.getShopAddress()));

        RegisteredUserResponse registered;
        try {
            registered = registrationService.register(owner);
        } catch (RuntimeException exception) {
            deleteShopQuietly(savedShop.getId());
            if (exception instanceof EmailAlreadyRegisteredException) {
                throw new APIException("Email is already in use: " + email.toLowerCase(Locale.ROOT));
            }
            if (exception instanceof APIException || exception instanceof AccessDeniedException) {
                throw exception;
            }
            log.error("Shop {} was not created: the owner account could not be created", savedShop.getId(), exception);
            throw new APIException("The shop owner account could not be created, so the shop was not created. Please try again.");
        }

        log.info("Shop {} ({}) created with owner {}", savedShop.getId(), savedShop.getShopName(), registered.userId());
        // Re-read so the response includes the owner in employeeIds.
        return shopRepository.findById(savedShop.getId()).orElse(savedShop);
    }

    private void requirePlatformOwner() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Login required to create a shop");
        }
        User user = authUtil.loggedInUser();
        boolean owner = user.getRoles() != null && user.getRoles().stream()
                .filter(Objects::nonNull).anyMatch(role -> role.getRoleName() == AppRole.OWNER);
        if (!owner) {
            throw new AccessDeniedException("Only the platform owner can create shops");
        }
    }

    private void deleteShopQuietly(String shopId) {
        try {
            shopRepository.deleteById(shopId);
        } catch (RuntimeException exception) {
            log.error("Owner registration failed AND the new shop {} could not be removed; delete it manually",
                    shopId, exception);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Partially updates a shop (null fields are skipped by ModelMapper).
     *
     * @return the updated shop, or null if the id does not exist
     */
    @Override
    public ManagedShop update(String shopId, UpdateShopRequest shopUpdate) {
        return shopRepository.findById(shopId)
                .map(existingShop -> {
                    modelMapper.map(shopUpdate, existingShop);
                    return shopRepository.save(existingShop);
                })
                .orElse(null);
    }

    // we dont need to delete the shop so not implemented
    @Override
    public void delete(String shopId) {
        // shopRepository.deleteById(shopId);
    }

    /**
     * Adds petty cash to a shop. Ported from ShopServiceImpl.setPettyCashToShop.
     *
     * Steps:
     * 1. Requires a logged-in user (a JWT), since we must record who added the cash.
     * 2. Authorization: the user must be a platform OWNER or be assigned to this shop;
     *    otherwise AccessDeniedException (403).
     * 3. Atomically adds the amount to pettyCash and appends a PettyCashEntry
     *    (amount, IST timestamp, user id) to allPettyCashDetails.
     *
     * Unlike the original, this does not load and re-save the whole shop, so two
     * top-ups at the same time cannot overwrite each other.
     *
     * @throws AccessDeniedException if not logged in or not allowed for this shop
     * @throws APIException          "Shop Not Found" if the shop does not exist
     */
    @Override
    public PettyCashTopUpResponse addPettyCash(String shopId, AddPettyCashRequest request) {
        User currentUser = requireLoggedInUser();

        if (!isPlatformOwner(currentUser) && !isAssignedToShop(currentUser, shopId)) {
            throw new AccessDeniedException("You are not authorized to access this shop");
        }

        PettyCashEntry entry = PettyCashEntry.builder()
                .amount(request.getAmount())
                .addingDate(DateTimeUtils.getNowInIST())
                .addedBy(currentUser.getUserId())
                .build();

        ManagedShop updatedShop = shopRepository.addPettyCash(shopId, entry);
        if (updatedShop == null) {
            throw new APIException("Shop Not Found");
        }

        return PettyCashTopUpResponse.builder()
                .shopId(updatedShop.getId())
                .shopName(updatedShop.getShopName())
                .amountAdded(entry.getAmount())
                .pettyCashBalance(updatedShop.getPettyCash())
                .addedBy(entry.getAddedBy())
                .addedAt(entry.getAddingDate())
                .build();
    }

    /**
     * Returns the logged-in user, or throws AccessDeniedException for anonymous
     * requests. WebSecurityConfig already requires a token; this keeps the service safe
     * on its own.
     */
    private User requireLoggedInUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Login required to add petty cash");
        }
        return authUtil.loggedInUser();
    }

    private boolean isPlatformOwner(User user) {
        return user.getRoles() != null && user.getRoles().stream()
                .anyMatch(role -> role.getRoleName() == AppRole.OWNER);
    }

    private boolean isAssignedToShop(User user, String shopId) {
        return user.getAssignedShops() != null && user.getAssignedShops().stream()
                .filter(Objects::nonNull)
                .anyMatch(shop -> shopId.equals(shop.getId()));
    }
}
