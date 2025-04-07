package com.saritha.fashions.repository;

import com.saritha.fashions.model.Employee;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeRepository extends MongoRepository<Employee, String> {
    List<Employee> findByAssignedShopsId(String shopId);
}
