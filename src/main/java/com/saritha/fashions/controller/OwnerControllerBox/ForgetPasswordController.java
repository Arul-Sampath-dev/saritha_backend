package com.saritha.fashions.controller.OwnerControllerBox;

import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.ForgetPassword;
import com.saritha.fashions.model.User;
import com.saritha.fashions.payload.MailBody;
import com.saritha.fashions.repository.ForgetPasswordRepository;
import com.saritha.fashions.repository.UserRepository;
import com.saritha.fashions.security.response.MessageResponse;
import com.saritha.fashions.service.BrevoEmailServiceForForgotPassword;
import com.saritha.fashions.service.EmailService;
import com.saritha.fashions.util.ChangePassword;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.Random;

@RestController
@RequestMapping("/v1/api/forgotPassword")
public class ForgetPasswordController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ForgetPasswordRepository forgetPasswordRepository;

    @Autowired
    private BrevoEmailServiceForForgotPassword brevoEmailService;

    private final EmailService emailService;

    private final PasswordEncoder passwordEncoder;



    public ForgetPasswordController(EmailService emailService, PasswordEncoder passwordEncoder) {
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/verifyMail/{email}")
    public ResponseEntity<?> verifyEmail(@PathVariable String email) {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new APIException("Please enter correct mail id");
        }

        Integer otp = otpGenerator();
        MailBody mailBody = MailBody.builder()
                .to(email)
                .text("This is the OTP for your forget password request: " + otp)
                .subject("OTP for Forget Password Request")
                .build();

        ForgetPassword fp = ForgetPassword.builder()
                .otp(otp)
                .expirationTime(new Date(System.currentTimeMillis() + (10 * 60 * 1000)))
                .user(user.getUserId())
                .build();
        MessageResponse messageResponse = new MessageResponse();
        try {
             brevoEmailService.sendEmail(email, user.getName(), otp);
            forgetPasswordRepository.save(fp);
            messageResponse.setMessage("Password reset Email sent successfully");
            return ResponseEntity.ok(messageResponse);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to send email: " + e.getMessage());
        }

    }

//    @PostMapping("/verifyOtp/{otp}/{email}")
//    public ResponseEntity<?> verifyOtp(@PathVariable Integer otp, @PathVariable String email) {
//        User user = userRepository.findByEmail(email);
//        if (user == null) {
//            throw new APIException("Please enter correct mail id");
//        }
//        ForgetPassword fp = forgetPasswordRepository.findByOtpAndUser(otp, user.getUserId()).orElseThrow(() -> new APIException("Invalid OTP for email" + email));
//
//        if(fp.getExpirationTime().before(Date.from(Instant.now()))) {
//            forgetPasswordRepository.deleteById(fp.getFpid());
//            throw new APIException("OTP is Expired");
//        }
//
//        MessageResponse messageResponse = new MessageResponse();
//        messageResponse.setMessage("OTP Verified Successfully");
//
//        return ResponseEntity.ok(messageResponse);
//    }

    @PostMapping("/verifyOtp")
    public ResponseEntity<?> verifyOtp(@RequestBody ChangePassword changePassword) {
        User user = userRepository.findByEmail(changePassword.getEmail());
        if (user == null) {
            throw new APIException("Please enter correct mail id");
        }
        ForgetPassword fp = forgetPasswordRepository.findByOtpAndUser(changePassword.getOtp(), user.getUserId()).orElseThrow(() -> new APIException("Invalid OTP for email " + changePassword.getEmail()));

        if(fp.getExpirationTime().before(Date.from(Instant.now()))) {
            forgetPasswordRepository.deleteById(fp.getFpid());
            throw new APIException("OTP is Expired, Please Resend OTP & Try again");
        }

        MessageResponse messageResponse = new MessageResponse();
        messageResponse.setMessage("OTP Verified Successfully");

        return ResponseEntity.ok(messageResponse);
    }

//    @PostMapping("/changePassword/{email}")
//    public ResponseEntity<?> changePasswordHandler(@RequestBody ChangePassword changePassword, @PathVariable String email) {
//
//        if(!Objects.equals(changePassword.password(), changePassword.repeatPassword())) {
//            throw new APIException("Please enter the password again");
//        }
//
//        String encodedPassword = passwordEncoder.encode(changePassword.password());
//        User user = userRepository.findByEmail(email);
//        user.setPassword(encodedPassword);
//        userRepository.save(user);
//
//        MessageResponse messageResponse = new MessageResponse();
//        messageResponse.setMessage("Password has changes Successfully");
//
//        return ResponseEntity.ok(messageResponse);
//    }

    @PostMapping("/changePassword")
    public ResponseEntity<?> changePasswordHandler(@RequestBody ChangePassword changePassword) {

        if(!Objects.equals(changePassword.getPassword(), changePassword.getRepeatPassword())) {
            throw new APIException("Password & Repeat Password must be same, Please try again");
        }

        String encodedPassword = passwordEncoder.encode(changePassword.getPassword());
        User user = userRepository.findByEmail(changePassword.getEmail());

        ForgetPassword fp = forgetPasswordRepository.findByOtpAndUser(changePassword.getOtp(), user.getUserId()).orElseThrow(() -> new APIException("Password reset is already attempted for" + changePassword.getEmail() + ", Please try to do forgot password again"));
        if(fp.getExpirationTime().before(Date.from(Instant.now()))) {
            forgetPasswordRepository.deleteById(fp.getFpid());
            throw new APIException("OTP is Expired, Please do forgot password again");
        }
        user.setPassword(encodedPassword);
        userRepository.save(user);
//        forgetPasswordRepository.deleteById(fp.getFpid());
        forgetPasswordRepository.deleteByUser(user.getUserId());

        MessageResponse messageResponse = new MessageResponse();
        messageResponse.setMessage("Password has been changed successfully");

        return ResponseEntity.ok(messageResponse);
    }

    private Integer otpGenerator() {
        Random random = new Random();
        return random.nextInt(100_000, 999_999);
    }
}
