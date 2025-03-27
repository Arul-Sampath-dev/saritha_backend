package com.saritha.fashions.repository;

import com.saritha.fashions.model.Customer;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CustomerRepository extends MongoRepository<Customer, String > {

    Customer findByPhone(String phone);
}
