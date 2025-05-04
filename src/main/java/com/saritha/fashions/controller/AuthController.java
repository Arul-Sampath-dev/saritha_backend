package com.saritha.fashions.controller;

import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.*;
import com.saritha.fashions.repository.RoleRepository;
import com.saritha.fashions.repository.ShopRepository;
import com.saritha.fashions.repository.UserRepository;
import com.saritha.fashions.security.jwt.JwtUtils;
import com.saritha.fashions.security.request.LoginRequest;
import com.saritha.fashions.security.request.SignupRequest;
import com.saritha.fashions.security.response.MessageResponse;
import com.saritha.fashions.security.response.UserInfoResponse;
import com.saritha.fashions.security.services.UserDetailsImpl;
import com.saritha.fashions.util.AuthUtil;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/api/auth")
public class AuthController {

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
    private ShopRepository shopRepository;

    @PostMapping("/signup")
    @Transactional
    @PreAuthorize("hasAuthority('OWNER')")
    public ResponseEntity<?> registerUser(@RequestBody SignupRequest signupRequest) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUserRole = authentication.getAuthorities().iterator().next().getAuthority();

        if (!currentUserRole.equals("OWNER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only Shop Owners can register new users.");
        }

        if (userRepository.existsByEmail(signupRequest.getEmail().toLowerCase())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Email is already in use!"));
        }

//        Create new user's account
        String username = String.valueOf(UUID.randomUUID());
        String phone = signupRequest.getPhone() == null ? "0000000000" : signupRequest.getPhone();
        User user = new User(username,
                signupRequest.getEmail().toLowerCase(),
                encoder.encode(signupRequest.getPassword() != null ? signupRequest.getPassword() : "Saritha@123"), signupRequest.getName(), phone
        );

        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyy");
        Date today = new Date();
        String todayDate = sdf.format(today);
        user.setAccountCreationDate(todayDate);

//        Set<String> roleFromFrontend = Collections.singleton(signupRequest.getRole());
//        Set<String> roleFromFrontend = signupRequest.getRole() != null ?
//                Collections.singleton(signupRequest.getRole()) : new HashSet<>();

        Set<String> roleFromFrontend = signupRequest.getRoles();
        Set<String> strRoles = signupRequest.getRoles();
        Set<Role> roles = new HashSet<>();


        if (roleFromFrontend == null) {
            Role userRole = roleRepository.findByRoleName(AppRole.ROLE_USER)
                    .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
            roles.add(userRole);
        } else {
            roleFromFrontend.forEach(role -> {
                switch (role) {
                    case "owner":
                        Role ownerRole = roleRepository.findByRoleName(AppRole.OWNER)
                                .orElseThrow(() -> new RuntimeException("Error: Role not found"));
                        roles.add(ownerRole);
                        break;
                    case "shopOwner":
                        Role shopOwnerRole = roleRepository.findByRoleName(AppRole.SHOP_OWNER)
                                .orElseThrow(() -> new RuntimeException("Error: Role not found"));
                        roles.add(shopOwnerRole);

                        break;
                    case "tailorMaster":
                        Role tailorMasterRole = roleRepository.findByRoleName(AppRole.TAILOR_MASTER)
                                .orElseThrow(() -> new RuntimeException("Error: Role not found"));
                        roles.add(tailorMasterRole);

                        break;
                    case "tailor":
                        Role tailorRole = roleRepository.findByRoleName(AppRole.TAILOR)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found"));
                        roles.add(tailorRole);

                        break;
                    case "shopAssistant":
                        Role shopAssistantRole = roleRepository.findByRoleName(AppRole.SHOP_ASSISTANT)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found"));
                        roles.add(shopAssistantRole);

                        break;

                    case "accountant":
                        Role accountantRole = roleRepository.findByRoleName(AppRole.ACCOUNTANT)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found"));
                        roles.add(accountantRole);

                        break;
                    default:
                        Role userRole = roleRepository.findByRoleName(AppRole.ROLE_USER)
                                .orElseThrow(() -> new RuntimeException("Error Role is not found"));
                        roles.add(userRole);
                        break;
                }
            });
        }

