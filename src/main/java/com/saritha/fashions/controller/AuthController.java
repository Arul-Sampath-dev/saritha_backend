package com.saritha.fashions.controller;

import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.exception.APIResponse;
import com.saritha.fashions.model.*;
import com.saritha.fashions.repository.ForgetPasswordRepository;
import com.saritha.fashions.repository.AggregationInterface.UserWithShopProjection;
import com.saritha.fashions.repository.RoleRepository;
import com.saritha.fashions.repository.ShopRepository;
import com.saritha.fashions.repository.UserRepository;
import com.saritha.fashions.security.jwt.JwtUtils;
import com.saritha.fashions.security.request.LoginRequest;
import com.saritha.fashions.security.request.SignupRequest;
import com.saritha.fashions.security.response.MessageResponse;
import com.saritha.fashions.security.response.UserInfoResponse;
import com.saritha.fashions.security.services.UserDetailsImpl;
import com.saritha.fashions.service.BrevoEmailServiceForForgotPassword;
import com.saritha.fashions.util.ChangePassword;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.core.GrantedAuthority;
import com.saritha.fashions.util.AuthUtil;
import com.saritha.fashions.util.DateTimeUtils;
import io.jsonwebtoken.ExpiredJwtException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/api/auth")
@Tag(name = "Authentication", description = "Signup, sign-in, token refresh, and password reset")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    private AuthUtil authUtil;

    @Autowired
    @Qualifier("taskExecutor")
    private Executor taskExecutor;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ForgetPasswordRepository forgetPasswordRepository;

    @Autowired
    private BrevoEmailServiceForForgotPassword brevoEmailService;


    @PostMapping("/signup")
    @Transactional
    @PreAuthorize("hasAuthority('OWNER')")
    @Operation(
            summary = "Register a user",
            description = "Creates an account. Requires an authenticated OWNER bearer token.",
            parameters = @Parameter(name = "Authorization", in = ParameterIn.HEADER, required = true,
                    description = "OWNER access token", example = "Bearer <JWT>"),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = SignupRequest.class),
                            examples = @ExampleObject(value = "{\"name\":\"Asha\",\"phone\":\"9876543210\",\"email\":\"asha@example.com\",\"password\":\"StrongPassword123!\",\"roles\":[\"tailor\"],\"salary\":0,\"address\":\"Chennai\",\"assignedShops\":\"<shopId>\"}"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "User created",
                            content = @Content(schema = @Schema(implementation = MessageResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Email already in use",
                            content = @Content(schema = @Schema(implementation = MessageResponse.class))),
                    @ApiResponse(responseCode = "403", description = "OWNER authorization required")
            })
    public ResponseEntity<?> registerUser(@RequestBody SignupRequest signupRequest) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUserRole = authentication.getAuthorities().iterator().next().getAuthority();

        if (!currentUserRole.equals("OWNER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only Shop Owners can register new users.");
        }

        if (userRepository.existsByEmail(signupRequest.getEmail().toLowerCase())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Email is already in use!"));
        }

        String username = String.valueOf(UUID.randomUUID());
        String phone = signupRequest.getPhone() == null ? "0000000000" : signupRequest.getPhone();
        User user = new User(username,
                signupRequest.getEmail().toLowerCase(),
                encoder.encode(signupRequest.getPassword() != null
                        ? signupRequest.getPassword() : "Saritha@123"),
                signupRequest.getName(), phone);
        user.setAccountCreationDate(DateTimeUtils.getNowInIST());

        Set<String> requestedRoles = signupRequest.getRoles();
        Set<Role> roles = new HashSet<>();
        if (requestedRoles == null) {
            roles.add(roleRepository.findByRoleName(AppRole.ROLE_USER)
                    .orElseThrow(() -> new RuntimeException("Error: Role is not found.")));
        } else {
            requestedRoles.forEach(role -> {
                switch (role) {
                    case "owner" -> roles.add(roleRepository.findByRoleName(AppRole.OWNER)
                            .orElseThrow(() -> new RuntimeException("Error: Role not found")));
                    case "shopOwner" -> roles.add(roleRepository.findByRoleName(AppRole.SHOP_OWNER)
                            .orElseThrow(() -> new RuntimeException("Error: Role not found")));
                    case "tailorMaster" -> roles.add(roleRepository.findByRoleName(AppRole.TAILOR_MASTER)
                            .orElseThrow(() -> new RuntimeException("Error: Role not found")));
                    case "tailor" -> roles.add(roleRepository.findByRoleName(AppRole.TAILOR)
                            .orElseThrow(() -> new RuntimeException("Error: Role is not found")));
                    case "shopAssistant" -> roles.add(roleRepository.findByRoleName(AppRole.SHOP_ASSISTANT)
                            .orElseThrow(() -> new RuntimeException("Error: Role is not found")));
                    case "accountant" -> roles.add(roleRepository.findByRoleName(AppRole.ACCOUNTANT)
                            .orElseThrow(() -> new RuntimeException("Error: Role is not found")));
                    default -> roles.add(roleRepository.findByRoleName(AppRole.ROLE_USER)
                            .orElseThrow(() -> new RuntimeException("Error Role is not found")));
                }
            });
        }
        user.setRoles(roles);
        user.setSalary(signupRequest.getSalary() > 0 ? signupRequest.getSalary() : 0.0);
        if (signupRequest.getAddress() != null) {
            user.setAddress(signupRequest.getAddress());
        }

        if (signupRequest.getAssignedShops() != null) {
            Shop shop = shopRepository.findById(signupRequest.getAssignedShops())
                    .orElseThrow(() -> new APIException("Shop Data not found, hence user can't be created"));
            user.setAssignedShops(new ArrayList<>(List.of(shop)));
            User savedUser = userRepository.save(user);

            List<User> employees = shop.getEmployees() != null ? shop.getEmployees() : new ArrayList<>();
            employees.add(savedUser);
            shop.setEmployees(employees);

            List<String> employeeIds = shop.getEmployeeIds() != null
                    ? shop.getEmployeeIds() : new ArrayList<>();
            employeeIds.add(savedUser.getUserId());
            shop.setEmployeeIds(employeeIds);
            shopRepository.save(shop);
        } else {
            userRepository.save(user);
        }

        return ResponseEntity.ok(new MessageResponse("User created successfully"));
    }

    @PostMapping("/refresh-token")
    @Operation(
            summary = "Refresh an access token",
            description = "Validates the bearer token and returns a renewed token in the Authorization response header.",
            parameters = @Parameter(name = "Authorization", in = ParameterIn.HEADER, required = true,
                    description = "Current bearer token", example = "Bearer <JWT>"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Token refreshed",
                            headers = @io.swagger.v3.oas.annotations.headers.Header(
                                    name = "Authorization", description = "New bearer token",
                                    schema = @Schema(type = "string", example = "Bearer <JWT>")),
                            content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                                    examples = @ExampleObject(value = "Token refreshed successfully"))),
                    @ApiResponse(responseCode = "400", description = "No token provided",
                            content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                                    examples = @ExampleObject(value = "No token provided"))),
                    @ApiResponse(responseCode = "401", description = "Token expired, invalid, or revoked")
            })
    public ResponseEntity<?> refreshToken(HttpServletRequest request) {
        String oldToken = jwtUtils.getJwtFromHeader(request);
        if (oldToken == null) {
            return ResponseEntity.badRequest().body("No token provided");
        }

        try {
            String username = jwtUtils.getUserNameFromJwtToken(oldToken);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            String newToken = jwtUtils.generateTokenFromUsername(userDetails);

            return ResponseEntity.ok()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + newToken)
                    .body("Token refreshed successfully");
        } catch (ExpiredJwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token is expired");
        }
    }

    private Integer otpGenerator() {
        Random random = new Random();
        return random.nextInt(100_000, 999_999);
    }

    @PostMapping("/request-reset/{email}")
    @Operation(
            summary = "Request a password reset OTP",
            description = "Emails a one-time password reset code to the account. The newest request invalidates older codes.",
            parameters = @Parameter(name = "email", in = ParameterIn.PATH, required = true,
                    description = "Account email address", example = "asha@example.com"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "OTP email sent",
                            content = @Content(schema = @Schema(implementation = MessageResponse.class),
                                    examples = @ExampleObject(value = "{\"message\":\"Password reset Email sent successfully\"}"))),
                    @ApiResponse(responseCode = "400", description = "Email is not associated with an account"),
                    @ApiResponse(responseCode = "500", description = "Email delivery or reset request storage failed")
            })
    public ResponseEntity<?> requestPasswordReset(@PathVariable String email) {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new APIException("Please enter correct mail id");
        }

        Integer otp = otpGenerator();
        ForgetPassword resetRequest = ForgetPassword.builder()
                .otp(otp)
                .expirationTime(new Date(System.currentTimeMillis() + (10 * 60 * 1000)))
                .user(user.getUserId())
                .build();

        try {
            // Only the newest OTP remains valid for this account.
            forgetPasswordRepository.deleteByUser(user.getUserId());
            resetRequest = forgetPasswordRepository.save(resetRequest);
            brevoEmailService.sendEmail(email, user.getName(), otp);
            return ResponseEntity.ok(new MessageResponse("Password reset Email sent successfully"));
        } catch (Exception e) {
            if (resetRequest.getFpid() != null) {
                try {
                    forgetPasswordRepository.deleteById(resetRequest.getFpid());
                } catch (Exception cleanupException) {
                    logger.error("Could not remove unsent password reset OTP", cleanupException);
                }
            }
            logger.error("Failed to send password reset email", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to send password reset email");
        }
    }

    @PostMapping("/reset")
    @Operation(
            summary = "Reset the account password",
            description = "Checks the email and unexpired OTP, updates the password, and invalidates existing access tokens.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = ChangePassword.class),
                            examples = @ExampleObject(value = "{\"email\":\"asha@example.com\",\"otp\":123456,\"password\":\"NewStrongPassword123!\",\"repeatPassword\":\"NewStrongPassword123!\"}"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Password changed",
                            content = @Content(schema = @Schema(implementation = MessageResponse.class),
                                    examples = @ExampleObject(value = "{\"message\":\"Password has been changed successfully\"}"))),
                    @ApiResponse(responseCode = "400", description = "Unknown account, invalid/expired OTP, or passwords do not match",
                            content = @Content(schema = @Schema(implementation = APIResponse.class))),
                    @ApiResponse(responseCode = "500", description = "Password update failed")
            })
    public ResponseEntity<?> resetPassword(@RequestBody ChangePassword changePassword) {
        if (!Objects.equals(changePassword.getPassword(), changePassword.getRepeatPassword())) {
            throw new APIException("Password & Repeat Password must be same, Please try again");
        }

        User user = userRepository.findByEmail(changePassword.getEmail());
        if (user == null) {
            throw new APIException("Please enter correct mail id");
        }

        ForgetPassword resetRequest = forgetPasswordRepository
                .findByOtpAndUser(changePassword.getOtp(), user.getUserId())
                .orElseThrow(() -> new APIException("Invalid or already used password reset OTP"));

        if (resetRequest.getExpirationTime().before(Date.from(Instant.now()))) {
            forgetPasswordRepository.deleteById(resetRequest.getFpid());
            throw new APIException("OTP is expired, please request a new password reset");
        }

        var credentialUpdate = mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(user.getUserId())),
                new Update()
                        .set("password", passwordEncoder.encode(changePassword.getPassword()))
                        .inc("tokenVersion", 1),
                User.class);
        if (credentialUpdate.getMatchedCount() != 1) {
            throw new APIException("Could not update password; please try again");
        }
        forgetPasswordRepository.deleteByUser(user.getUserId());

        return ResponseEntity.ok(new MessageResponse("Password has been changed successfully"));
    }

    private User getUserWithCache(String email) {
        try {
            return userRepository.findByEmail(email);
        } catch (Exception e) {
            System.out.print("Cache lookup failed, falling back to DB");
            return userRepository.findByEmailNoCache(email);
        }
    }

    private void asyncUpdateLastLogin(User user) {
        try {
            // Use simpler update operation
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(user.getUserId())),
                    new Update()
                            .set("lastLoginDate", DateTimeUtils.getNowInIST())
                            .set("lastOnlineDate", DateTimeUtils.getNowInIST()),
                    User.class
            );
        } catch (Exception e) {
            System.out.println("Async last login update failed" + e);
        }
    }

