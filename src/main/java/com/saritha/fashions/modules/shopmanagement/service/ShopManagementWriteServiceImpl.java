package com.saritha.fashions.modules.shopmanagement.service;

import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.AppRole;
import com.saritha.fashions.model.User;
import com.saritha.fashions.modules.authentication.exception.EmailAlreadyRegisteredException;
import com.saritha.fashions.modules.authentication.payload.RegisterUserRequest;
import com.saritha.fashions.modules.authentication.payload.RegisteredUserResponse;
import com.saritha.fashions.modules.authentication.service.UserRegistrationService;
import com.saritha.fashions.modules.shopmanagement.model.ManagedShop;
import com.saritha.fashions.modules.shopmanagement.model.PettyCashEntry;
import com.saritha.fashions.modules.shopmanagement.payload.AddPettyCashRequest;
import com.saritha.fashions.modules.shopmanagement.payload.CreateShopRequest;
import com.saritha.fashions.modules.shopmanagement.payload.PettyCashTopUpResponse;
import com.saritha.fashions.modules.shopmanagement.payload.UpdateShopRequest;
import com.saritha.fashions.modules.shopmanagement.repository.ShopManagementRepository;
import com.saritha.fashions.util.AuthUtil;
import com.saritha.fashions.util.DateTimeUtils;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Write-side implementation for shop management
 * (create / update / delete / petty cash top-ups).
 */
@Service
public class ShopManagementWriteServiceImpl implements ShopManagementWriteService {

    private static final Logger log = LoggerFactory.getLogger(ShopManagementWriteServiceImpl.class);

    private final ShopManagementRepository shopRepository;
    private final ModelMapper modelMapper;
    private final UserRegistrationService registrationService;
    private final AuthUtil authUtil;

    public ShopManagementWriteServiceImpl(ShopManagementRepository shopRepository,
                                          ModelMapper modelMapper,
                                          UserRegistrationService registrationService,
                                          AuthUtil authUtil) {
        this.shopRepository = shopRepository;
        this.modelMapper = modelMapper;
        this.registrationService = registrationService;
        this.authUtil = authUtil;
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
