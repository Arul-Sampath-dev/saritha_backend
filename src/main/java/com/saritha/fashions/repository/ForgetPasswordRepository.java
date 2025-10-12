package com.saritha.fashions.repository;

import com.saritha.fashions.model.ForgetPassword;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;

public interface ForgetPasswordRepository extends MongoRepository<ForgetPassword, String> {

    @Query("{ 'otp': ?0, 'user': ?1 }")
    Optional<ForgetPassword> findByOtpAndUser(Integer otp, String user);

    void deleteByUser(String userId);
}
