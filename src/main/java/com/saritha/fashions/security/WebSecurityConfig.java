package com.saritha.fashions.security;

import com.saritha.fashions.model.AppRole;
import com.saritha.fashions.model.Role;
import com.saritha.fashions.repository.RoleRepository;
import com.saritha.fashions.security.jwt.AuthEntryPointJwt;
import com.saritha.fashions.security.jwt.AuthTokenFilter;
import com.saritha.fashions.security.services.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
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
        return new BCryptPasswordEncoder();
    }


    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .exceptionHandling(exception -> exception.authenticationEntryPoint(unauthorizedHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth ->
                        auth.requestMatchers("/v1/api/auth/**").permitAll()
                                .requestMatchers("/v1/api/payments/handle-payment-callback").permitAll()
                                .requestMatchers("/v1/api/user/support-ticket/contact-us").permitAll()
                                .requestMatchers("/v1/api/forgotPassword/**").permitAll()
                                .requestMatchers("/v1/api/public/**").permitAll()

                                .requestMatchers("/v1/api/owner/**").hasAnyAuthority("OWNER")

                                .requestMatchers("/v1/api/orders/**").hasAnyAuthority("OWNER","SHOP_OWNER", "TAILOR_MASTER", "SHOP_ASSISTANT")
                                .requestMatchers("/v1/api/invoices/**").hasAnyAuthority("OWNER","SHOP_OWNER")
                                .requestMatchers("/v1/api/receipt/**").hasAnyAuthority("OWNER","SHOP_OWNER")
                                .requestMatchers("/v1/api/expenses/**").hasAnyAuthority("OWNER","SHOP_OWNER")
                                .requestMatchers("/v1/api/shops/**").hasAnyAuthority("OWNER","SHOP_OWNER")
                                .requestMatchers("/v1/api/tailor-master/**").hasAnyAuthority("OWNER","SHOP_OWNER","TAILOR_MASTER")
                                .requestMatchers("/v1/api/tailor/**").hasAnyAuthority("OWNER","SHOP_OWNER","TAILOR")
                                .requestMatchers("/v1/api/shop-assistant/**").hasAnyAuthority("OWNER","SHOP_ASSISTANT")

                                .requestMatchers("/v3/api-docs/**").permitAll()
                                .requestMatchers("/swagger-ui/**").permitAll()
                                .requestMatchers("/api/test/**").permitAll()
                                .requestMatchers("/images/**").permitAll()
                                .anyRequest().authenticated())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()));

        http.authenticationProvider(authenticationProvider());

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
        configuration.setAllowedOrigins(List.of("http://localhost:5173/","https://sarithasdesignerstudio.com","https://testing.sarithasdesignerstudio.com", "https://*.razorpay.com","1.179.112.0/20"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
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