package com.saritha.fashions.repository;

import com.saritha.fashions.model.AppRole;
import com.saritha.fashions.model.Role;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface RoleRepository extends MongoRepository<Role, ObjectId> {
    Optional<Role> findByRoleName(AppRole appRole);
}
