package com.saritha.fashions.repository;

import com.saritha.fashions.model.Shop;
import com.saritha.fashions.payload.shop.AllShopDTO;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShopRepository extends MongoRepository<Shop, String> {
    Shop findByShopName(String assignedShops);

    @Aggregation(pipeline = {
            // Expenses lookup with _id to string conversion
            "{ $lookup: { " +
                    "from: 'expenses', " +
                    "let: { shopIdStr: { $toString: '$_id' } }, " +
                    "pipeline: [ " +
                    "{ $match: { $expr: { $eq: ['$shopId', '$$shopIdStr'] } } }, " +
                    "{ $project: { totalAmountWithGST: 1, balanceDue: 1, paymentStatus: 1, orderStatus: 1 } } " +
                    "], " +
                    "as: 'expenses' " +
                    "} }",

            // Invoice lookup with _id to string conversion
            "{ $lookup: { " +
                    "from: 'invoice', " +
                    "let: { shopIdStr: { $toString: '$_id' } }, " +
                    "pipeline: [ " +
                    "{ $match: { $expr: { $eq: ['$shopId', '$$shopIdStr'] } } } " +
                    "], " +
                    "as: 'invoices' " +
                    "} }",

            // Receipt lookup with _id to string conversion
            "{ $lookup: { " +
                    "from: 'receipt', " +
                    "let: { shopIdStr: { $toString: '$_id' } }, " +
                    "pipeline: [ " +
                    "{ $match: { $expr: { $eq: ['$shopId', '$$shopIdStr'] } } } " +
                    "], " +
                    "as: 'receipts' " +
                    "} }",

            // Users lookup - with null handling for assignedShops
            "{ $lookup: { " +
                    "from: 'users', " +
                    "let: { shopId: '$_id' }, " +
                    "pipeline: [ " +
                    "{ $match: { " +
                    "$expr: { " +
                    "$and: [ " +
                    "{ $ifNull: ['$assignedShops', false] }, " +
                    "{ $in: [ " +
                    "'$$shopId', " +
                    "{ $map: { " +
                    "input: '$assignedShops', " +
                    "in: { $toObjectId: '$$this.$id' } " +
                    "} } " +
                    "] } " +
                    "] " +
                    "} " +
                    "} } " +
                    "], " +
                    "as: 'employees' " +
                    "} }",

            // Project stage
            "{ $project: { " +
                    "_id: 0, " +
                    "id: '$_id', " +
                    "shopName: 1, " +
                    "shopContactNumber: 1, " +
                    "shopAddress: 1, " +
                    "shopType: 1, " +
                    "gstNumber: 1, " +
                    "pettyCash: 1, " +
                    "ownerName: 1, " +
                    "totalEmployee: { $size: '$employees' }, " +
                    "totalExpense: { " +
                    "$reduce: { " +
                    "input: { " +
                    "$filter: { " +
                    "input: '$expenses', " +
                    "as: 'e', " +
                    "cond: { $eq: [{ $toLower: '$$e.paymentMethod' }, 'cash'] } " +
                    "} " +
                    "}, " +
                    "initialValue: 0, " +
                    "in: { $add: ['$$value', '$$this.expenseAmount'] } " +
                    "} " +
                    "}, " +
                    "totalExpenseEntry: { " +
                    "$reduce: { " +
                    "input: { " +
                    "$filter: { " +
                    "input: '$expenses', " +
                    "as: 'e', " +
                    "cond: { $eq: [{ $toLower: '$$e.paymentMethod' }, 'bank'] } " +
                    "} " +
                    "}, " +
                    "initialValue: 0, " +
                    "in: { $add: ['$$value', '$$this.expenseAmount'] } " +
                    "} " +
                    "}, " +
                    "totalInvoice: { $size: '$invoices' }, " +
                    "totalRevenue: { $sum: '$invoices.totalAmountWithGST' }, " +
                    "totalOutstanding: { $sum: '$invoices.balanceDue' }, " +
                    "totalPaidInvoice: { " +
                    "$size: { $filter: { " +
                    "input: '$invoices', " +
                    "as: 'inv', " +
                    "cond: { $eq: ['$$inv.paymentStatus', 'PAID'] } " +
                    "} } " +
                    "}, " +
                    "totalReceipt: { $size: '$receipts' }, " +
                    "totalPendingOrders: { " +
                    "$size: { " +
                    "$filter: { " +
                    "input: '$invoices', " +
                    "as: 'inv', " +
                    "cond: { $ne: ['$$inv.orderStatus', 'ORDER_DELIVERED'] } " +
                    "} " +
                    "} " +
                    "}, " +
                    "totalCompletedOrders: { " +
                    "$size: { " +
                    "$filter: { " +
                    "input: '$invoices', " +
                    "as: 'inv', " +
                    "cond: { $eq: ['$$inv.orderStatus', 'ORDER_DELIVERED'] } " +
                    "} " +
                    "} " +
                    "} " +
                    "} }"
    })
    List<AllShopDTO> findAllShopSummaries();

//    @Aggregation(pipeline = {
//            // Expenses lookup with _id to string conversion
//            "{ $lookup: { " +
//                    "from: 'expenses', " +
//                    "let: { shopIdStr: { $toString: '$_id' } }, " +
//                    "pipeline: [ " +
//                    "{ $match: { $expr: { $eq: ['$shopId', '$$shopIdStr'] } } } " +
//                    "], " +
//                    "as: 'expenses' " +
//                    "} }",
//
//            // Invoice lookup with _id to string conversion
//            "{ $lookup: { " +
//                    "from: 'invoice', " +
//                    "let: { shopIdStr: { $toString: '$_id' } }, " +
//                    "pipeline: [ " +
//                    "{ $match: { $expr: { $eq: ['$shopId', '$$shopIdStr'] } } } " +
//                    "], " +
//                    "as: 'invoices' " +
//                    "} }",
//
//            // Receipt lookup with _id to string conversion
//            "{ $lookup: { " +
//                    "from: 'receipt', " +
//                    "let: { shopIdStr: { $toString: '$_id' } }, " +
//                    "pipeline: [ " +
//                    "{ $match: { $expr: { $eq: ['$shopId', '$$shopIdStr'] } } } " +
//                    "], " +
//                    "as: 'receipts' " +
//                    "} }",
//
//            // Users lookup - with null handling for assignedShops
//            "{ $lookup: { " +
//                    "from: 'users', " +
//                    "let: { shopId: '$_id' }, " +
//                    "pipeline: [ " +
//                    "{ $match: { " +
//                    "$expr: { " +
//                    "$and: [ " +
//                    "{ $ifNull: ['$assignedShops', false] }, " +
//                    "{ $in: [ " +
//                    "'$$shopId', " +
//                    "{ $map: { " +
//                    "input: '$assignedShops', " +
//                    "in: { $toObjectId: '$$this.$id' } " +
//                    "} } " +
//                    "] } " +
//                    "] " +
//                    "} " +
//                    "} } " +
//                    "], " +
//                    "as: 'employees' " +
//                    "} }",
//
//            // Project stage
//            "{ $project: { " +
//                    "_id: 0, " +
//                    "id: '$_id', " +
//                    "shopName: 1, " +
//                    "shopContactNumber: 1, " +
//                    "shopAddress: 1, " +
//                    "shopType: 1, " +
//                    "gstNumber: 1, " +
//                    "pettyCash: 1, " +
//                    "ownerName: 1, " +
//                    "totalEmployee: { $size: '$employees' }, " +
//                    "totalExpense: { " +
//                    "$reduce: { " +
//                    "input: '$expenses', " +
//                    "initialValue: 0, " +
//                    "in: { $add: ['$$value', '$$this.expenseAmount'] } " +
//                    "} " +
//                    "}, " +
//                    "totalExpenseEntry: { " +
//                    "$reduce: { " +
//                    "input: { " +
//                    "$filter: { " +
//                    "input: '$expenses', " +
//                    "as: 'e', " +
//                    "cond: { $eq: ['$$e.paymentMethod', 'BANK'] } " +
//                    "} " +
//                    "}, " +
//                    "initialValue: 0, " +
//                    "in: { $add: ['$$value', '$$this.expenseAmount'] } " +
//                    "} " +
//                    "}, " +
//                    "totalInvoice: { $size: '$invoices' }, " +
//                    "totalReceipt: { $size: '$receipts' }, " +
//                    "totalPendingOrders: { " +
//                    "$size: { " +
//                    "$filter: { " +
//                    "input: '$invoices', " +
//                    "as: 'inv', " +
//                    "cond: { $ne: ['$$inv.orderStatus', 'ORDER_DELIVERED'] } " +
//                    "} " +
//                    "} " +
//                    "}, " +
//                    "totalCompletedOrders: { " +
//                    "$size: { " +
//                    "$filter: { " +
//                    "input: '$invoices', " +
//                    "as: 'inv', " +
//                    "cond: { $eq: ['$$inv.orderStatus', 'ORDER_DELIVERED'] } " +
//                    "} " +
//                    "} " +
//                    "} " +
//                    "} }"
//    })
//    List<AllShopDTO> findAllShopSummaries();
}
