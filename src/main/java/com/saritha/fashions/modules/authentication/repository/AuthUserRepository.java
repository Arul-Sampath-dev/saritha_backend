package com.saritha.fashions.modules.authentication.repository;

import com.saritha.fashions.modules.authentication.model.AuthUser;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Mongo repository for user accounts ("users" collection).
 *
 * Named differently from com.saritha.fashions.repository.UserRepository so the two Spring
 * beans do not clash. Emails are stored lower-case; callers must pass lower-case emails.
 */
@Repository
public interface AuthUserRepository extends MongoRepository<AuthUser, String>, AuthUserRepositoryCustom {

    Optional<AuthUser> findByEmail(String email);

    boolean existsByEmail(String email);
}
