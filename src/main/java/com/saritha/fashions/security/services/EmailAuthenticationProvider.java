package com.saritha.fashions.security.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

//@Component
//public class EmailAuthenticationProvider extends DaoAuthenticationProvider {
//
//    @Autowired
//    private PasswordEncoder passwordEncoder;
//
//    @Autowired
//    public void setUserDetailsService(UserDetailsService userDetailsService) {
//        super.setUserDetailsService(userDetailsService);
//    }
//
//    @Override
//    public Authentication authenticate(Authentication auth) throws AuthenticationException {
//        String email = auth.getName();
//        String password = auth.getCredentials().toString();
//
//        UserDetails user = this.getUserDetailsService().loadUserByUsername(email);
//
//        if (!passwordEncoder.matches(password, user.getPassword())) {
//            throw new BadCredentialsException("Invalid credentials");
//        }
//
//        return new UsernamePasswordAuthenticationToken(
//                user.getUsername(),
//                password,
//                user.getAuthorities()
//        );
//    }
//}

@Component
public class EmailAuthenticationProvider extends DaoAuthenticationProvider {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    public void setUserDetailsService(UserDetailsService userDetailsService) {
        super.setUserDetailsService(userDetailsService);
    }

    @Override
    public Authentication authenticate(Authentication auth) throws AuthenticationException {
        String email = auth.getName();
        if (email == null || email.isEmpty()) {
            throw new BadCredentialsException("Email cannot be empty");
        }

        UserDetails user = this.getUserDetailsService().loadUserByUsername(email);

        if (!passwordEncoder.matches(auth.getCredentials().toString(), user.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        return new UsernamePasswordAuthenticationToken(
                user.getUsername(), // email
                null,
                user.getAuthorities()
        );
    }
}
