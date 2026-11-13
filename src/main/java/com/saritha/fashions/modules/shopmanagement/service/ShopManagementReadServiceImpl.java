package com.saritha.fashions.modules.shopmanagement.service;

import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.AppRole;
import com.saritha.fashions.model.User;
import com.saritha.fashions.modules.shopmanagement.model.ManagedShop;
import com.saritha.fashions.modules.shopmanagement.model.PettyCashEntry;
import com.saritha.fashions.modules.shopmanagement.payload.PettyCashHistoryResponse;
import com.saritha.fashions.modules.shopmanagement.payload.ShopNameResponse;
import com.saritha.fashions.modules.shopmanagement.payload.ShopSummaryResponse;
import com.saritha.fashions.modules.shopmanagement.repository.ShopManagementRepository;
import com.saritha.fashions.modules.shopmanagement.repository.ShopManagementRepositoryCustom.PettyCashPage;
import com.saritha.fashions.payload.PaginatedResponse;
import com.saritha.fashions.util.AuthUtil;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Read-side implementation for shop management.
 */
@Service
public class ShopManagementReadServiceImpl implements ShopManagementReadService {

    private static final String SHOPS_COLLECTION = "shops";
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int MAX_PAGE_SIZE = 100;

    private final ShopManagementRepository shopRepository;
    private final MongoTemplate mongoTemplate;
    private final AuthUtil authUtil;

    public ShopManagementReadServiceImpl(ShopManagementRepository shopRepository,
                                         MongoTemplate mongoTemplate,
                                         AuthUtil authUtil) {
        this.shopRepository = shopRepository;
        this.mongoTemplate = mongoTemplate;
        this.authUtil = authUtil;
    }

    /**
     * Returns a single shop by id.
     *
     * @throws APIException "No Shop Found" if the id does not exist
     */
    @Override
    public ManagedShop getById(String shopId) {
        return shopRepository.findById(shopId)
                .orElseThrow(() -> new APIException("No Shop Found"));
    }

    /**
     * Returns every shop in the "shops" collection.
     */
    @Override
    public List<ManagedShop> getAll() {
        return shopRepository.findAll();
    }

    /**
     * Returns a summary of every shop (basic details + pettyCash + totalExpense),
     * sorted by shop name (A-Z).
     *
     * Only the listed fields are requested from MongoDB, so the large arrays on the
     * shop document (allExpense, allInvoice, allReceipts, employees,
     * allPettyCashDetails) are never loaded or resolved. pettyCash and totalExpense
     * are the running totals stored on the shop, so no extra lookup is needed.
     */
    @Override
    public List<ShopSummaryResponse> getAllShopSummaries() {
        Query query = new Query().with(Sort.by(Sort.Direction.ASC, "shopName"));
        query.fields()
                .include("_id")
                .include("shopName")
                .include("shopType")
                .include("gstNumber")
                .include("ownerName")
                .include("shopContactNumber")
                .include("shopAddress")
                .include("shopState")
                .include("pettyCash")
                .include("totalExpense");

        return mongoTemplate.find(query, ShopSummaryResponse.class, SHOPS_COLLECTION);
    }

    /** Replaces GET /v1/api/shops/shop-name. */
    @Override
    public List<ShopNameResponse> getShopNames() {
        return shopRepository.findShopNames().stream()
                .map(shop -> new ShopNameResponse(shop.get("_id").toString(),
                        shop.get("shopName") == null ? null : shop.get("shopName").toString()))
                .toList();
    }

    /**
     * Replaces GET /v1/api/owner/petty-cash/{shopId}/all-pettyCash and /search-by-amount/{term}.
     * Read in one aggregation (the legacy endpoints loaded the whole shop document), with the
     * names of the people who added the cash resolved in one extra query.
     */
    @Override
    public PaginatedResponse<PettyCashHistoryResponse> getPettyCashHistory(String shopId, LocalDate from, LocalDate to,
                                                                           Double amount, int pageNumber, int pageSize) {
        if (pageNumber < 0) {
            throw new APIException("pageNumber must not be negative");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new APIException("pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (from != null && to != null && to.isBefore(from)) {
            throw new APIException("to must not be before from");
        }
        requireShopAccess(shopId);

        Date start = from == null ? null : Date.from(from.atStartOfDay(IST).toInstant());
        Date before = to == null ? null : Date.from(to.plusDays(1).atStartOfDay(IST).toInstant());
        PettyCashPage page = shopRepository.findPettyCashHistory(shopId, start, before, amount, pageNumber, pageSize)
                .orElseThrow(() -> new APIException("Shop Not Found"));

        Map<String, String> names = shopRepository.findUserNames(
                page.entries().stream().map(PettyCashEntry::getAddedBy).toList());
        List<PettyCashHistoryResponse> content = page.entries().stream()
                .map(entry -> new PettyCashHistoryResponse(entry.getAmount(), entry.getAddingDate(),
                        entry.getAddedBy(), names.get(entry.getAddedBy())))
                .toList();

        int totalPages = (int) Math.ceil((double) page.total() / pageSize);
        return new PaginatedResponse<>(content, pageNumber, pageSize, page.total(), totalPages,
                pageNumber >= totalPages - 1);
    }

    /** Platform OWNER: any shop. SHOP_OWNER: only the shop whose employeeIds contains them. */
    private void requireShopAccess(String shopId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Login required");
        }
        User user = authUtil.loggedInUser();
        boolean platformOwner = hasRole(user, AppRole.OWNER);
        if (platformOwner) {
            return;
        }
        boolean ownShop = hasRole(user, AppRole.SHOP_OWNER)
                && shopRepository.findShopIdOfEmployee(user.getUserId()).map(shopId::equals).orElse(false);
        if (!ownShop) {
            throw new AccessDeniedException("You can only view your own shop's petty cash");
        }
    }

    private static boolean hasRole(User user, AppRole role) {
        return user.getRoles() != null && user.getRoles().stream()
                .filter(Objects::nonNull).anyMatch(r -> r.getRoleName() == role);
    }
}
