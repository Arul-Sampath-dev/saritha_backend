# Tailor Master Invoice Availability and Conditions

This document details the accessibility rules, filtering criteria, and MongoDB queries governing which invoices are visible and actionable for users with the `TAILOR_MASTER` role in the Saritha Fashion Backend.

---

## 1. Overview

**Are all invoices available for the Tailor Master?**  
**No.** Tailor Masters do not have global access to all invoices. Invoices are strictly scoped to the Tailor Master's assigned shop, and filtered based on the order progress status, item status, and delivery lifecycle. Invoices that are already delivered (`ORDER_DELIVERED`) or have progressed beyond assignment stages are intentionally excluded.

---

## 2. Endpoint-by-Endpoint Filtering Breakdown

### 2.1. Active Invoices for Tailor Master
- **Endpoint:** `GET /v1/api/tailor-master/shop/all-invoice`
- **Controller:** [`TailorAssignmentController.java`](file:///C:/Users/ARUL%20S/Downloads/SarithaFashion_Backend/SarithaFashion_Backend/src/main/java/com/saritha/fashions/controller/TailorAssignmentController.java#L51-L64)
- **Service Implementation:** [`InvoiceServiceImpl.java` (L1347–L1383)](file:///C:/Users/ARUL%20S/Downloads/SarithaFashion_Backend/SarithaFashion_Backend/src/main/java/com/saritha/fashions/service/InvoiceServiceImpl.java#L1347-L1383)

#### MongoDB Aggregation Match Filter:
```java
List<String> excludedItems = List.of("NoUse");
List<String> allowedStatuses = List.of(
        "CUTTING", "TAILOR_ASSIGNED", "ORDER_ACCEPTED"
);

Criteria matchCriteria = Criteria.where("shopId").is(shopId)
        .and("items.itemName").nin(excludedItems)
        .and("items.itemOrderStatus").in(allowedStatuses);

if (startDate != null && endDate != null) {
    matchCriteria = matchCriteria.and("invoiceDate").gte(start).lt(end);
}
```

#### Enforced Conditions:
1. **Shop Scoping (`shopId`):** The Tailor Master only accesses invoices belonging to their shop (resolved via `employeeIds` containing the authenticated `userId`).
2. **Item Status (`itemOrderStatus`):** Only invoices containing items in one of the following statuses are matched:
   - `ORDER_ACCEPTED`
   - `CUTTING`
   - `TAILOR_ASSIGNED`
   *(Invoices whose items are in `STITCHING`, `COMPLETED`, or `ORDER_DELIVERED` will not appear).*
3. **Excluded Item Names (`itemName`):** Items with the name `"NoUse"` are explicitly excluded.
4. **Date Filtering:** Optional `startDate` and `endDate` parameters query against `invoiceDate`.

---

### 2.2. Unassigned / Order Accepted Invoices
- **Endpoint:** `GET /v1/api/tailor-master/shop/order-accepted-status/all-invoice`
- **Controller:** [`TailorAssignmentController.java`](file:///C:/Users/ARUL%20S/Downloads/SarithaFashion_Backend/SarithaFashion_Backend/src/main/java/com/saritha/fashions/controller/TailorAssignmentController.java#L66-L79)
- **Service Implementation:** [`TailorOrderServiceImpl.java` (L661–L701)](file:///C:/Users/ARUL%20S/Downloads/SarithaFashion_Backend/SarithaFashion_Backend/src/main/java/com/saritha/fashions/service/TailorOrderServiceImpl.java#L661-L701)

#### MongoDB Aggregation Match Filter:
```java
List<String> excludedItems = List.of("NoUse");
List<String> allowedStatuses = List.of(
        "ORDER_ACCEPTED",
        "ASSIGNED"
);

Criteria matchCriteria = Criteria.where("shopId").is(shopId)
        .and("items.itemName").nin(excludedItems)
        .and("items.itemOrderStatus").in(allowedStatuses);
```

#### Enforced Conditions:
1. **Shop Scoping (`shopId`):** Restricted to the Tailor Master's shop.
2. **Initial Status Only:** Specifically limited to orders ready for cutting/assignment (`ORDER_ACCEPTED` and `ASSIGNED`).

---

### 2.3. View Specific Invoice Details
- **Endpoint:** `GET /v1/api/tailor-master/view-order/{invoiceId}`
- **Controller:** [`TailorAssignmentController.java`](file:///C:/Users/ARUL%20S/Downloads/SarithaFashion_Backend/SarithaFashion_Backend/src/main/java/com/saritha/fashions/controller/TailorAssignmentController.java#L118-L133)
- **Service Implementation:** [`InvoiceServiceImpl.java` (L1294–L1321)](file:///C:/Users/ARUL%20S/Downloads/SarithaFashion_Backend/SarithaFashion_Backend/src/main/java/com/saritha/fashions/service/InvoiceServiceImpl.java#L1294-L1321)
- **Repository Query:** [`InvoiceRepository.java` (L154–L164)](file:///C:/Users/ARUL%20S/Downloads/SarithaFashion_Backend/SarithaFashion_Backend/src/main/java/com/saritha/fashions/repository/InvoiceRepository.java#L154-L164)

#### Aggregation Pipeline:
```json
{
  "$match": {
    "invoiceId": ?0,
    "orderStatus": { "$ne": "ORDER_DELIVERED" },
    "shopId": ?1
  }
}
```

#### Enforced Conditions:
1. **Delivered Orders Hidden:** If `orderStatus == "ORDER_DELIVERED"`, the repository returns empty, and the service throws:
   > `APIException("No items assigned to you found in the searched Invoice or invoice is already delivered")`
2. **Shop Match:** The invoice must match the shop of the Tailor Master.

---

### 2.4. Shop Assigned Invoices
- **Endpoint:** `GET /v1/api/tailor-master/tailor/`
- **Controller:** [`TailorAssignmentController.java`](file:///C:/Users/ARUL%20S/Downloads/SarithaFashion_Backend/SarithaFashion_Backend/src/main/java/com/saritha/fashions/controller/TailorAssignmentController.java#L93-L101)
- **Service Implementation:** [`TailorAssignmentServiceImpl.java` (L93–L129)](file:///C:/Users/ARUL%20S/Downloads/SarithaFashion_Backend/SarithaFashion_Backend/src/main/java/com/saritha/fashions/service/TailorAssignmentServiceImpl.java#L93-L129)

#### Enforced Conditions:
- Returns paginated invoices matching `invoiceRepository.findByShopId(shopId, pageable)`.
- Scoped to the Tailor Master's current shop.

---

### 2.5. Assigning Tailors to Invoice Items
- **Endpoint:** `POST /v1/api/tailor-master/assign-tailor`
- **Controller:** [`TailorAssignmentController.java`](file:///C:/Users/ARUL%20S/Downloads/SarithaFashion_Backend/SarithaFashion_Backend/src/main/java/com/saritha/fashions/controller/TailorAssignmentController.java#L141-L144)
- **Service Implementation:** [`InvoiceServiceImpl.java` (L120–L250)](file:///C:/Users/ARUL%20S/Downloads/SarithaFashion_Backend/SarithaFashion_Backend/src/main/java/com/saritha/fashions/service/InvoiceServiceImpl.java#L120-L250)

#### Enforced Conditions:
- The target invoice and assigned tailor must both belong to the Tailor Master's shop.
- The item cannot be re-assigned if already assigned unless unassigned first.

---

## 3. Condition Summary Matrix

| Condition / Dimension | Rule Applied | Reason |
|---|---|---|
| **Shop Isolation** | Filtered by `shopId` of the authenticated user | Tailor Masters cannot view or interact with invoices from any other shop branch. |
| **Lifecycle / Stage** | Restricted to early stages (`CUTTING`, `ORDER_ACCEPTED`, `TAILOR_ASSIGNED`, `ASSIGNED`) | Tailor Masters only need visibility during cutting and assignment to tailors. |
| **Delivered Status** | Invoices with `orderStatus == "ORDER_DELIVERED"` are excluded | Once delivered to the customer, tailoring workflows are closed. |
| **Item Level Exclusions** | Items with `itemName == "NoUse"` are omitted | System cleanup/dummy items are filtered out. |
| **Date Range Filter** | Optional `startDate` and `endDate` on `invoiceDate` | Allows filtering within active reporting or work periods. |
