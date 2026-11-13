package com.saritha.fashions.service;

import com.saritha.fashions.controller.AuthController;
import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.Expense;
import com.saritha.fashions.model.AppRole;
import com.saritha.fashions.model.Shop;
import com.saritha.fashions.model.User;
import com.saritha.fashions.payload.PaginatedResponse;
import com.saritha.fashions.payload.expense.ExpenseDTO;
import com.saritha.fashions.payload.expense.RefreshmentDetails;
import com.saritha.fashions.payload.shop.*;
import com.saritha.fashions.repository.ShopRepository;
import com.saritha.fashions.security.request.SignupRequest;
import com.saritha.fashions.service.Interface.ShopService;
import com.saritha.fashions.util.AuthUtil;
import com.saritha.fashions.util.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import java.util.List;


@Service
@RequiredArgsConstructor
public class ShopServiceImpl implements ShopService {

    @Autowired
    private ShopRepository shopRepository;
    @Autowired
    private ModelMapper modelMapper;
    @Autowired
    private AuthController authController;
    @Autowired
    private AuthUtil authUtil;
    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * Creates a new shop and registers its owner as a user.
     *
     * Steps:
     * 1. Maps the incoming ShopRegisterDTO to a Shop entity (STRICT matching) and uses
     *    the owner's phone number as the shop contact number.
     * 2. Saves the shop to MongoDB.
     * 3. Builds a SignupRequest for the owner (name, password, phone, email, salary,
     *    address, role "shopOwner") and assigns them to the newly saved shop.
     * 4. Calls AuthController.registerUser to create the owner account.
     * 5. If user registration fails (non-2xx or exception), the saved shop is deleted
     *    manually as a rollback, and an APIException is thrown with the clean error
     *    message extracted from the "message=...)" part of the exception text.
     *
     * Note: @Transactional alone does not roll back here, which is why the shop is
     * deleted manually on failure. Also, setting the matching strategy to STRICT changes
     * the shared ModelMapper bean, so it affects every other mapping in the app afterwards.
     *
     * @param shop shop + owner registration details
     * @return the saved Shop
     */
    @Override
    @Transactional
    public Shop createShop(ShopRegisterDTO shop) {

        modelMapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);
        Shop shopConverted = modelMapper.map(shop, Shop.class);
        shopConverted.setShopContactNumber(shop.getOwnerNumber());

        Shop shopSaved = shopRepository.save(shopConverted);

        SignupRequest signupRequest = new SignupRequest();

        // Explicitly setting the correct name field
        signupRequest.setName(shop.getOwnerName());
        signupRequest.setPassword(shop.getPassword());
        signupRequest.setAssignedShops(shopSaved.getId());
        signupRequest.setPhone(shop.getOwnerNumber());
        signupRequest.setEmail(shop.getOwnerEmailId());
        Set<String> roles = new HashSet<>();
        roles.add("shopOwner");
        signupRequest.setRoles(roles);
        signupRequest.setSalary(shop.getSalary());
        signupRequest.setAddress(shop.getShopAddress());