//    ==============================================================================================================

    @PostMapping("/signin")
    @Operation(
            summary = "Sign in",
            description = "Authenticates with email and password. Returns user, role, and shop information; the JWT is in the Authorization response header.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = LoginRequest.class),
                            examples = @ExampleObject(value = "{\"email\":\"asha@example.com\",\"password\":\"StrongPassword123!\"}"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Sign-in successful",
                            headers = @io.swagger.v3.oas.annotations.headers.Header(
                                    name = "Authorization", description = "Bearer access token",
                                    schema = @Schema(type = "string", example = "Bearer <JWT>")),
                            content = @Content(schema = @Schema(implementation = UserInfoResponse.class),
                                    examples = @ExampleObject(value = "{\"id\":\"user-id\",\"username\":\"Asha\",\"message\":\"Login Successful\",\"shopName\":\"Main Shop\",\"shopId\":\"shop-id\",\"roles\":[\"TAILOR\"]}"))),
                    @ApiResponse(responseCode = "400", description = "Email is required",
                            content = @Content(schema = @Schema(type = "object", example = "{\"message\":\"Email is required\",\"status\":false}"))),
                    @ApiResponse(responseCode = "401", description = "Invalid password",
                            content = @Content(schema = @Schema(type = "object", example = "{\"message\":\"Invalid password\",\"status\":false}"))),
                    @ApiResponse(responseCode = "404", description = "Email not found",
                            content = @Content(schema = @Schema(type = "object", example = "{\"message\":\"Email not found\",\"status\":false}")))
            })
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {
        // Start timing
        long startTime = System.currentTimeMillis();
        logger.info("1. Start - {}", startTime);

        long step1 = System.currentTimeMillis();
        if (loginRequest.getEmail() == null || loginRequest.getEmail().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email is required", "status", false));
        }
        String normalizedEmail = loginRequest.getEmail().trim().toLowerCase(Locale.ROOT);
        logger.info("2. After normalize - {}ms", step1 - startTime);

        UserWithShopProjection dbUser = userRepository.findUserWithShop(normalizedEmail);
        long step2 = System.currentTimeMillis();
        logger.info("3. After DB query - {}ms", step2 - step1);

        if (dbUser == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Email not found", "status", false));
        }

        // 2. Fast-fail password check first
        if (!passwordEncoder.matches(loginRequest.getPassword(), dbUser.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid password", "status", false));
        }
        long step3 = System.currentTimeMillis();
        logger.info("4. After password check - {}ms", step3 - step2);

        List<GrantedAuthority> authorities = dbUser.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getRoleName().name()))
                .collect(Collectors.toList());

        // 5. Build response
        UserInfoResponse response = new UserInfoResponse(
                dbUser.getUserId(),
                authorities.stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toList()),
                "Login Successful",
                dbUser.getShopName(),
                dbUser.getShopId(),
                dbUser.getName()
        );

        long step4 = System.currentTimeMillis();
        logger.info("5. After building response - {}ms", step4 - step3);

        // 4. Simplify response building
        String jwtToken = jwtUtils.generateTokenFromUsername(new UserDetailsImpl(
                dbUser.getUserId(),
                dbUser.getEmail(),
                dbUser.getPassword(),
                authorities,
                dbUser.getTokenVersion()
        ));
        long step5 = System.currentTimeMillis();
        logger.info("6. After token generation - {}ms", step5 - step4);

        // Async update if really needed
//        CompletableFuture.runAsync(() -> asyncUpdateLastLogin(dbUser));

        logger.info("TOTAL TIME - {}ms", System.currentTimeMillis() - startTime);

        return ResponseEntity.ok()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                .body(response);
    }

}

