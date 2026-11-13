package com.saritha.fashions.modules.employeemanagement.repository;

import com.saritha.fashions.modules.employeemanagement.model.ManagedEmployee;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Employee accounts ("users" collection) plus the shop / item operations employee
 * management needs (EmployeeManagementRepositoryCustom).
 */
@Repository
public interface EmployeeManagementRepository
        extends MongoRepository<ManagedEmployee, String>, EmployeeManagementRepositoryCustom {

    Optional<ManagedEmployee> findByEmail(String email);
}
