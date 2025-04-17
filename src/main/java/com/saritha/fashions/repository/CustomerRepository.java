package com.saritha.fashions.repository;

import com.saritha.fashions.model.Customer;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CustomerRepository extends MongoRepository<Customer, String > {

    Customer findByPhone(String phone);

    boolean existsByCustomerId(String customerId);

    Optional<Customer> findByCustomerId(String customerId);
}