        try {
            ResponseEntity<?> response = authController.registerUser(signupRequest);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new APIException("User creation failed: " + response.getBody());
            }
        } catch (Exception e) {
            // Manually delete the shop if user registration fails
            shopRepository.deleteById(shopSaved.getId());
//            throw new APIException(e.getMessage());

            Matcher matcher = Pattern.compile("message=(.*?)\\)").matcher(e.getMessage());
            if (matcher.find()) {
                throw new APIException(matcher.group(1));
            } else {
                throw new APIException(e.getMessage());
            }

        }

        return shopSaved;
    }

    /**
     * Returns every shop document in the "shops" collection (full entities, no filtering
     * or pagination).
     */
    @Override
    public List<Shop> getAllShops() {
        return shopRepository.findAll();
    }

    /**
     * Fetches a single shop by its id.
     *
     * @throws APIException "No Shop Found" if no shop exists with the given id
     */
    @Override
    public Shop getShopById(String id) {
        return shopRepository.findById(id).orElseThrow(() -> new APIException("No Shop Found"));
    }

    /**
     * Lightweight list of all shops containing only shopId and shopName.
     * Uses a Mongo aggregation projection so the rest of the shop document
     * (employees, expenses, receipts, etc.) is not loaded. Useful for dropdowns.
     */
    @Override
    public List<ShopDTO> getShopNameAndIdOnly() {
        // Build aggregation: only _id and shopName
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.project("_id", "shopName")
                        .and("_id").as("shopId")
                        .andExclude("_id")
        );

        AggregationResults<ShopDTO> results = mongoTemplate.aggregate(
                aggregation,
                "shops",
                ShopDTO.class
        );

        return results.getMappedResults();
    }


    /**
     * Updates an existing shop.
     *
     * Loads the shop by id, copies the fields from ShopUpdateDTO onto the existing
     * entity using ModelMapper, and saves it.
     *
     * Works as a partial update: ModelMapper is configured with setSkipNullEnabled(true)
     * in AppConfig, so null fields in the DTO are ignored and keep their existing values.
     *
     * @return the updated Shop, or null if no shop exists with the given id
     */
    @Override
    public Shop updateShop(String id, ShopUpdateDTO updatedFields) {
//        Optional<Shop> existingShop = shopRepository.findById(id);
//        if (existingShop.isPresent()) {
//            Shop shop = existingShop.get();
//            shop.setShopName(updatedShop.getShopName());
//            shop.setOwnerName(updatedShop.getOwnerName());
//            shop.setShopAddress(updatedShop.getShopAddress());
//            shop.setShopContactNumber(updatedShop.getShopContactNumber());
//            shop.setGstNumber(updatedShop.getGstNumber());
//            shop.setShopType(updatedShop.getShopType());
//            return shopRepository.save(shop);
//        }
        return shopRepository.findById(id).map(existingShop -> {
            modelMapper.map(updatedFields, existingShop);
            return shopRepository.save(existingShop);
        }).orElse(null);
    }

    /**
     * Deletes the shop document with the given id.
     *
     * Note: this only removes the shop itself. It does not check that the shop exists,
     * and it does not delete or unassign related data (owner/employee users,
     * expenses, invoices, receipts) that reference this shop.
     */
    @Override
    public void deleteShop(String id) {
        shopRepository.deleteById(id);
    }

    /**
     * Returns the employees of the shop the currently logged-in user works in.
     *
     * Steps:
     * 1. Gets the logged-in user's id.
     * 2. Finds the first shop whose "employeeIds" contains that user id
     *    (only the shop id is fetched, for speed).
     * 3. Loads the full shop and returns its "employees" list (lazy @DBRef users).
     *
     * If the user belongs to more than one shop, only the first match is used.
     *
     * @return list of employees (empty list if none), or null if the shop could not be loaded
     * @throws APIException if the user is not assigned to any shop
     */
    @Override
    public List<User> getAllEmployeeFromShops() {
        User user = authUtil.loggedInUser();

        String userId = user.getUserId();
        Query query = Query.query(Criteria.where("employeeIds").is(userId));
        query.fields().include("id");
        List<Shop> shops = mongoTemplate.find(query.limit(1), Shop.class);

        if (shops.isEmpty()) {
            throw new APIException("User is not assigned to any shop");
        }

        String shopId = shops.get(0).getId();

        Shop shop = shopRepository.findById(shopId).orElse(null);
        if (shop != null) {
            return shop.getEmployees() != null ? shop.getEmployees() : new ArrayList<User>();
        }else {
            return null;
        }
    }

    /**
     * Returns the full Shop the logged-in user belongs to (used by callers to read
     * its petty cash details).
     *
     * Finds the first shop whose "employeeIds" contains the current user's id and
     * loads the whole shop document. Despite the name, it returns the entire Shop,
     * not just the petty cash value.
     *
     * @return the user's Shop, or null if it could not be loaded
     * @throws APIException if the user is not assigned to any shop
     */
    @Override
    public Shop getPettyCashOfShop() {
        User user = authUtil.loggedInUser();
//        String shopId = user.getAssignedShops().get(0).getId();

        String userId = user.getUserId();
        Query query = Query.query(Criteria.where("employeeIds").is(userId));
        query.fields().include("id");
        List<Shop> shops = mongoTemplate.find(query.limit(1), Shop.class);

        if (shops.isEmpty()) {
            throw new APIException("User is not assigned to any shop");
        }

        String shopId = shops.get(0).getId();

        return shopRepository.findById(shopId).orElse(null);
    }

