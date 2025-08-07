package com.saritha.fashions.repository;

import com.saritha.fashions.model.Role;
import com.saritha.fashions.model.User;
import com.saritha.fashions.repository.AggregationInterface.UserWithShopProjection;
import lombok.Data;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface UserRepository extends MongoRepository<User, String > {

//    @Cacheable(value = "users", key = "#email.toLowerCase().trim()")
//    @Query(value = "{ 'email' : ?0 }", fields = "{ 'password' : 1, 'username' : 1, 'roles' : 1, 'name' : 1, 'assignedShops' : 1 }")
//    User findByEmail(String email);

    @Query(value = "{ 'email' : ?0 }", fields = "{ 'password' : 1, 'roles' : 1, 'name' : 1, 'email' : 1, 'userId' : 1, 'assignedShops' : 1}")
    User findByEmail(String email);

    @Aggregation(pipeline = {
            "{ $match: { email: ?0 } }",
            "{ $addFields: { " +
                    "assignedShopIds: { " +
                    "$map: { " +
                    "input: { $ifNull: [ '$assignedShops', [] ] }, " +
                    "as: 'shop', " +
                    "in: '$$shop.$id' " +
                    "} " +
                    "} " +
                    "} }",
            "{ $lookup: { " +
                    "from: 'shops', " +
                    "let: { shopIds: '$assignedShopIds' }, " +
                    "pipeline: [ " +
                    "{ $match: { $expr: { $in: [ '$_id', '$$shopIds' ] } } } " +
                    "], " +
                    "as: 'shopData' " +
                    "} }",
            "{ $project: { " +
                    "userId: '$_id', " +
                    "password: 1, " +
                    "name: 1, " +
                    "email: 1, " +
                    "shopName: { $ifNull: [ { $arrayElemAt: [ '$shopData.shopName', 0 ] }, 'No Shop Assigned' ] } " +
                    "} }"
    })
    UserWithShopProjection findUserWithShop(String email);

    @Query(value = "{ 'email' : ?0 }", fields = "{ 'roles' : 1 }")
    User findUserRoles(String email);

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String user1);

    boolean existsByEmail(String email);

    Optional<User> findByUserId(String userId);

    List<User> findByAccountCreationDate(Date yesterdayString);

    List<User> findByAssignedShopsId(String shopId);

    @Query(value = "{ 'email' : ?0 }", fields = "{ 'password' : 1, 'username' : 1, 'roles' : 1, 'name' : 1, 'assignedShops' : 1 }")
    User findByEmailNoCache(String email);
}
