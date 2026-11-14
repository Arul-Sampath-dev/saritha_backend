package com.saritha.fashions.modules.employee.repository;

import com.saritha.fashions.modules.employee.model.ManagedEmployee;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Employee accounts ("users" collection) plus the shop / item operations employee
 * management needs (EmployeeRepositoryCustom).
 */
@Repository
public interface EmployeeRepository
        extends MongoRepository<ManagedEmployee, String>, EmployeeRepositoryCustom {

    Optional<ManagedEmployee> findByEmail(String email);
}
