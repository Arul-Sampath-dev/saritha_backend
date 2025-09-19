package com.saritha.fashions.service;

import com.saritha.fashions.config.OrderStatus;
import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.*;
import com.saritha.fashions.payload.PaginatedResponse;
import com.saritha.fashions.payload.invoice.InvoiceResponse;
import com.saritha.fashions.payload.items.Items;
import com.saritha.fashions.payload.shop.ShopDTO;
import com.saritha.fashions.payload.tailor.OrderStatusUpdate;
import com.saritha.fashions.payload.user.MinimumUserDetailsDTO;
import com.saritha.fashions.repository.InvoiceRepository;
import com.saritha.fashions.service.Interface.EmployeeActivityService;
import com.saritha.fashions.service.Interface.ShopAssistantService;
import com.saritha.fashions.util.AuthUtil;
import com.saritha.fashions.util.DateTimeUtils;
import org.bson.Document;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ShopAssistantServiceImpl implements ShopAssistantService {

    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private AuthUtil authUtil;
    @Autowired
    private ModelMapper modelMapper;
    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private EmployeeActivityService employeeActivityService;

    private static final List<String> EXCLUDED_ITEMS = List.of("NoUse");
    private static final List<String> ALLOWED_ITEM_STATUSES = List.of(
            "SHOP_ASSISTANT_ASSIGNED", "IRONING", "HEMMING"
    );

    @Override
    public PaginatedResponse getAllInvoiceFromParticularShopForAssistant(
            Integer page, Integer size, String sortBy, String sortOrder, Date startDate, Date endDate) {

        User user = authUtil.loggedInUser();
        String userId = user.getUserId();

        // Find shop for logged-in tailor
        Query shopQuery = Query.query(Criteria.where("employeeIds").is(userId)).limit(1);
        shopQuery.fields().include("id");
        List<Shop> shops = mongoTemplate.find(shopQuery, Shop.class);
        if (shops.isEmpty()) throw new APIException("User is not assigned to any shop");
        String shopId = shops.get(0).getId();

        // --- Aggregation pipeline ---
        Criteria initialMatch = Criteria.where("shopId").is(shopId)
                .and("items.itemName").nin(EXCLUDED_ITEMS);
        AggregationOperation matchShop = Aggregation.match(initialMatch);

        AggregationOperation unwindItems = Aggregation.unwind("items");

        // Item status allowed
        Criteria itemStatusCrit = Criteria.where("items.itemOrderStatus").in(ALLOWED_ITEM_STATUSES);

        // Build elemMatch for assignment history with date filtering
        List<Criteria> tsCriteria = new ArrayList<>();
        if (startDate != null) tsCriteria.add(Criteria.where("timestamp").gte(startDate));
        if (endDate != null) tsCriteria.add(Criteria.where("timestamp").lte(endDate));

        Criteria elemMatch;
        if (tsCriteria.isEmpty()) {
            elemMatch = Criteria.where("tailorId").is(userId);
        } else {
            elemMatch = new Criteria().andOperator(
                    Criteria.where("tailorId").is(userId),
                    new Criteria().andOperator(tsCriteria.toArray(new Criteria[0]))
            );
        }

        Criteria assignmentHistoryCrit = Criteria.where("items.assignmentHistory").elemMatch(elemMatch);

        // Direct assigned (must also have history in range!)
        Criteria assignedWithDate = new Criteria().andOperator(
                Criteria.where("items.assignedTo").is(userId),
                assignmentHistoryCrit
        );

        // Final match: status + (assignedWithDate OR historyWithDate)
        Criteria fullItemMatch = new Criteria().andOperator(
                itemStatusCrit,
                new Criteria().orOperator(assignedWithDate, assignmentHistoryCrit)
        );

        AggregationOperation matchItems = Aggregation.match(fullItemMatch);

        // Lookup customer
        Document lookupStage = new Document("$lookup",
                new Document("from", "customers")
                        .append("localField", "customer.$id")
                        .append("foreignField", "_id")
                        .append("as", "customerObj"));
        AggregationOperation lookupOp = ctx -> lookupStage;

        // Unwind customer
        Document unwindCustomer = new Document("$unwind",
                new Document("path", "$customerObj").append("preserveNullAndEmptyArrays", true));
        AggregationOperation unwindCustomerOp = ctx -> unwindCustomer;

        // Add fields
        List<Object> filterCondParts = new ArrayList<>();
        filterCondParts.add(new Document("$eq", Arrays.asList("$$ah.tailorId", userId)));
        if (startDate != null) filterCondParts.add(new Document("$gte", Arrays.asList("$$ah.timestamp", startDate)));
        if (endDate != null)   filterCondParts.add(new Document("$lte", Arrays.asList("$$ah.timestamp", endDate)));

        Object condForFilter = filterCondParts.size() == 1
                ? filterCondParts.get(0)
                : new Document("$and", filterCondParts);

        Document filterAssignedRecords = new Document("$filter",
                new Document("input", new Document("$ifNull", Arrays.asList("$items.assignmentHistory", Collections.emptyList())))
                        .append("as", "ah")
                        .append("cond", condForFilter));

        Document mapTimestamps = new Document("$map",
                new Document("input", "$assignmentRecordsForUser")
                        .append("as", "r")
                        .append("in", "$$r.timestamp"));

        Document addFieldsDoc = new Document("$addFields", new Document()
                .append("assignmentRecordsForUser", filterAssignedRecords)
                .append("assignmentTimestampsForUser", mapTimestamps));
        AggregationOperation addFieldsOp = ctx -> addFieldsDoc;

        // Project
        Document projectDoc = new Document("$project", new Document()
                .append("_id", 0)
                .append("invoiceId", "$invoiceId")
                .append("invoiceIdForCloudBucket", "$invoiceIdForCloudBucket")
                .append("customer", new Document()
                        .append("_id", "$customerObj._id")
                        .append("customerId", "$customerObj.customerId")
                        .append("name", "$customerObj.name"))
                .append("item", "$items")
                .append("assignedDate", new Document("$cond", Arrays.asList(
                        new Document("$gt", Arrays.asList(
                                new Document("$size", new Document("$ifNull", Arrays.asList("$assignmentTimestampsForUser", Collections.emptyList()))),
                                0
                        )),
                        new Document("$last", new Document("$ifNull", Arrays.asList("$assignmentTimestampsForUser", Collections.emptyList()))),
                        null
                )))
                .append("invoiceDate", "$invoiceDate"));
        AggregationOperation projectOp = ctx -> projectDoc;

        Sort.Direction dir = "desc".equalsIgnoreCase(sortOrder) ? Sort.Direction.DESC : Sort.Direction.ASC;
        AggregationOperation sortOp = Aggregation.sort(dir, sortBy);

        int skip = page * size;
        AggregationOperation skipOp = Aggregation.skip((long) skip);
        AggregationOperation limitOp = Aggregation.limit(size);

        List<AggregationOperation> pipeline = Arrays.asList(
                matchShop,
                unwindItems,
                matchItems,
                lookupOp,
                unwindCustomerOp,
                addFieldsOp,
                projectOp,
                sortOp,
                skipOp,
                limitOp
        );

        Aggregation aggregation = Aggregation.newAggregation(pipeline);
        AggregationResults<Document> aggResults = mongoTemplate.aggregate(aggregation, "invoice", Document.class);
        List<Document> pageContent = aggResults.getMappedResults();

        // --- Count pipeline (same match) ---
        List<AggregationOperation> countPipeline = new ArrayList<>(Arrays.asList(
                matchShop,
                unwindItems,
                matchItems,
                context -> new Document("$count", "total")
        ));
        Aggregation countAgg = Aggregation.newAggregation(countPipeline);
        AggregationResults<Document> countRes = mongoTemplate.aggregate(countAgg, "invoice", Document.class);

        int totalElements = 0;
        if (!countRes.getMappedResults().isEmpty()) {
            totalElements = countRes.getMappedResults().get(0).getInteger("total", 0);
        }

        if (pageContent.isEmpty() && totalElements == 0) {
            throw new APIException("No assigned items found for the selected date range");
        }

        int totalPages = (int) Math.ceil((double) totalElements / size);

        PaginatedResponse response = new PaginatedResponse();
        response.setContent(pageContent);
        response.setPageNumber(page);
        response.setPageSize(size);
        response.setTotalElements((long) totalElements);
        response.setTotalPages(totalPages);
        response.setLastPage(page >= totalPages - 1);
        return response;
    }

//@Override
//    public InvoiceResponse getAllInvoiceFromParticularShopForAssistant(
//            Integer page, Integer size, String sortBy, String sortOrder, Date startDate, Date endDate) {
//
//        User user = authUtil.loggedInUser();
//        String userId = user.getUserId();
//
//        // Find the shop for the logged-in user (unchanged)
//        Query shopQuery = Query.query(Criteria.where("employeeIds").is(userId)).limit(1);
//        shopQuery.fields().include("id");
//
//        List<Shop> shops = mongoTemplate.find(shopQuery, Shop.class);
//
//        if (shops.isEmpty()) throw new APIException("User is not assigned to any shop");
//        String shopId = shops.get(0).getId();
//
//        // If no date filters are provided: preserve old behaviour but sanitize customer fields
//        if (startDate == null && endDate == null) {
//            Sort sort = "desc".equalsIgnoreCase(sortOrder)
//                    ? Sort.by(sortBy).descending()
//                    : Sort.by(sortBy).ascending();
//            Pageable pageable = PageRequest.of(page, size, sort);
//
//            Page<Invoice> invoicePage = invoiceRepository
//                    .findByShopIdAndItemsItemNameNotInAndItemsItemOrderStatusIn(shopId, EXCLUDED_ITEMS, ALLOWED_ITEM_STATUSES, pageable);
//
//            if (invoicePage.getContent().isEmpty()) {
//                throw new APIException("No Invoices found");
//            }
//
//            // SANITIZE: keep only customer.id, customer.customerId and customer.name
//            List<Invoice> sanitized = invoicePage.getContent().stream().peek(inv -> {
//                Customer c = inv.getCustomer();
//                if (c != null) {
//                    Customer minimal = new Customer(); // uses no-arg constructor + setters
//                    minimal.setId(c.getId());
//                    minimal.setCustomerId(c.getCustomerId());
//                    minimal.setName(c.getName());
//                    inv.setCustomer(minimal);
//                }
//            }).collect(Collectors.toList());
//
//            InvoiceResponse invoiceResponse = new InvoiceResponse();
//            invoiceResponse.setContent(sanitized);
//            invoiceResponse.setPageNumber(invoicePage.getNumber());
//            invoiceResponse.setPageSize(invoicePage.getSize());
//            invoiceResponse.setTotalElements(invoicePage.getTotalElements());
//            invoiceResponse.setTotalPages(invoicePage.getTotalPages());
//            invoiceResponse.setLastPage(invoicePage.isLast());
//            return invoiceResponse;
//        }
//
//        // --- Build aggregation pipeline for date filtered items ---
//
//        // 1) initial match on shopId and exclude common items (reduces scanned docs)
//        Criteria initialMatch = Criteria.where("shopId").is(shopId)
//                .and("items.itemName").nin(EXCLUDED_ITEMS);
//
//        AggregationOperation matchShop = Aggregation.match(initialMatch);
//
//        // 2) unwind items array
//        AggregationOperation unwindItems = Aggregation.unwind("items");
//
//        // 3) build item-level match:
//        // - itemOrderStatus in allowed statuses
//        // - items assigned to the user OR items.assignmentHistory contains tailorId==userId (and optional timestamp range)
//        Criteria itemStatusCrit = Criteria.where("items.itemOrderStatus").in(ALLOWED_ITEM_STATUSES);
//        Criteria assignedToCrit = Criteria.where("items.assignedTo").is(userId);
//
//        // build elemMatch for items.assignmentHistory
//        Criteria elemMatch = Criteria.where("tailorId").is(userId);
//        List<Criteria> timeCriteria = new ArrayList<>();
//        if (startDate != null) timeCriteria.add(Criteria.where("timestamp").gte(startDate));
//        if (endDate != null)   timeCriteria.add(Criteria.where("timestamp").lte(endDate));
//        if (!timeCriteria.isEmpty()) {
//            elemMatch = elemMatch.andOperator(timeCriteria.toArray(new Criteria[0]));
//        }
//        Criteria assignmentHistoryCrit = Criteria.where("items.assignmentHistory").elemMatch(elemMatch);
//
//        // OR condition: either assignedTo == userId OR assignmentHistory has an entry for this user+date
//        Criteria assignedOrHistory = new Criteria().orOperator(assignedToCrit, assignmentHistoryCrit);
//
//        // combine with status
//        Criteria fullItemMatch = new Criteria().andOperator(itemStatusCrit, assignedOrHistory);
//
//        AggregationOperation matchItems = Aggregation.match(fullItemMatch);
//
//        // 4) group back to invoice level and collect only matched items
//        Document groupDoc = new Document()
//                .append("_id", "$_id")
//                .append("doc", new Document("$first", "$$ROOT"))
//                .append("items", new Document("$push", "$items"));
//
//        AggregationOperation groupOp = context -> new Document("$group", groupDoc);
//
//        // 5) replaceRoot to merge doc and items (so items will be the collected array of matched items)
//        Document replaceRootDoc = new Document("$replaceRoot",
//                new Document("newRoot",
//                        new Document("$mergeObjects", Arrays.asList("$doc", new Document("items", "$items")))));
//        AggregationOperation replaceRootOp = context -> replaceRootDoc;
//
//        // ---- NEW: lookup customer (resolve DBRef) and unwind ----
//        // localField "customer.$id" matches DBRef internal structure -> foreignField "_id" in customers collection
//        Document lookup = new Document("$lookup",
//                new Document("from", "customers")
//                        .append("localField", "customer.$id")
//                        .append("foreignField", "_id")
//                        .append("as", "customerObj")
//        );
//        AggregationOperation lookupOp = ctx -> lookup;
//
//        // unwind the array (if no customer found, preserve nulls)
//        Document unwindCustomer = new Document("$unwind",
//                new Document("path", "$customerObj").append("preserveNullAndEmptyArrays", true));
//        AggregationOperation unwindCustomerOp = ctx -> unwindCustomer;
//
//        // Build a $project stage that explicitly selects only allowed fields and uses looked-up customerObj
//        Document projectDoc = new Document("$project", new Document()
//                // invoice-level fields you want to keep
//                .append("_id", 1)
//                .append("invoiceId", 1)
//                .append("invoiceIdForCloudBucket", 1)
//                // Important: only keep minimal customer fields coming from customerObj
//                .append("customer", new Document()
//                        .append("_id", "$customerObj._id")
//                        .append("customerId", "$customerObj.customerId")
//                        .append("name", "$customerObj.name"))
//                .append("customerState", 1)
//                .append("shopId", 1)
//                .append("discount", 1)
//                .append("assignedTailorId", 1)
//                .append("assignmentStatus", 1)
//                .append("assignmentHistory", 1)
//                // keep matched items array (these are the already filtered items from the pipeline)
//                .append("items", 1)
//                // keep other invoice fields you want to show
//                .append("transactionId", 1)
//                .append("totalAmount", 1)
//                .append("advancePaid", 1)
//                .append("balanceDue", 1)
//                .append("paymentMethod", 1)
//                .append("paymentStatus", 1)
//                .append("orderStatus", 1)
//                .append("expectedDeliveryDate", 1)
//                .append("paymentCompletedDate", 1)
//                .append("orderDate", 1)
//                .append("invoiceDate", 1)
//                .append("notes", 1)
//                .append("receiptId", 1)
//                .append("taxableAmount", 1)
//                .append("cgstRate", 1)
//                .append("sgstRate", 1)
//                .append("igstRate", 1)
//                .append("cgstAmount", 1)
//                .append("sgstAmount", 1)
//                .append("igstAmount", 1)
//                .append("totalAmountWithGST", 1)
//                .append("gstin", 1)
//                .append("sacCode", 1)
//                .append("hsnCode", 1)
//                .append("creditNoteIds", 1)
//        );
//
//        AggregationOperation projectOp = context -> projectDoc;
//
//        // 6) optional sort (on invoice root fields)
//        Sort.Direction dir = "desc".equalsIgnoreCase(sortOrder) ? Sort.Direction.DESC : Sort.Direction.ASC;
//        AggregationOperation sortOp = Aggregation.sort(dir, sortBy);
//
//        // 7) pagination: skip & limit
//        int skip = page * size;
//        AggregationOperation skipOp = Aggregation.skip((long) skip);
//        AggregationOperation limitOp = Aggregation.limit(size);
//
//        List<AggregationOperation> pipeline = new ArrayList<>(
//                Arrays.asList(
//                        matchShop,
//                        unwindItems,
//                        matchItems,
//                        groupOp,
//                        replaceRootOp,
//                        // resolve DBRef to customer -> unwind result -> project minimal fields
//                        lookupOp,
//                        unwindCustomerOp,
//                        projectOp,
//                        sortOp,
//                        skipOp,
//                        limitOp
//                )
//        );
//
//        Aggregation aggregation = Aggregation.newAggregation(pipeline);
//
//        AggregationResults<Invoice> aggResults = mongoTemplate.aggregate(aggregation, "invoice", Invoice.class);
//        List<Invoice> pageContent = aggResults.getMappedResults();
//
//        // Build aggregation to compute total matched invoices count (group then $count)
//        List<AggregationOperation> countPipeline = new ArrayList<>(Arrays.asList(matchShop, unwindItems, matchItems, groupOp));
//        // after grouping, count number of groups
//        Document countStage = new Document("$count", "total");
//        AggregationOperation countOp = ctx -> countStage;
//        countPipeline.add(countOp);
//        Aggregation countAgg = Aggregation.newAggregation(countPipeline);
//
//        AggregationResults<Document> countRes = mongoTemplate.aggregate(countAgg, "invoice", Document.class);
//        int totalElements = 0;
//        if (!countRes.getMappedResults().isEmpty()) {
//            totalElements = countRes.getMappedResults().get(0).getInteger("total", 0);
//        }
//
//        if (pageContent.isEmpty()) {
//            throw new APIException("No assigned items found for the selected date range");
//        }
//
//        int totalPages = (int) Math.ceil((double) totalElements / size);
//
//        // Final safety: ensure each invoice's customer is minimal (in case mapping produced nulls or full objects)
//        pageContent.forEach(inv -> {
//            Customer c = inv.getCustomer();
//            if (c != null) {
//                Customer minimal = new Customer();
//                minimal.setId(c.getId());
//                minimal.setCustomerId(c.getCustomerId());
//                minimal.setName(c.getName());
//                inv.setCustomer(minimal);
//            }
//        });
//
//        InvoiceResponse response = new InvoiceResponse();
//        response.setContent(pageContent);
//        response.setPageNumber(page);
//        response.setPageSize(size);
//        response.setTotalElements((long) totalElements);
//        response.setTotalPages(totalPages);
//        response.setLastPage(page >= totalPages - 1);
//        return response;
//    }
//

//    @Override
//    public InvoiceResponse getAssignedOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
//        User user = authUtil.loggedInUser();
//
//        // Set default sorting if not provided
//        if (sortBy == null || sortBy.isEmpty()) {
//            sortBy = "invoiceDate";
//        }
//
//        // Convert sort direction (1 for ascending, -1 for descending)
//        int sortDirection = sortOrder.equalsIgnoreCase("desc") ? 1 : -1;
//
//        // Calculate skip value
//        long skip = (long) pageNumber * pageSize;
//
//        // Get total count
//        long total = invoiceRepository.countAssignedInvoicesExcludingDeliveredForShopAssistant(user.getUserId());
//
//        // Get paginated and sorted results
//        List<Invoice> invoices = invoiceRepository.findAssignedInvoicesExcludingDeliveredForShopAssistant(
//                user.getUserId(),
//                sortBy,
//                sortDirection,
//                skip,
//                pageSize);
//
//        if (invoices.isEmpty()) {
//            throw new APIException("No assigned orders found (excluding delivered orders)");
//        }
//
//        // Calculate pagination metadata
//        int totalPages = (int) Math.ceil((double) total / pageSize);
//        boolean isLastPage = (skip + pageSize) >= total;
//
//        return new InvoiceResponse(
//                invoices,
//                pageNumber,
//                pageSize,
//                total,
//                totalPages,
//                isLastPage
//        );
//    }

    @Override
    @Transactional
    public Invoice updateOrderStatus(OrderStatusUpdate status) {
        // 1. Find the invoice
        Invoice invoice = invoiceRepository.findByInvoiceId(status.getInvoiceId())
                .orElseThrow(() -> new APIException("Invoice not found"));

        // 2. Get authenticated user and validate permissions
        User user = authUtil.loggedInUser();
        boolean isTailorMaster = user.getRoles().stream()
                .anyMatch(role -> role.getRoleName() == AppRole.TAILOR_MASTER);

        // 3. Find and update the specific item
        boolean itemUpdated = false;
        for (Items item : invoice.getItems()) {
            if (item.getItemUniqueId().equals(status.getItemUniqueIds())) {
                // Validate tailor assignment
                if (!isTailorMaster && !user.getUserId().equals(item.getAssignedTo())) {
                    throw new APIException("You are not assigned to this item");
                }

                // Update item status
                item.setItemOrderStatus(status.getNewStatus());

                // Add to history
                item.getAssignmentHistory().add(new Items.AssignmentHistory(
                        user.getUserId(),
                        status.getNewStatus(),
                        DateTimeUtils.getNowInIST()
                ));

                itemUpdated = true;
                break;
            }
        }

        if (!itemUpdated) {
            throw new APIException("Item not found in invoice");
        }

        // 4. Save the updated invoice
        return invoiceRepository.save(invoice);
    }

    @Override
    @Transactional
    public Invoice updateOrderStatusByTailor(OrderStatusUpdate status) {
        // 1. Find the invoice
        Invoice invoice = invoiceRepository.findByInvoiceId(status.getInvoiceId())
                .orElseThrow(() -> new APIException("Invoice not found"));

        // 2. Get authenticated user and validate permissions
        User user = authUtil.loggedInUser();
        boolean isShopAssistant = user.getRoles().stream()
                .anyMatch(role -> role.getRoleName() == AppRole.SHOP_ASSISTANT);

        // ✅ Find item details locally (no extra DB hit)
        Items itemm = invoice.getItems().stream()
                .filter(i -> status.getItemUniqueIds().equals(i.getItemUniqueId()))
                .findFirst()
                .orElseThrow(() -> new APIException("Item not found in invoice"));

        int itemQuantity = itemm.getItemQuantity();
        double itemPrice = itemm.getItemPrice();

        // 👇 Check if already assigned
        boolean alreadyAssigned = itemm.getItemOrderStatus().equals(OrderStatus.ORDER_COMPLETED);

        // 3. Find and update the specific item
        boolean itemUpdated = false;
        for (Items item : invoice.getItems()) {
            if (item.getItemUniqueId().equals(status.getItemUniqueIds())) {
                // Validate shop assistant assignment
                if (!isShopAssistant && !user.getUserId().equals(item.getAssignedToWhichShopAssistant())) {
                    throw new APIException("You are not assigned to this item");
                }

                // Update item status
                item.setItemOrderStatus(status.getNewStatus());

                // Add to history
                item.getAssignmentHistory().add(new Items.AssignmentHistory(
                        user.getUserId(),
                        status.getNewStatus(),
                        DateTimeUtils.getNowInIST()
                ));

                itemUpdated = true;
                break;
            }
        }

        if (!itemUpdated) {
            throw new APIException("Item not found in invoice");
        }

        if (status.getNewStatus().equals(OrderStatus.STITCHING)) {
            employeeActivityService.recordActivity(invoice.getShopId(), invoice.getInvoiceId(), invoice.getId(),
                    user.getUserId(), user.getName(), ActivityType.ASSISTANT_COMPLETED, "Ironing completed, order completed");
        } else if (status.getNewStatus().equals(OrderStatus.IRONING)) {
            employeeActivityService.recordActivity(invoice.getShopId(), invoice.getInvoiceId(), invoice.getId(),
                    user.getUserId(), user.getName(), ActivityType.ASSISTANT_IRONING, "Ironing completed");
        } else if (status.getNewStatus().equals(OrderStatus.HEMMING)) {
            employeeActivityService.recordActivity(invoice.getShopId(), invoice.getInvoiceId(), invoice.getId(),
                    user.getUserId(), user.getName(), ActivityType.ASSISTANT_HEMMING, "Hemming completed");
        } else if (status.getNewStatus().equals(OrderStatus.ORDER_COMPLETED)) {

            employeeActivityService.recordActivity(
                    invoice.getShopId(),
                    invoice.getInvoiceId(),
                    invoice.getId(),
                    user.getUserId(),
                    user.getName(),
                    ActivityType.ORDER_COMPLETED,
                    alreadyAssigned ? "Re-Completed by shop assistant" : "Completed by shop assistant",
                    itemQuantity,
                    itemPrice,
                    alreadyAssigned ? 1 : 0
            );
        }

        // 4. Save the updated invoice
        return invoiceRepository.save(invoice);
    }

    @Override
    public Optional<Invoice> getInvoice(String invoiceId) {
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
        Optional<Invoice> invoice = invoiceRepository.findByInvoiceId(invoiceId);
        if (invoice.isPresent()) {

            if (!shopId.equals(invoice.get().getShopId())) {
                throw new APIException("You are not allowed to view or edit this order");
            }

            if (!invoice.get().getAssignedTailorId().equals(user.getUserId())) {
                throw new APIException("You are not assigned to this order");
            }

        }
        return invoice;
    }

    @Override
    public Invoice getInvoiceWithAssignedItems(String invoiceId) {
        User user = authUtil.loggedInUser();
        String tailorId = user.getUserId();

        Optional<Invoice> invoiceOptional = invoiceRepository
                .findInvoiceWithAssignedItemsExcludingDeliveredForShopAssistant(invoiceId, tailorId);

        if (invoiceOptional.isEmpty()) {
            throw new APIException(
                    "No items assigned to you found in the searched Invoice or invoice is already delivered"
            );
        }

        // The invoice will only contain items assigned to this tailor
        return invoiceOptional.get();
    }

    @Override
    public InvoiceResponse getAllInvoicesOfOnlyOrderAcceptedStatus(Integer page, Integer size, String sortBy, String sortOrder) {
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

        List<String> excludedItems = List.of(
//                "Embroidery",
//                "Lining Material",
//                "Saree Fall",
//                "Saree Pico",
//                "Designing",
                "NoUse"
        );

        List<String> allowedStatuses = List.of(
                "ORDER_ACCEPTED",
                "CUTTING",
                "TAILOR_ASSIGNED",
                "STITCHING",
                "IRONING",
                "ORDER_COMPLETED",
                "ORDER_DELIVERED",
                "ASSIGNED",
                "UNDER_PRODUCTION"
        );

        Sort sort = sortOrder.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

//        Page<Invoice> invoicePage = invoiceRepository
//                .findByShopIdAndItemsItemNameNotInAndOrderStatusIn(shopId, excludedItems, allowedStatuses, pageable);

        Page<Invoice> invoicePage = invoiceRepository
                        .findByShopIdAndItemsItemNameNotInAndOrderStatusInWithAtLeastOneOrderAcceptedItem(shopId, excludedItems, allowedStatuses, pageable);

        if (invoicePage.getContent().isEmpty()) {
            throw new APIException("No Invoices found");
        }

        InvoiceResponse invoiceResponse = new InvoiceResponse();
        invoiceResponse.setContent(invoicePage.getContent());
        invoiceResponse.setPageNumber(invoicePage.getNumber());
        invoiceResponse.setPageSize(invoicePage.getSize());
        invoiceResponse.setTotalElements(invoicePage.getTotalElements());
        invoiceResponse.setTotalPages(invoicePage.getTotalPages());
        invoiceResponse.setLastPage(invoicePage.isLast());

        return invoiceResponse;
    }

    @Override
    public MinimumUserDetailsDTO getMyProfileDetails() {
        User userFromDb = authUtil.loggedInUser();
        MinimumUserDetailsDTO minimumUserDetailsDTO = new MinimumUserDetailsDTO();
        minimumUserDetailsDTO.setName(userFromDb.getName());
        minimumUserDetailsDTO.setAssignedShops(userFromDb.getAssignedShops().stream().map(shop -> modelMapper.map(shop, ShopDTO.class)).toList());

        return minimumUserDetailsDTO;
    }

}

