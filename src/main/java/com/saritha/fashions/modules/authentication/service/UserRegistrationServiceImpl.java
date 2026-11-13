package com.saritha.fashions.modules.authentication.service;

import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.AppRole;
import com.saritha.fashions.modules.authentication.exception.EmailAlreadyRegisteredException;
import com.saritha.fashions.modules.authentication.model.AuthRole;
import com.saritha.fashions.modules.authentication.model.AuthUser;
import com.saritha.fashions.modules.authentication.model.RoleNames;
import com.saritha.fashions.modules.authentication.payload.RegisterUserRequest;
import com.saritha.fashions.modules.authentication.payload.RegisteredUserResponse;
import com.saritha.fashions.modules.authentication.repository.AuthUserRepository;
import com.saritha.fashions.security.services.UserDetailsImpl;
import com.saritha.fashions.util.DateTimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.saritha.fashions.modules.authentication.service.AuthenticationServiceImpl.normaliseEmail;

/**
 * User registration, ported from AuthController.registerUser.
 *
 * Fixes vs the old signup:
 * - a password is required and must meet the policy (old: silently used "Saritha@123");
 * - unknown role names are rejected (old: silently became ROLE_USER);
 * - shop roles must name an existing shop;
 * - the user is added to the shop with an atomic $addToSet (old: loaded and re-saved the
 *   whole shop, so concurrent sign-ups could overwrite each other);
 * - duplicate email is a 409, also when two requests race (unique index on users.email).
 */
@Service
public class UserRegistrationServiceImpl implements UserRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(UserRegistrationServiceImpl.class);

    private final AuthUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentPrincipal currentPrincipal;

    public UserRegistrationServiceImpl(AuthUserRepository userRepository,
                                       PasswordEncoder passwordEncoder,
                                       CurrentPrincipal currentPrincipal) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentPrincipal = currentPrincipal;
    }

    @Override
    public RegisteredUserResponse register(RegisterUserRequest request) {
        UserDetailsImpl actor = currentPrincipal.require();
        if (!currentPrincipal.hasAuthority(actor, AppRole.OWNER.name())) {
            throw new AccessDeniedException("Only the platform owner can create user accounts");
        }

        String email = normaliseEmail(request.getEmail());
        Set<AppRole> roles = parseRoles(request.getRoles());
        String shopId = resolveShop(request.getShopId(), roles);

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }

        List<AuthRole> roleDocuments = userRepository.findRoles(roles);
        if (roleDocuments.size() != roles.size()) {
            Set<AppRole> found = roleDocuments.stream().map(AuthRole::getRoleName).collect(Collectors.toSet());
            Set<AppRole> missing = EnumSet.copyOf(roles);
            missing.removeAll(found);
            throw new APIException("Role(s) " + missing + " are not set up in the roles collection");
        }

        AuthUser user = AuthUser.builder()
                .username(UUID.randomUUID().toString())
                .name(request.getName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(blankToNull(request.getPhone()))
                .salary(request.getSalary() == null ? 0.0 : request.getSalary())
                .address(blankToNull(request.getAddress()))
                .roles(roleDocuments)
                .accountCreationDate(DateTimeUtils.getNowInIST())
                .build();

        try {
            user = userRepository.insertUser(user, shopId);
        } catch (DuplicateKeyException exception) {
            throw new EmailAlreadyRegisteredException(email);
        }

        if (shopId != null) {
            try {
                userRepository.addEmployeeToShop(shopId, user.getUserId());
            } catch (RuntimeException exception) {
                // Do not leave a user that the shop does not know about.
                userRepository.deleteById(user.getUserId());
                throw exception;
            }
        }

        List<String> roleNames = roles.stream().map(Enum::name).toList();
        log.info("User {} ({}) created by {} with roles {} in shop {}",
                user.getUserId(), email, actor.getId(), roleNames, shopId);
        return new RegisteredUserResponse(user.getUserId(), user.getName(), email, roleNames, shopId);
    }

    @Override
    public boolean isEmailRegistered(String email) {
        return email != null && !email.isBlank() && userRepository.existsByEmail(normaliseEmail(email));
    }

    private static Set<AppRole> parseRoles(Set<String> requested) {
        Set<AppRole> roles = EnumSet.noneOf(AppRole.class);
        for (String name : requested) {
            roles.add(RoleNames.parse(name).orElseThrow(() -> new APIException("Unknown role: " + name
                    + ". Use one of OWNER, SHOP_OWNER, TAILOR_MASTER, TAILOR, SHOP_ASSISTANT, ACCOUNTANT")));
        }
        return roles;
    }

    /** Validates the shop: required for shop roles, must exist when given. */
    private String resolveShop(String requestedShopId, Set<AppRole> roles) {
        String shopId = blankToNull(requestedShopId);
        List<AppRole> shopRoles = roles.stream().filter(RoleNames::requiresShop).toList();
        if (shopId == null) {
            if (!shopRoles.isEmpty()) {
                throw new APIException("shopId is required for role(s) " + shopRoles);
            }
            return null;
        }
        if (!userRepository.shopExists(shopId)) {
            throw new APIException("Shop not found: " + shopId);
        }
        return shopId;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
