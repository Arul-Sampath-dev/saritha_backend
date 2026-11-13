package com.saritha.fashions.modules.authentication.service;

import com.saritha.fashions.modules.authentication.exception.InvalidCredentialsException;
import com.saritha.fashions.modules.authentication.model.AuthRole;
import com.saritha.fashions.modules.authentication.model.AuthUser;
import com.saritha.fashions.modules.authentication.payload.AuthenticatedUserResponse;
import com.saritha.fashions.modules.authentication.payload.SignInRequest;
import com.saritha.fashions.modules.authentication.repository.AuthUserRepository;
import com.saritha.fashions.modules.authentication.repository.AuthUserRepositoryCustom.ShopInfo;
import com.saritha.fashions.security.jwt.JwtUtils;
import com.saritha.fashions.security.services.UserDetailsImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Sign-in / refresh / sign-out, ported from AuthController.authenticateUser / refreshToken.
 *
 * Tokens are issued exactly like before (JwtUtils.generateTokenFromUsername with
 * UserDetailsImpl and the user's tokenVersion), so the existing AuthTokenFilter accepts them.
 *
 * Fixes vs the old sign-in:
 * - unknown email and wrong password give the SAME 401 "Invalid email or password"
 *   (old: 404 "Email not found" vs 401 "Invalid password", which let anyone check which
 *   emails have accounts);
 * - for an unknown email a password hash is still checked against a dummy hash, so the
 *   response time does not reveal whether the account exists either;
 * - last-login time is recorded again (it was commented out).
 */
@Service
public class AuthenticationServiceImpl implements AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationServiceImpl.class);

    private static final String INVALID_CREDENTIALS = "Invalid email or password";
    private static final String TOKEN_TYPE = "Bearer";

    private final AuthUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final CurrentPrincipal currentPrincipal;
    private final long tokenLifetimeSeconds;
    /** Hash of a random value, compared against when the email is unknown (equal timing). */
    private final String dummyPasswordHash;

    public AuthenticationServiceImpl(AuthUserRepository userRepository,
                                     PasswordEncoder passwordEncoder,
                                     JwtUtils jwtUtils,
                                     CurrentPrincipal currentPrincipal,
                                     @Value("${spring.app.jwtExpirationMs}") long jwtExpirationMs) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.currentPrincipal = currentPrincipal;
        this.tokenLifetimeSeconds = jwtExpirationMs / 1000;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public AuthenticatedUserResponse signIn(SignInRequest request) {
        String email = normaliseEmail(request.getEmail());
        Optional<AuthUser> found = userRepository.findByEmail(email);

        String hashToCheck = found.map(AuthUser::getPassword).filter(Objects::nonNull).orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), hashToCheck);
        if (found.isEmpty() || !passwordMatches) {
            log.info("Failed sign-in for {}", email);
            throw new InvalidCredentialsException(INVALID_CREDENTIALS);
        }

        AuthUser user = found.get();
        try {
            userRepository.recordLogin(user.getUserId());
        } catch (Exception exception) {
            log.warn("Could not record last login for user {}: {}", user.getUserId(), exception.getMessage());
        }
        return toResponse(user, issueToken(user));
    }

    @Override
    public AuthenticatedUserResponse refreshToken() {
        UserDetailsImpl principal = currentPrincipal.require();
        // Reload: roles or tokenVersion may have changed since the old token was issued.
        AuthUser user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new InvalidCredentialsException("Account no longer exists"));
        return toResponse(user, issueToken(user));
    }

    @Override
    public void signOut() {
        UserDetailsImpl principal = currentPrincipal.require();
        if (!userRepository.revokeTokens(principal.getId())) {
            throw new InvalidCredentialsException("Account no longer exists");
        }
        log.info("User {} signed out (all tokens revoked)", principal.getId());
    }

    @Override
    public AuthenticatedUserResponse currentUser() {
        UserDetailsImpl principal = currentPrincipal.require();
        AuthUser user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new InvalidCredentialsException("Account no longer exists"));
        return toResponse(user, null);
    }

    // ------------------------------------------------------------------ helpers

    private String issueToken(AuthUser user) {
        List<GrantedAuthority> authorities = user.getRoles().stream()
                .map(AuthRole::getRoleName)
                .filter(Objects::nonNull)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role.name()))
                .toList();
        return jwtUtils.generateTokenFromUsername(new UserDetailsImpl(
                user.getUserId(), user.getEmail(), user.getPassword(), authorities, user.getTokenVersion()));
    }

    private AuthenticatedUserResponse toResponse(AuthUser user, String token) {
        Optional<ShopInfo> shop = userRepository.findShopOfUser(user.getUserId());
        return AuthenticatedUserResponse.builder()
                .userId(user.getUserId())
                .name(user.getName())
                .email(user.getEmail())
                .roles(user.getRoles().stream().map(AuthRole::getRoleName).filter(Objects::nonNull).map(Enum::name).toList())
                .shopId(shop.map(ShopInfo::shopId).orElse(null))
                .shopName(shop.map(ShopInfo::shopName).orElse(null))
                .accessToken(token)
                .tokenType(token == null ? null : TOKEN_TYPE)
                .expiresIn(token == null ? null : tokenLifetimeSeconds)
                .build();
    }

    static String normaliseEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
