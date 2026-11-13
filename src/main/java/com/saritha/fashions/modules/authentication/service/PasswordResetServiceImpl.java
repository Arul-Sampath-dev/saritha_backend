package com.saritha.fashions.modules.authentication.service;

import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.modules.authentication.exception.TooManyAttemptsException;
import com.saritha.fashions.modules.authentication.model.AuthUser;
import com.saritha.fashions.modules.authentication.model.PasswordResetOtp;
import com.saritha.fashions.modules.authentication.payload.ResetPasswordRequest;
import com.saritha.fashions.modules.authentication.repository.AuthUserRepository;
import com.saritha.fashions.modules.authentication.repository.PasswordResetOtpRepository;
import com.saritha.fashions.modules.core.async.NotificationDispatcher;
import com.saritha.fashions.modules.core.mailing.BrevoMailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import static com.saritha.fashions.modules.authentication.service.AuthenticationServiceImpl.normaliseEmail;

/**
 * Password reset, ported from AuthController.requestPasswordReset / resetPassword.
 *
 * Fixes vs the old flow:
 * - the code comes from SecureRandom (old: java.util.Random, which is predictable);
 * - only a BCrypt hash of the code is stored (old: the code in plain text);
 * - at most 5 wrong guesses, then the code is locked until a new one may be requested
 *   (old: unlimited guesses, so the 900,000 possible codes could be brute-forced within the
 *   10 minutes); with the 60 s cooldown that caps guessing at 5 per minute per account;
 * - the same response whether or not the email has an account, and the email is sent in
 *   the background so timing does not reveal it either (old: "Please enter correct mail id");
 * - a 60-second cooldown between codes, so the endpoint cannot be used to spam inboxes;
 * - the code is consumed atomically, so it works only once even with parallel requests;
 * - email matching is case-insensitive in both steps (old reset used the raw email).
 */
@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetServiceImpl.class);

    static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    static final String INVALID_CODE = "Invalid or expired code. Please check the code or request a new one.";
    static final String LOCKED = "Too many wrong codes. Please wait a minute and request a new code.";

    private final SecureRandom secureRandom = new SecureRandom();

    private final AuthUserRepository userRepository;
    private final PasswordResetOtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final BrevoMailService mailService;
    private final NotificationDispatcher dispatcher;
    private final long resetTemplateId;

    public PasswordResetServiceImpl(AuthUserRepository userRepository,
                                    PasswordResetOtpRepository otpRepository,
                                    PasswordEncoder passwordEncoder,
                                    BrevoMailService mailService,
                                    NotificationDispatcher dispatcher,
                                    @Value("${brevo.template.password-reset:1}") long resetTemplateId) {
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.dispatcher = dispatcher;
        this.resetTemplateId = resetTemplateId;
    }

    @Override
    public void requestResetCode(String rawEmail) {
        String email = normaliseEmail(rawEmail);
        Optional<AuthUser> found = userRepository.findByEmail(email);
        if (found.isEmpty()) {
            // Same response AND roughly the same time as for a real account (which hashes a code).
            passwordEncoder.encode(String.format("%06d", secureRandom.nextInt(1_000_000)));
            log.info("Password reset requested for unknown email {}", email);
            return;
        }
        AuthUser user = found.get();

        Optional<PasswordResetOtp> existing = otpRepository.findByUserId(user.getUserId());
        if (existing.isPresent() && existing.get().getCreatedAt() != null
                && existing.get().getCreatedAt().toInstant().isAfter(Instant.now().minus(RESEND_COOLDOWN))) {
            log.info("Password reset for user {} ignored: cooldown", user.getUserId());
            return;
        }

        String code = String.format("%06d", 100_000 + secureRandom.nextInt(900_000));
        Instant now = Instant.now();

        otpRepository.deleteByUserId(user.getUserId()); // only the newest code is valid
        PasswordResetOtp otp = otpRepository.save(PasswordResetOtp.builder()
                .userId(user.getUserId())
                .otpHash(passwordEncoder.encode(code))
                .failedAttempts(0)
                .createdAt(Date.from(now))
                .expiresAt(Date.from(now.plus(CODE_LIFETIME)))
                .build());

        // Sent in the background: the response time must not depend on whether the account exists.
        dispatcher.dispatch("password reset email for user " + user.getUserId(), () -> {
            try {
                mailService.sendTemplate(email, user.getName(), resetTemplateId, Map.of(
                        "USER_NAME", user.getName() == null ? "" : user.getName(),
                        "OTP_CODE", code,
                        "EMAIL", email));
            } catch (RuntimeException exception) {
                // An undeliverable code is useless; remove it so a new one can be requested at once.
                otpRepository.consume(otp.getId());
                throw exception;
            }
        });
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        String email = normaliseEmail(request.getEmail());
        AuthUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new APIException(INVALID_CODE));

        PasswordResetOtp otp = otpRepository.findByUserId(user.getUserId())
                .orElseThrow(() -> new APIException(INVALID_CODE));

        if (otp.getExpiresAt() == null || otp.getExpiresAt().toInstant().isBefore(Instant.now())) {
            otpRepository.consume(otp.getId());
            throw new APIException(INVALID_CODE);
        }
        // A locked code is KEPT (not deleted) until it expires or is replaced: its createdAt
        // drives the 60 s cooldown, so after a lock-out a new code (and 5 new guesses) can only
        // be had once a minute. Deleting it would allow unlimited guess/re-request cycles.
        if (otp.getFailedAttempts() >= PasswordResetOtp.MAX_ATTEMPTS) {
            throw new TooManyAttemptsException(LOCKED);
        }

        if (!passwordEncoder.matches(request.getCode(), otp.getOtpHash())) {
            int attempts = otpRepository.registerFailedAttempt(otp.getId());
            if (attempts >= PasswordResetOtp.MAX_ATTEMPTS) {
                log.warn("Password reset code for user {} locked after {} wrong attempts", user.getUserId(), attempts);
                throw new TooManyAttemptsException(LOCKED);
            }
            throw new APIException(INVALID_CODE);
        }

        // Single use: only the request that removes the code may change the password.
        if (!otpRepository.consume(otp.getId())) {
            throw new APIException(INVALID_CODE);
        }

        if (!userRepository.updatePasswordAndRevokeTokens(user.getUserId(), passwordEncoder.encode(request.getNewPassword()))) {
            throw new APIException("Could not update the password, please try again");
        }
        log.info("Password reset for user {}; all tokens revoked", user.getUserId());
    }
}
