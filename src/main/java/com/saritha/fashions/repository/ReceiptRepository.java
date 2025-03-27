package com.saritha.fashions.repository;

import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.Receipt;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ReceiptRepository extends MongoRepository<Receipt, String > {

}
