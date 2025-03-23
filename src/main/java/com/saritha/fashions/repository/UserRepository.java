package com.saritha.fashions.repository;

import com.saritha.fashions.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String > {
    User findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String user1);

    boolean existsByEmail(String email);

    Optional<User> findByUserId(String userId);

    List<User> findByAccountCreationDate(String yesterdayString);
}
