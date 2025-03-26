package com.saritha.fashions.repository;

import com.saritha.fashions.model.Customer;
import com.saritha.fashions.model.Invoice;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CustomerRepository extends MongoRepository<Customer, String > {

}