//    @Override
//    public RefreshmentDetails getPettyCashFromShopOptimized() {
//        User user = authUtil.loggedInUser();
//        String userId = user.getUserId();
//
//        // Create the aggregation pipeline
//        Aggregation aggregation = Aggregation.newAggregation(
//                // 1. Match shops where user is an employee
//                Aggregation.match(Criteria.where("employeeIds").in(userId)),
//
//                // 2. Convert _id to string for proper matching with expenses.shopId
//                Aggregation.project()
//                        .and("pettyCash").as("pettyCash")
//                        .and(ConvertOperators.ToString.toString("$_id")).as("shopIdStr")
//                        .and("_id").as("shopId"),
//
//                // 3. Lookup expenses using string ID
//                Aggregation.lookup()
//                        .from("expenses")
//                        .localField("shopIdStr")
//                        .foreignField("shopId")
//                        .as("expenses"),
//
//                // 4. Unwind expenses while preserving shop document
//                Aggregation.unwind("$expenses", true),
//
//                // 5. Group back by shop to calculate totals
//                Aggregation.group("$shopId")
//                        .first("$pettyCash").as("pettyCash")
//                        .sum("$expenses.expenseAmount").as("totalExpense"),
//
//                // 6. Final projection
//                Aggregation.project()
//                        .and("$pettyCash").as("pettyCash")
//                        .and("$totalExpense").as("totalExpense")
//                        .andExclude("_id")
//        );
//
//        // Execute aggregation
//        AggregationResults<RefreshmentDetails> results = mongoTemplate.aggregate(
//                aggregation,
//                "shops",
//                RefreshmentDetails.class
//        );
//
//        // Return results or default if empty
//        RefreshmentDetails details = results.getUniqueMappedResult();
//        return details != null ? details : new RefreshmentDetails(0.0, 0.0, 0.0, "");
//    }

    /**
     * Returns the petty cash summary for the logged-in user's shop in one DB call.
     *
     * Runs a Mongo aggregation on "shops" that:
     * 1. Matches the shop(s) where the current user is in "employeeIds".
     * 2. Sums allPettyCashDetails.amount -> total petty cash ever added (pettyCash).
     * 3. Looks up the shop's expenses and keeps only those paid by "Cash"
     *    (case-insensitive).
     * 4. Sums those cash expenses -> totalExpense.
     *
     * Note: because step 3 filters on paymentMethod after the unwind, a shop with no
     * cash expenses drops out of the pipeline, so the default (all zeros) is returned
     * even if petty cash was added. Also, getUniqueMappedResult() throws if the user
     * belongs to more than one shop.
     *
     * @return RefreshmentDetails with pettyCash and totalExpense, or a zeroed default
     */
    @Override
    public RefreshmentDetails getPettyCashFromShopOptimized() {
        User user = authUtil.loggedInUser();
        String userId = user.getUserId();

        // Aggregation pipeline
        Aggregation aggregation = Aggregation.newAggregation(
                // 1️⃣ Match shops where user is an employee
                Aggregation.match(Criteria.where("employeeIds").in(userId)),

                // 2️⃣ Prepare shop data and allPettyCashDetails
                Aggregation.project()
                        .and(ConvertOperators.ToString.toString("$_id")).as("shopIdStr")
                        .and("_id").as("shopId")
                        .and("allPettyCashDetails").as("allPettyCashDetails"),

                // 3️⃣ Calculate totalPettyCash = sum of allPettyCashDetails.amount
                Aggregation.unwind("allPettyCashDetails", true),
                Aggregation.group("$shopId")
                        .sum("allPettyCashDetails.amount").as("totalPettyCash")
                        .first("$shopIdStr").as("shopIdStr"),

                // 4️⃣ Lookup expenses for this shop
                Aggregation.lookup("expenses", "shopIdStr", "shopId", "expenses"),

                // 5️⃣ Unwind expenses
                Aggregation.unwind("expenses", true),

                // 6️⃣ Filter only Cash paymentMethod (case-insensitive)
                Aggregation.match(Criteria.where("expenses.paymentMethod").regex("^Cash$", "i")),

                // 7️⃣ Group back to compute totalExpense (only Cash ones)
                Aggregation.group("$shopId")
                        .first("$totalPettyCash").as("totalPettyCash")
                        .sum("expenses.expenseAmount").as("totalExpense"),

                // 8️⃣ Final projection
                Aggregation.project()
                        .and("$totalPettyCash").as("pettyCash")
                        .and("$totalExpense").as("totalExpense")
                        .andExclude("_id")
        );

        // Execute aggregation
        AggregationResults<RefreshmentDetails> results = mongoTemplate.aggregate(
                aggregation,
                "shops",
                RefreshmentDetails.class
        );

        RefreshmentDetails details = results.getUniqueMappedResult();
        return details != null ? details : new RefreshmentDetails(0.0, 0.0, 0.0, "");
    }


    /**
     * Adds petty cash to a shop.
     *
     * Steps:
     * 1. Authorization: only allowed if the logged-in user has the OWNER role
     *    (platform owner) or is assigned to this shop; otherwise AccessDeniedException.
     * 2. Loads the shop and adds the amount to its current pettyCash balance.
     * 3. Appends a history entry (PettyCashDTO: amount, date in IST, added-by user id)
     *    to allPettyCashDetails.
     * 4. Saves and returns the updated shop.
     *
     * @param amount amount to add (no validation for negative/zero values)
     * @param shopId shop to top up
     * @throws APIException "Shop Not Found" if the shop does not exist
     */
    @Override
    public Shop setPettyCashToShop(double amount, String shopId) {
        User currentUser = authUtil.loggedInUser();
        boolean platformOwner = currentUser.getRoles() != null && currentUser.getRoles().stream()
                .anyMatch(role -> role.getRoleName() == AppRole.OWNER);
        boolean assigned = currentUser.getAssignedShops() != null && currentUser.getAssignedShops().stream()
                .filter(java.util.Objects::nonNull)
                .anyMatch(shop -> shopId.equals(shop.getId()));
        if (!platformOwner && !assigned) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You are not authorized to access this shop");
        }
        Shop shop = shopRepository.findById(shopId).orElse(null);

        if (shop != null) {
            double previousPettyCashAmount = shop.getPettyCash();
            shop.setPettyCash(previousPettyCashAmount+amount);

            PettyCashDTO pettyCashDTO = new PettyCashDTO();
            pettyCashDTO.setAddingDate(DateTimeUtils.getNowInIST());
            pettyCashDTO.setAmount(amount);
            pettyCashDTO.setAddedBy(authUtil.loggedInUserId());

            List<PettyCashDTO> pettyCashDTOS = shop.getAllPettyCashDetails();
            pettyCashDTOS.add(pettyCashDTO);
            shop.setAllPettyCashDetails(pettyCashDTOS);

            return shopRepository.save(shop);
        } else {
            throw new APIException("Shop Not Found");
        }
    }

    /**
     * Paginated, sorted search of a shop's expenses.
     *
     * Filters applied to the "expenses" collection:
     * - shopId must match (shop must exist, else "Shop Not Found").
     * - Optional date range on expenseDate: startDate is inclusive, endDate is made
     *   inclusive by querying "< endDate + 1 day". Throws if endDate is before startDate.
     * - Optional search term: case-insensitive regex on expenseType or expenseAmount.
     *   (expenseAmount is numeric, so the regex will not match it in practice; the term
     *   is also not regex-escaped, so characters like "(" can cause an error.)
     *
     * Counts total matches, applies page/sort, and maps results to ExpenseDTO.
     *
     * @return PaginatedResponse with the page content and paging metadata
     * @throws APIException "No expenses found" if the requested page is empty
     */
    @Override
    public PaginatedResponse<ExpenseDTO> searchAllExpensesByShopId(
            Integer pageNumber,
            Integer pageSize,
            String sortBy,
            String sortOrder,
            String shopId,
            Date startDate,
            Date endDate,
            String term)
    {
        // Validate date range if both dates are provided
        if (startDate != null && endDate != null && endDate.before(startDate)) {
            throw new APIException("End date must be after start date");
        }

        // Validate shop exists
        if (!shopRepository.existsById(shopId)) {
            throw new APIException("Shop Not Found");
        }

        // Create sort direction
        Sort.Direction direction = sortOrder.equalsIgnoreCase("asc") ?
                Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, sortBy);

        // Create pageable with sorting
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);

        // Build the MongoDB query
        Query query = new Query();

        // Filter by shop ID
        query.addCriteria(Criteria.where("shopId").is(shopId));

        // Add date range filtering if dates are provided
        if (startDate != null && endDate != null) {
            // Increase endDate by 1 day to make it inclusive
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(endDate);
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            Date inclusiveEndDate = calendar.getTime();

            query.addCriteria(Criteria.where("expenseDate")
                    .gte(startDate)
                    .lt(inclusiveEndDate));
        } else if (startDate != null) {
            query.addCriteria(Criteria.where("expenseDate").gte(startDate));
        } else if (endDate != null) {
            // Increase endDate by 1 day to make it inclusive
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(endDate);
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            Date inclusiveEndDate = calendar.getTime();

            query.addCriteria(Criteria.where("expenseDate").lt(inclusiveEndDate));
        }

        // Add search term filtering if provided
        if (term != null && !term.isEmpty()) {
            Pattern pattern = Pattern.compile(term, Pattern.CASE_INSENSITIVE);
            Criteria searchCriteria = new Criteria().orOperator(
                    Criteria.where("expenseType").regex(pattern),
                    Criteria.where("expenseAmount").regex(pattern)
            );
            query.addCriteria(searchCriteria);
        }

        // Get total count
        long totalCount = mongoTemplate.count(query, Expense.class);

        // Apply pagination and execute query
        query.with(pageable);
        List<Expense> expenseList = mongoTemplate.find(query, Expense.class);

        if (expenseList.isEmpty()) {
            throw new APIException("No expenses found");
        }

        // Map to DTOs
        List<ExpenseDTO> expenseDTOS = expenseList.stream()
                .map(expense -> {
                    ExpenseDTO dto = modelMapper.map(expense, ExpenseDTO.class);
                    return dto;
                })
                .collect(Collectors.toList());

        // Calculate total pages
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        boolean lastPage = pageNumber >= totalPages - 1;

        return new PaginatedResponse<>(
                expenseDTOS,
                pageNumber,
                expenseList.size(),
                totalCount,
                totalPages,
                lastPage
        );
    }

    /**
     * Same as searchAllExpensesByShopId, but the search term is matched against
     * paymentMethod (e.g. "Cash", "Bank") instead of expenseType/expenseAmount.
     *
     * Filters: shopId, optional inclusive date range on expenseDate, and an optional
     * case-insensitive regex on paymentMethod. Returns a paginated, sorted list of
     * ExpenseDTO.
     *
     * Note: almost all of this code is duplicated from searchAllExpensesByShopId and
     * could be merged into one method with a "search field" parameter.
     *
     * @throws APIException if the date range is invalid, the shop is not found,
     *                      or no expenses are found for the requested page
     */
    @Override
    public PaginatedResponse<ExpenseDTO> searchAllExpensesByShopIdWithPaymentMethod(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder, String shopId, Date startDate, Date endDate, String term) {
        // Validate date range if both dates are provided
        if (startDate != null && endDate != null && endDate.before(startDate)) {
            throw new APIException("End date must be after start date");
        }

        // Validate shop exists
        if (!shopRepository.existsById(shopId)) {
            throw new APIException("Shop Not Found");
        }

        // Create sort direction
        Sort.Direction direction = sortOrder.equalsIgnoreCase("asc") ?
                Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, sortBy);

        // Create pageable with sorting
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);

        // Build the MongoDB query
        Query query = new Query();

        // Filter by shop ID
        query.addCriteria(Criteria.where("shopId").is(shopId));

        // Add date range filtering if dates are provided
        if (startDate != null && endDate != null) {
            // Increase endDate by 1 day to make it inclusive
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(endDate);
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            Date inclusiveEndDate = calendar.getTime();

            query.addCriteria(Criteria.where("expenseDate")
                    .gte(startDate)
                    .lt(inclusiveEndDate));
        } else if (startDate != null) {
            query.addCriteria(Criteria.where("expenseDate").gte(startDate));
        } else if (endDate != null) {
            // Increase endDate by 1 day to make it inclusive
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(endDate);
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            Date inclusiveEndDate = calendar.getTime();

            query.addCriteria(Criteria.where("expenseDate").lt(inclusiveEndDate));
        }

        // Add search term filtering if provided
        if (term != null && !term.isEmpty()) {
            Pattern pattern = Pattern.compile(term, Pattern.CASE_INSENSITIVE);
            Criteria searchCriteria = new Criteria().orOperator(
                    Criteria.where("paymentMethod").regex(pattern)
            );
            query.addCriteria(searchCriteria);
        }

        // Get total count
        long totalCount = mongoTemplate.count(query, Expense.class);

        // Apply pagination and execute query
        query.with(pageable);
        List<Expense> expenseList = mongoTemplate.find(query, Expense.class);

        if (expenseList.isEmpty()) {
            throw new APIException("No expenses found");
        }

        // Map to DTOs
        List<ExpenseDTO> expenseDTOS = expenseList.stream()
                .map(expense -> {
                    ExpenseDTO dto = modelMapper.map(expense, ExpenseDTO.class);
                    return dto;
                })
                .collect(Collectors.toList());

        // Calculate total pages
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        boolean lastPage = pageNumber >= totalPages - 1;

        return new PaginatedResponse<>(
                expenseDTOS,
                pageNumber,
                expenseList.size(),
                totalCount,
                totalPages,
                lastPage
        );
    }

    /**
     * Dashboard summary for every shop, built by a single aggregation in
     * ShopRepository.findAllShopSummaries().
     *
     * For each shop it joins expenses, invoices, receipts and users, and returns basic
     * shop info plus: totalEmployee, totalExpense (cash), totalExpenseEntry (bank),
     * totalInvoice, totalRevenue, totalOutstanding, totalPaidInvoice, totalReceipt,
     * totalPendingOrders and totalCompletedOrders.
     *
     * Note: the expenses $lookup in the repository only projects totalAmountWithGST,
     * balanceDue, paymentStatus and orderStatus, so paymentMethod and expenseAmount are
     * dropped and totalExpense / totalExpenseEntry will always come back as 0.
     */
    @Override
    public List<AllShopDTO> findAllShopSummaries() {
        return shopRepository.findAllShopSummaries();
    }

}
