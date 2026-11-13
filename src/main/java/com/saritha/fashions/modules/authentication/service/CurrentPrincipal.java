package com.saritha.fashions.modules.authentication.service;

import com.saritha.fashions.modules.authentication.exception.InvalidCredentialsException;
import com.saritha.fashions.security.services.UserDetailsImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The authenticated user of the current request, as set by AuthTokenFilter after it has
 * verified the JWT signature, expiry and tokenVersion.
 */
@Component
public class CurrentPrincipal {

    /**
     * @throws InvalidCredentialsException (401) if the request carries no valid token
     */
    public UserDetailsImpl require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl principal)) {
            throw new InvalidCredentialsException("Sign in required");
        }
        return principal;
    }

    public boolean hasAuthority(UserDetailsImpl principal, String authority) {
        return principal.getAuthorities().stream().anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}
