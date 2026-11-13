package com.saritha.fashions.modules.authentication;

import com.saritha.fashions.exception.APIResponse;
import com.saritha.fashions.modules.authentication.payload.AuthMessageResponse;
import com.saritha.fashions.modules.authentication.payload.AuthenticatedUserResponse;
import com.saritha.fashions.modules.authentication.payload.PasswordResetCodeRequest;
import com.saritha.fashions.modules.authentication.payload.RegisterUserRequest;
import com.saritha.fashions.modules.authentication.payload.RegisteredUserResponse;
import com.saritha.fashions.modules.authentication.payload.ResetPasswordRequest;
import com.saritha.fashions.modules.authentication.payload.SignInRequest;
import com.saritha.fashions.modules.authentication.service.AuthenticationService;
import com.saritha.fashions.modules.authentication.service.PasswordResetService;
import com.saritha.fashions.modules.authentication.service.UserRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints. Replaces /v1/api/auth (AuthController).
 *
 *  POST /sign-in                public   email + password â†’ user + access token
 *  POST /token/refresh          token    new access token
 *  POST /sign-out               token    revoke all of the user's tokens
 *  GET  /me                     token    current user
 *  POST /users                  OWNER    create a user account
 *  POST /password-reset/code    public   email a reset code
 *  POST /password-reset         public   set a new password with the code
 *
 * Access rules are in WebSecurityConfig (and checked again in the services).
 */
@RestController
@RequestMapping("/v1/api/authentication")
@Tag(name = "Authentication (new)",
        description = "Sign-in, tokens, sign-out, user registration and password reset. The access token is "
                + "returned in the body (accessToken) and in the Authorization response header; send it as "
                + "`Authorization: Bearer <token>`.")
public class AuthenticationController {

    private static final String BEARER = "Bearer ";
    /**
     * Swagger security scheme (defined in SarithaFashionsApplication). Only the endpoints that need a
     * token declare it, so sign-in and password reset stay public in Swagger UI.
     */
    private static final String BEARER_AUTH = "Bearer Authentication";
    private static final String UNAUTHORIZED_EXAMPLE = "{\"message\": \"Invalid email or password\", \"status\": false}";

    private final AuthenticationService authenticationService;
    private final UserRegistrationService registrationService;
    private final PasswordResetService passwordResetService;

    public AuthenticationController(AuthenticationService authenticationService,
                                    UserRegistrationService registrationService,
                                    PasswordResetService passwordResetService) {
        this.authenticationService = authenticationService;
        this.registrationService = registrationService;
        this.passwordResetService = passwordResetService;
    }

