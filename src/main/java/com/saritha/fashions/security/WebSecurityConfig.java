package com.saritha.fashions.security;
import jakarta.servlet.DispatcherType;
import com.saritha.fashions.model.AppRole;
import com.saritha.fashions.model.Role;
import com.saritha.fashions.repository.RoleRepository;
import com.saritha.fashions.security.jwt.AuthEntryPointJwt;
import com.saritha.fashions.security.jwt.AuthTokenFilter;
import com.saritha.fashions.security.services.EmailAuthenticationProvider;
import com.saritha.fashions.security.services.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfig {

    @Value("${frontend.website.allowed}")
    private String allowedRequestFromFrontend;

    @Autowired
    UserDetailsServiceImpl userDetailsService;

    @Autowired
    private AuthEntryPointJwt unauthorizedHandler;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @Autowired
    @Lazy
    private EmailAuthenticationProvider emailAuthenticationProvider;

    @Bean
    public AuthTokenFilter authenticationJwtTokenFilter() {
        return new AuthTokenFilter();
    }


    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();

        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());

        return authProvider;
    }


    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }


    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)

                .exceptionHandling(exception -> exception.authenticationEntryPoint(unauthorizedHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                    // Allow error dispatches
                    .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()

                    // Swagger / OpenAPI endpoints
                    .requestMatchers(
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/v3/api-docs.yaml",
                        "/swagger-ui.html",
                        "/swagger-ui/**"
                    ).permitAll()

                    // Public application endpoints
                    .requestMatchers("/v1/api/payments/handle-payment-callback").permitAll()
                    .requestMatchers("/v1/api/user/support-ticket/contact-us").permitAll()
                    .requestMatchers("/v1/api/public/**").permitAll()

                    // Authentication: sign-in and password reset are public; creating users needs the
                    // platform OWNER; everything else (refresh, sign-out, me) needs a valid token.
                    .requestMatchers(HttpMethod.POST,
                            "/v1/api/auth/sign-in",
                            "/v1/api/auth/password-reset",
                            "/v1/api/auth/password-reset/code").permitAll()
                    .requestMatchers(HttpMethod.POST, "/v1/api/auth/users").hasAuthority("OWNER")
                    .requestMatchers("/v1/api/auth/**").authenticated()

                    // ---------------------------------------------------------------------------
                    // Module APIs (modules/*). URL rules reject wrong roles early; the services
                    // additionally check the user's shop, assignments and ownership.
                    // ORDER MATTERS: several modules share the /v1/api/shops and /v1/api/invoices
                    // prefixes, so the more specific sub-path rules must come first.
                    // ---------------------------------------------------------------------------

                    // Shops (/v1/api/shops/**): platform OWNER only, except petty cash and the shop's
                    // employees, which the shop's own SHOP_OWNER may also use (checked in the services).
                    .requestMatchers("/v1/api/shops/*/petty-cash").hasAnyAuthority("OWNER", "SHOP_OWNER")
                    .requestMatchers("/v1/api/shops/*/employees", "/v1/api/shops/*/employees/**")
                        .hasAnyAuthority("OWNER", "SHOP_OWNER")
                    .requestMatchers("/v1/api/shops", "/v1/api/shops/**").hasAuthority("OWNER")

                    // Employees: platform OWNER (all shops) or SHOP_OWNER (own shop).
                    .requestMatchers("/v1/api/employees/**").hasAnyAuthority("OWNER", "SHOP_OWNER")

                    // Production (items): the workflow roles; assignment is checked per item.
                    .requestMatchers("/v1/api/items/**", "/v1/api/invoices/*/items/**")
                        .hasAnyAuthority("SHOP_OWNER", "TAILOR_MASTER", "TAILOR", "SHOP_ASSISTANT")

                    // Payments / discount / receipt creation on an invoice: the shop owner.
                    .requestMatchers("/v1/api/invoices/*/account", "/v1/api/invoices/*/payments",
                            "/v1/api/invoices/*/discount", "/v1/api/invoices/*/receipt").hasAuthority("SHOP_OWNER")

                    // Invoices: read by the platform OWNER (any shop, with shopId) and the SHOP_OWNER
                    // (own shop); create / edit / delete are the shop owner's.
                    .requestMatchers(HttpMethod.GET, "/v1/api/invoices", "/v1/api/invoices/**")
                        .hasAnyAuthority("OWNER", "SHOP_OWNER")
                    .requestMatchers("/v1/api/invoices", "/v1/api/invoices/**").hasAuthority("SHOP_OWNER")

                    // Receipts: read by the platform OWNER (all shops) and the SHOP_OWNER (own shop).
                    .requestMatchers(HttpMethod.GET, "/v1/api/receipts", "/v1/api/receipts/**")
                        .hasAnyAuthority("OWNER", "SHOP_OWNER")
                    .requestMatchers("/v1/api/receipts", "/v1/api/receipts/**").denyAll()

                    // Payment receipts: read by the platform OWNER (all shops) and the SHOP_OWNER (own shop);
                    // they are created by POST /v1/api/invoices/{id}/payments.
                    .requestMatchers(HttpMethod.GET, "/v1/api/payment-receipts/**").hasAnyAuthority("OWNER", "SHOP_OWNER")
                    .requestMatchers("/v1/api/payment-receipts/**").denyAll()

                    // Expenses: OWNER (with shopId) and SHOP_OWNER can read; only the shop owner
                    // records, edits and deletes them.
                    .requestMatchers(HttpMethod.GET, "/v1/api/expenses", "/v1/api/expenses/**")
                        .hasAnyAuthority("OWNER", "SHOP_OWNER")
                    .requestMatchers("/v1/api/expenses", "/v1/api/expenses/**", "/v1/api/employee-expenses")
                        .hasAuthority("SHOP_OWNER")

                    // Reports: own activity for shop staff; the shops overview for the platform OWNER;
                    // everything else for the OWNER (any / all shops) and the SHOP_OWNER (own shop).
                    .requestMatchers(HttpMethod.GET, "/v1/api/reports/my-activity")
                        .hasAnyAuthority("SHOP_OWNER", "TAILOR_MASTER", "TAILOR", "SHOP_ASSISTANT")
                    .requestMatchers(HttpMethod.GET, "/v1/api/reports/shops/**").hasAuthority("OWNER")
                    .requestMatchers(HttpMethod.GET, "/v1/api/reports/**").hasAnyAuthority("OWNER", "SHOP_OWNER")
                    .requestMatchers("/v1/api/reports/**").denyAll()

                    // GET /v1/api/pdf-service/warmup falls under anyRequest().authenticated();
                    // /v1/api/public/** (customer downloads) is permitted above.

                    .requestMatchers("/api/test/**", "/images/**").permitAll()

                    .anyRequest().authenticated()
                )

                .cors(cors -> cors.configurationSource(corsConfigurationSource()));

//        http.authenticationProvider(authenticationProvider());
        http.authenticationProvider(emailAuthenticationProvider);

//        http.addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(authenticationJwtTokenFilter(), UsernamePasswordAuthenticationFilter.class);
        http.headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()));

        return http.build();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web -> web.ignoring().requestMatchers("/v2/api-docs",
                "/configuration/ui",
                "/swagger-resources/**",
                "/configuration/security",
                "/swagger-ui.html",
                "/api/payments/handle-payment-callback",
                "/webjars/**"));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://192.168.1.16:5173","http://localhost:5173/","http://localhost:5174/","https://sarithasdesignerstudio.com","https://testing.sarithasdesignerstudio.com", "https://*.razorpay.com","1.179.112.0/20"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setExposedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;

    }

//    @Bean
//    public CommandLineRunner initData(RoleRepository roleRepository) {
//        return args -> {
//            // Initialize all roles in the system
//            initializeRole(roleRepository, AppRole.SHOP_OWNER);
//            initializeRole(roleRepository, AppRole.TAILOR_MASTER);
//            initializeRole(roleRepository, AppRole.TAILOR);
//            initializeRole(roleRepository, AppRole.SHOP_ASSISTANT);
//            initializeRole(roleRepository, AppRole.ACCOUNTANT);
//            initializeRole(roleRepository, AppRole.OWNER);
//            initializeRole(roleRepository, AppRole.ROLE_USER);
//        };
//    }
//
////    Helper method to find or create roles
//    private void initializeRole(RoleRepository roleRepository, AppRole appRole) {
//        roleRepository.findByRoleName(appRole)
//                .orElseGet(() -> roleRepository.save(new Role(appRole)));
//    }

}