        user.setRoles(roles);
        if (signupRequest.getSalary() > 0 ) {
            user.setSalary(signupRequest.getSalary());
        }else {
            user.setSalary(0.0);
        }

        if (signupRequest.getAddress() != null) {
            user.setAddress(signupRequest.getAddress());
        }

        if (signupRequest.getAssignedShops() != null) {
            String assignedShops = signupRequest.getAssignedShops();
            Optional<Shop> shop = shopRepository.findById(assignedShops);
            if (shop.isPresent()) {
                List<Shop> allShopsAssigned = new ArrayList<>();
                allShopsAssigned.add(shop.get());
                user.setAssignedShops(allShopsAssigned);
                User userSaved = userRepository.save(user);

                Shop updatedShop = shop.get();
                List<User> employees = updatedShop.getEmployees() != null ? updatedShop.getEmployees() : new ArrayList<>();
                employees.add(userSaved);
                updatedShop.setEmployees(employees);

                shopRepository.save(updatedShop);

            }else {
                throw new APIException("Shop Data not found, hence user can't be created");
            }
        } else {
            User userSaved = userRepository.save(user);
        }

        // add user to that shop's employee list
//        if (shop.isPresent()) {
//            Shop updatedShop = shop.get();
//            List<User> employees = updatedShop.getEmployees() != null ? updatedShop.getEmployees() : new ArrayList<>();
//            employees.add(userSaved);
//            updatedShop.setEmployees(employees);
//
//            shopRepository.save(updatedShop);
//        }

        return ResponseEntity.ok(new MessageResponse("User created successfully"));
    }

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {
        Authentication authentication;

//        try {
//            authentication = authenticationManager
//                    .authenticate(new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));
//        }catch (AuthenticationException exception) {
//            Map<String, Object> map = new HashMap<>();
//            map.put("message","Bad credentials");
//            map.put("status", false);
//
//            return new ResponseEntity<Object>(map, HttpStatus.NOT_FOUND);
//        }

        User dbUser = userRepository.findByEmail(loginRequest.getEmail().toLowerCase());

        if(dbUser == null) {
            Map<String, Object> map = new HashMap<>();
            map.put("message", "Please Check Your Email Id");
            map.put("status", false);

            return new ResponseEntity<Object>(map, HttpStatus.NOT_FOUND);
//            return ResponseEntity.badRequest().body(new MessageResponse("Please Check Your Email Id"));
        }

        try {
            authentication = authenticationManager
                    .authenticate(new UsernamePasswordAuthenticationToken(dbUser.getUsername(), loginRequest.getPassword()));
        } catch (AuthenticationException exception) {
            Map<String, Object> map = new HashMap<>();
            map.put("message", "Please Enter Correct Password");
            map.put("status", false);

            return new ResponseEntity<Object>(map, HttpStatus.NOT_FOUND);
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        String jwtToken = jwtUtils.generateTokenFromUsername(userDetails);

        List<String> roles = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());


        UserInfoResponse response = new UserInfoResponse(userDetails.getId(), roles, "Login Successful",
                dbUser.getAssignedShops() != null ? dbUser.getAssignedShops().get(0).getShopName() : "No Shop Assigned",
                dbUser.getName());
//        UserInfoResponse response = new UserInfoResponse(userDetails.getId(), roles, "Login Successful");

//        Setting up last login date
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
        Date now = new Date();
        String todayDate = sdf.format(now);
        dbUser.setLastLoginDate(todayDate);
        userRepository.save(dbUser);

        return ResponseEntity.ok().header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken).body(response);

//        Below code is for JWT Cookies based authentication

//        ResponseCookie jwtCookie = jwtUtils.generateJwtCookie(userDetails);
//        UserInfoResponse response = new UserInfoResponse(userDetails.getId(),userDetails.getUsername(), roles,"Login Successful");
//        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, jwtCookie.toString()).body(response);
    }

    @PostMapping("/refresh-token")
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

}