    @Operation(
            summary = "Sign in",
            description = "Checks email and password. A wrong email and a wrong password give the same 401, so the "
                    + "endpoint cannot be used to find out which emails have accounts.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = SignInRequest.class),
                            examples = @ExampleObject(value = "{ \"email\": \"asha@example.com\", \"password\": \"StrongPass123\" }"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Signed in",
                            headers = @Header(name = "Authorization", description = "Bearer access token",
                                    schema = @Schema(type = "string", example = "Bearer <JWT>")),
                            content = @Content(schema = @Schema(implementation = AuthenticatedUserResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Missing / invalid email or password field",
                            content = @Content(examples = @ExampleObject(value = "{\"email\": \"email must be a valid email address\"}"))),
                    @ApiResponse(responseCode = "401", description = "Wrong email or password",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
                    @ApiResponse(responseCode = "429", description = "Too many requests from this IP (rate limit)")
            })
    @PostMapping("/sign-in")
    public ResponseEntity<AuthenticatedUserResponse> signIn(@Valid @RequestBody SignInRequest request) {
        AuthenticatedUserResponse response = authenticationService.signIn(request);
        return withTokenHeader(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Refresh the access token",
            security = @SecurityRequirement(name = BEARER_AUTH),
            description = "Send a valid (not expired, not revoked) token; get a new one with a fresh expiry and the "
                    + "user's current roles.",
            parameters = @Parameter(hidden = true),
            responses = {
                    @ApiResponse(responseCode = "200", description = "New token issued",
                            headers = @Header(name = "Authorization", description = "New bearer access token",
                                    schema = @Schema(type = "string", example = "Bearer <JWT>")),
                            content = @Content(schema = @Schema(implementation = AuthenticatedUserResponse.class))),
                    @ApiResponse(responseCode = "401", description = "No token, or the token is expired / invalid / revoked")
            })
    @PostMapping("/token/refresh")
    public ResponseEntity<AuthenticatedUserResponse> refreshToken() {
        return withTokenHeader(authenticationService.refreshToken(), HttpStatus.OK);
    }

    @Operation(
            summary = "Sign out everywhere",
            security = @SecurityRequirement(name = BEARER_AUTH),
            description = "Revokes ALL access tokens of the current user (every device). The user must sign in again.",
            parameters = @Parameter(hidden = true),
            responses = {
                    @ApiResponse(responseCode = "204", description = "Signed out"),
                    @ApiResponse(responseCode = "401", description = "No valid token")
            })
    @PostMapping("/sign-out")
    public ResponseEntity<Void> signOut() {
        authenticationService.signOut();
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Current user",
            security = @SecurityRequirement(name = BEARER_AUTH),
            description = "The signed-in user's id, name, email, roles and shop.",
            parameters = @Parameter(hidden = true),
            responses = {
                    @ApiResponse(responseCode = "200", description = "The current user",
                            content = @Content(schema = @Schema(implementation = AuthenticatedUserResponse.class))),
                    @ApiResponse(responseCode = "401", description = "No valid token")
            })
    @GetMapping("/me")
    public ResponseEntity<AuthenticatedUserResponse> currentUser() {
        return ResponseEntity.ok(authenticationService.currentUser());
    }

    @Operation(
            summary = "Create a user account (platform owner)",
            security = @SecurityRequirement(name = BEARER_AUTH),
            description = "Creates an account and links it to its shop. Only the platform OWNER can call this. "
                    + "Every role except OWNER needs a shopId. Roles accept AppRole names or the old short names "
                    + "(shopOwner, tailorMaster, tailor, shopAssistant, accountant, owner).",
            parameters = @Parameter(hidden = true),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = RegisterUserRequest.class),
                            examples = @ExampleObject(value = "{\n  \"name\": \"Lakshmi\",\n  \"email\": \"lakshmi@example.com\",\n"
                                    + "  \"password\": \"Welcome2026\",\n  \"roles\": [\"TAILOR\"],\n"
                                    + "  \"shopId\": \"665f1c2e9b1e8a3d4c7f0a12\",\n  \"phone\": \"9876543210\",\n  \"salary\": 18000\n}"))),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Account created",
                            content = @Content(schema = @Schema(implementation = RegisteredUserResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid fields / weak password / unknown role / missing or unknown shop",
                            content = @Content(examples = @ExampleObject(value = "{\"message\": \"shopId is required for role(s) [TAILOR]\", \"status\": false}"))),
                    @ApiResponse(responseCode = "401", description = "No valid token"),
                    @ApiResponse(responseCode = "403", description = "Not the platform OWNER"),
                    @ApiResponse(responseCode = "409", description = "Email already registered",
                            content = @Content(schema = @Schema(implementation = APIResponse.class)))
            })
    @PostMapping("/users")
    public ResponseEntity<RegisteredUserResponse> registerUser(@Valid @RequestBody RegisterUserRequest request) {
        return new ResponseEntity<>(registrationService.register(request), HttpStatus.CREATED);
    }

    @Operation(
            summary = "Request a password reset code",
            description = "If the email has an account, a 6-digit code (valid 10 minutes) is emailed to it. The response is "
                    + "always the same, so it does not reveal whether the email has an account. A new code can be "
                    + "requested at most once per minute; requesting a new code cancels the previous one.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = PasswordResetCodeRequest.class),
                            examples = @ExampleObject(value = "{ \"email\": \"asha@example.com\" }"))),
            responses = {
                    @ApiResponse(responseCode = "202", description = "Accepted (same response for every email)",
                            content = @Content(schema = @Schema(implementation = AuthMessageResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid email format")
            })
    @PostMapping("/password-reset/code")
    public ResponseEntity<AuthMessageResponse> requestResetCode(@Valid @RequestBody PasswordResetCodeRequest request) {
        passwordResetService.requestResetCode(request.getEmail());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new AuthMessageResponse(
                "If an account exists for this email, a reset code has been sent. It is valid for 10 minutes."));
    }

    @Operation(
            summary = "Reset the password",
            description = "Sets a new password using the emailed code. The code works once; after 5 wrong codes it is "
                    + "cancelled and a new one must be requested. A successful reset signs the user out on every device.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = ResetPasswordRequest.class),
                            examples = @ExampleObject(value = "{\n  \"email\": \"asha@example.com\",\n  \"code\": \"482913\",\n"
                                    + "  \"newPassword\": \"NewStrong2026\",\n  \"confirmPassword\": \"NewStrong2026\"\n}"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Password changed",
                            content = @Content(schema = @Schema(implementation = AuthMessageResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid / expired code, weak password, or passwords do not match",
                            content = @Content(examples = @ExampleObject(value = "{\"message\": \"Invalid or expired code. Please check the code or request a new one.\", \"status\": false}"))),
                    @ApiResponse(responseCode = "429", description = "Too many wrong codes; request a new code",
                            content = @Content(schema = @Schema(implementation = APIResponse.class)))
            })
    @PostMapping("/password-reset")
    public ResponseEntity<AuthMessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.ok(new AuthMessageResponse("Password has been changed. Please sign in again."));
    }

    private static ResponseEntity<AuthenticatedUserResponse> withTokenHeader(AuthenticatedUserResponse body, HttpStatus status) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.AUTHORIZATION, BEARER + body.getAccessToken())
                .body(body);
    }
}
