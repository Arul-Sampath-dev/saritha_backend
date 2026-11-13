package com.saritha.fashions.modules.authentication.service;

import com.saritha.fashions.modules.authentication.payload.AuthenticatedUserResponse;
import com.saritha.fashions.modules.authentication.payload.SignInRequest;

/**
 * Sign-in, token refresh, sign-out and the current user.
 */
public interface AuthenticationService {

    /** Checks email + password and issues an access token. */
    AuthenticatedUserResponse signIn(SignInRequest request);

    /** Issues a new access token for the (valid) token of the current request. */
    AuthenticatedUserResponse refreshToken();

    /** Revokes ALL access tokens of the current user (signs out on every device). */
    void signOut();

    /** The current user's account details (no token). */
    AuthenticatedUserResponse currentUser();
}
