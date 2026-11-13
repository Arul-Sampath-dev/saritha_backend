package com.saritha.fashions.modules.expensemanagement.repository;

import com.saritha.fashions.modules.expensemanagement.model.ExpenseRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Mongo repository for the ExpenseManagement module ("expenses" collection).
 *
 * Named differently from com.saritha.fashions.repository.ExpenseRepository so the two
 * Spring beans do not clash. Shop-side updates (petty cash / totals) come from
 * ExpenseRecordRepositoryCustom.
 */
@Repository
public interface ExpenseRecordRepository
        extends MongoRepository<ExpenseRecord, String>, ExpenseRecordRepositoryCustom {

    /** Finds an expense only if it belongs to the given shop. */
    Optional<ExpenseRecord> findByIdAndShopId(String id, String shopId);
}
