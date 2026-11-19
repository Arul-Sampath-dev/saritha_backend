package com.saritha.fashions.modules.expense.service;

import org.bson.Document;
import com.saritha.fashions.util.AmountUtils;
import com.saritha.fashions.modules.expense.repository.ExpenseRecordRepositoryCustom.GroupTotal;
import com.saritha.fashions.modules.expense.repository.ExpenseRecordRepositoryCustom.ExpenseTotals;
import com.saritha.fashions.modules.expense.repository.ExpenseRecordRepositoryCustom.ExpenseFilter;
import com.saritha.fashions.modules.expense.repository.ExpenseRecordRepositoryCustom.DeletedExpensePage;
import com.saritha.fashions.modules.expense.payload.ExpenseSummaryResponse;
import com.saritha.fashions.modules.expense.payload.DeletedExpenseResponse;
import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.AppRole;
import com.saritha.fashions.model.User;
import com.saritha.fashions.modules.expense.model.ExpenseRecord;
import com.saritha.fashions.modules.expense.payload.ExpenseResponse;
import com.saritha.fashions.modules.expense.repository.ExpenseRecordRepository;
import com.saritha.fashions.modules.expense.repository.ExpenseRecordRepositoryCustom.ExpensePage;
import com.saritha.fashions.payload.PaginatedResponse;
import com.saritha.fashions.util.AuthUtil;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Expense lists and details. Replaces GET /v1/api/shops/view-expenses (+ /{shopId},
 * /byPaymentMethod), GET /v1/api/expenses/shop/{shopId} and GET /v1/api/expenses/all.
 *
 * Access: a SHOP_OWNER always sees their own shop (any shopId parameter is ignored); the
 * platform OWNER must name the shop with shopId.
 *
 * Fixes vs the legacy search: the text is matched literally (the legacy search compiled the
 * user's text as a regex, so "(" or "*" broke it) and also searches notes; the payment-method
 * filter is exact and case-insensitive; an empty page is an empty list, not an error.
 */
@Service
public class ExpenseReadServiceImpl implements ExpenseReadService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int MAX_PAGE_SIZE = 100;

    private final ExpenseRecordRepository repository;
    private final AuthUtil authUtil;

    public ExpenseReadServiceImpl(ExpenseRecordRepository repository, AuthUtil authUtil) {
        this.repository = repository;
        this.authUtil = authUtil;
    }

    @Override
    public PaginatedResponse<ExpenseResponse> listExpenses(String shopId, LocalDate from, LocalDate to, String search,
                                                           String paymentMethod, String type, String employeeUserId,
                                                           String expenseType, String sort, int pageNumber, int pageSize) {
        if (pageNumber < 0) {
            throw new APIException("pageNumber must not be negative");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new APIException("pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (from != null && to != null && to.isBefore(from)) {
            throw new APIException("to must not be before from");
        }
        Boolean employeeOnly = switch (type == null ? "" : type.trim().toLowerCase(Locale.ROOT)) {
            case "" -> null;
            case "shop" -> false;
            case "employee" -> true;
            default -> throw new APIException("type must be shop or employee");
        };
        boolean oldestFirst = switch (sort == null ? "newest" : sort.trim().toLowerCase(Locale.ROOT)) {
            case "newest" -> false;
            case "oldest" -> true;
            default -> throw new APIException("sort must be newest or oldest");
        };

        String scope = resolveShop(shopId);
        ExpensePage page = repository.findExpenses(filter(scope, from, to, search, paymentMethod, employeeOnly,
                employeeUserId, expenseType), oldestFirst, pageNumber, pageSize);

        Map<String, String> names = repository.findUserNames(
                page.expenses().stream().map(ExpenseRecord::getUserId).filter(Objects::nonNull).toList());
        List<ExpenseResponse> content = page.expenses().stream()
                .map(expense -> toResponse(expense, names.get(expense.getUserId())))
                .toList();
        int totalPages = (int) Math.ceil((double) page.total() / pageSize);
        return new PaginatedResponse<>(content, pageNumber, pageSize, page.total(), totalPages,
                pageNumber >= totalPages - 1);
    }

    @Override
    public ExpenseSummaryResponse getExpenseSummary(String shopId, LocalDate from, LocalDate to, String search,
                                                    String paymentMethod, String type, String employeeUserId,
                                                    String expenseType) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new APIException("to must not be before from");
        }
        String scope = resolveShop(shopId);
        ExpenseTotals totals = repository.sumExpenses(filter(scope, from, to, search, paymentMethod, kind(type),
                employeeUserId, expenseType));
        return new ExpenseSummaryResponse(totals.count(), round(totals.amount()), round(totals.shopExpenses()),
                round(totals.employeeExpenses()), groups(totals.byType()), groups(totals.byPaymentMethod()));
    }

    @Override
    public PaginatedResponse<DeletedExpenseResponse> listDeletedExpenses(String shopId, LocalDate from, LocalDate to,
                                                                         String search, String type,
                                                                         int pageNumber, int pageSize) {
        if (pageNumber < 0) {
            throw new APIException("pageNumber must not be negative");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new APIException("pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (from != null && to != null && to.isBefore(from)) {
            throw new APIException("to must not be before from");
        }
        String scope = resolveShop(shopId);
        DeletedExpensePage page = repository.findDeletedExpenses(scope,
                from == null ? null : Date.from(from.atStartOfDay(IST).toInstant()),
                to == null ? null : Date.from(to.plusDays(1).atStartOfDay(IST).toInstant()),
                blankToNull(search), kind(type), pageNumber, pageSize);

        List<String> employeeIds = page.archives().stream()
                .map(archive -> ((Document) archive.get("expense")).getString("userId"))
                .filter(Objects::nonNull).toList();
        Map<String, String> names = repository.findUserNames(employeeIds);
        List<DeletedExpenseResponse> content = page.archives().stream().map(archive -> {
            Document expense = (Document) archive.get("expense");
            String userId = expense.getString("userId");
            return new DeletedExpenseResponse(
                    archive.getObjectId("_id").toHexString(), archive.getString("expenseId"), archive.getString("shopId"),
                    userId, userId == null ? null : names.get(userId), expense.getString("expenseType"),
                    expense.get("expenseAmount") instanceof Number amount ? amount.doubleValue() : 0.0,
                    expense.getString("paymentMethod"), expense.getString("notes"), expense.getDate("expenseDate"),
                    archive.getDate("deletedAt"), archive.getString("deletedBy"), archive.getString("deletedByName"),
                    archive.getString("reason"));
        }).toList();
        int totalPages = (int) Math.ceil((double) page.total() / pageSize);
        return new PaginatedResponse<>(content, pageNumber, pageSize, page.total(), totalPages,
                pageNumber >= totalPages - 1);
    }

    private static ExpenseFilter filter(String scope, LocalDate from, LocalDate to, String search, String paymentMethod,
                                        Boolean employeeOnly, String employeeUserId, String expenseType) {
        return new ExpenseFilter(scope,
                from == null ? null : Date.from(from.atStartOfDay(IST).toInstant()),
                to == null ? null : Date.from(to.plusDays(1).atStartOfDay(IST).toInstant()),
                blankToNull(search), blankToNull(paymentMethod), employeeOnly, blankToNull(employeeUserId),
                blankToNull(expenseType));
    }

    /** "shop" / "employee" / blank -> false / true / null. */
    private static Boolean kind(String type) {
        return switch (type == null ? "" : type.trim().toLowerCase(Locale.ROOT)) {
            case "" -> null;
            case "shop" -> false;
            case "employee" -> true;
            default -> throw new APIException("type must be shop or employee");
        };
    }

    private static List<ExpenseSummaryResponse.Group> groups(List<GroupTotal> rows) {
        return rows.stream().map(row -> new ExpenseSummaryResponse.Group(row.key(), round(row.amount()), row.count())).toList();
    }

    private static double round(double value) {
        return AmountUtils.round(value);
    }

    @Override
    public ExpenseResponse getExpense(String expenseId, String shopId) {
        String scope = resolveShop(shopId);
        ExpenseRecord expense = repository.findByIdAndShopId(expenseId, scope)
                .orElseThrow(() -> new APIException("Expense not found"));
        String name = expense.getUserId() == null ? null
                : repository.findUserNames(List.of(expense.getUserId())).get(expense.getUserId());
        return toResponse(expense, name);
    }

    /** Shop owner: their own shop. Platform owner: the requested shop (must exist). */
    private String resolveShop(String requestedShopId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Login required");
        }
        User user = authUtil.loggedInUser();
        if (hasRole(user, AppRole.SHOP_OWNER)) {
            return repository.findShopIdByEmployeeId(user.getUserId())
                    .orElseThrow(() -> new APIException("User is not assigned to any shop"));
        }
        if (hasRole(user, AppRole.OWNER)) {
            String shopId = blankToNull(requestedShopId);
            if (shopId == null) {
                throw new APIException("shopId is required");
            }
            if (repository.findShopBalances(shopId).isEmpty()) {
                throw new APIException("Shop Not Found");
            }
            return shopId;
        }
        throw new AccessDeniedException("Only shop owners and the platform owner can view expenses");
    }

    private static ExpenseResponse toResponse(ExpenseRecord expense, String employeeName) {
        return ExpenseResponse.builder()
                .id(expense.getId())
                .shopId(expense.getShopId())
                .userId(expense.getUserId())
                .employeeName(employeeName)
                .expenseType(expense.getExpenseType())
                .expenseAmount(expense.getExpenseAmount())
                .paymentMethod(expense.getPaymentMethod())
                .notes(expense.getNotes())
                .expenseDate(expense.getExpenseDate())
                .refreshmentDetails(expense.getRefreshmentDetails())
                .miscellaneousDetails(expense.getMiscellaneousDetails())
                .travelDetails(expense.getTravelDetails())
                .build();
    }

    private static boolean hasRole(User user, AppRole role) {
        return user.getRoles() != null && user.getRoles().stream()
                .filter(Objects::nonNull).anyMatch(r -> r.getRoleName() == role);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
