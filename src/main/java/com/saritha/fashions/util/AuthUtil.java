package com.saritha.fashions.util;

import com.saritha.fashions.model.User;
import com.saritha.fashions.repository.UserRepository;
import com.saritha.fashions.service.InvoiceServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class AuthUtil {

    private static final Logger logger = LoggerFactory.getLogger(AuthUtil.class);

    @Autowired
    UserRepository userRepository;

    public String loggedInEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            logger.error("No authentication found in SecurityContext");
            throw new UsernameNotFoundException("No authenticated user");
        }

        logger.debug("Getting logged in email for: {}", authentication.getName());

        User user = userRepository.findByEmail(authentication.getName());
        if (user == null) {
            throw new UsernameNotFoundException("User Not Found with email: " + authentication.getName());
        }
        return user.getEmail();
    }

    public String loggedInUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // CHANGE: Use findByEmail
        User user = userRepository.findByEmail(authentication.getName());
        if (user == null) {
            throw new UsernameNotFoundException("User Not Found with email: " + authentication.getName());
        }
        return user.getUserId();
    }

    public User loggedInUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            logger.error("No authentication found in SecurityContext");
            throw new UsernameNotFoundException("No authenticated user");
        }

        logger.debug("Getting logged in user for: {}", authentication.getName());

        User user = userRepository.findByEmail(authentication.getName());
        if (user == null) {
            throw new UsernameNotFoundException("User Not Found with email: " + authentication.getName());
        }
        return user;
    }
}