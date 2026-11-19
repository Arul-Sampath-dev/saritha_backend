package com.saritha.fashions.modules.expense.service;

import com.saritha.fashions.modules.core.payment.PaymentMethods;
import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.AppRole;
import com.saritha.fashions.model.User;
import com.saritha.fashions.modules.expense.model.ExpenseItem;
import com.saritha.fashions.modules.expense.model.ExpenseRecord;
import com.saritha.fashions.modules.expense.model.TravelExpenseItem;
import com.saritha.fashions.modules.expense.payload.CreateEmployeeExpenseRequest;
import com.saritha.fashions.modules.expense.payload.CreateExpenseRequest;
import com.saritha.fashions.modules.expense.payload.ExpenseItemRequest;
import com.saritha.fashions.modules.expense.payload.ExpenseResponse;
import com.saritha.fashions.modules.expense.payload.TravelItemRequest;
import com.saritha.fashions.modules.expense.payload.UpdateExpenseRequest;
import com.saritha.fashions.modules.expense.repository.ExpenseRecordRepository;
import com.saritha.fashions.modules.expense.repository.ExpenseRecordRepositoryCustom.ShopExpenseBalances;
import com.saritha.fashions.util.AuthUtil;
import com.saritha.fashions.util.DateTimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Write-side implementation for expense management.
 * Ported from ExpenseServiceImpl.addExpense / addEmployeeExpense.
 *
 * Both expense types are entered by the SHOP OWNER only:
 * - Shop expense:     stored WITHOUT a userId (it belongs to the shop).
 * - Employee expense: stored with userId = the employee's user id from the request;
 *                     the employee must belong to the shop owner's shop.
 *
 * Flow for both endpoints:
 * 1. Require a logged-in SHOP_OWNER and find their shop (shops.employeeIds).
 * 2. Employee expense only: check the employee is in that shop's employeeIds.
 * 3. Save the expense (shopId and userId are set on the server).
 * 4. Apply it to the shop in ONE atomic update: link it in allExpense, add to
 *    totalExpense and, for cash, subtract from pettyCash only if the balance is enough.
 * 5. If step 4 does not apply (not enough petty cash), the saved expense is deleted
 *    again so no orphan expense is left behind.
 *
 * MongoDB transactions are not configured in this project, so step 5 is a manual
 * compensation instead of a @Transactional rollback.
 */
@Service
public class ExpenseWriteServiceImpl implements ExpenseWriteService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseWriteServiceImpl.class);

    private static final String CASH = "Cash";
    private static final int MAX_REASON_LENGTH = 300;

    private final ExpenseRecordRepository expenseRepository;
    private final AuthUtil authUtil;

    public ExpenseWriteServiceImpl(ExpenseRecordRepository expenseRepository,
                                             AuthUtil authUtil) {
        this.expenseRepository = expenseRepository;
        this.authUtil = authUtil;
    }

    /**
     * Shop expense: no userId is stored.
     */
    @Override
    public ExpenseResponse createExpense(CreateExpenseRequest request) {
        return recordExpense(buildBaseExpense(request), null);
    }

    /**
     * Employee expense: same as a shop expense, plus userId = the employee's id.
     */
    @Override
    public ExpenseResponse createEmployeeExpense(CreateEmployeeExpenseRequest request) {
        return recordExpense(buildBaseExpense(request), request.getEmployeeUserId().trim());
    }

    /**
     * Shared save + apply-to-shop logic for both expense types.
     *
     * @param employeeUserId the employee the expense is for, or null for a shop expense
     */
    private ExpenseResponse recordExpense(ExpenseRecord expense, String employeeUserId) {
        String shopId = resolveShopOwnerShopId();

        if (employeeUserId != null && !expenseRepository.isEmployeeOfShop(shopId, employeeUserId)) {
            throw new APIException("Employee does not belong to your shop");
        }

        expense.setShopId(shopId);
        expense.setUserId(employeeUserId); // null for a shop expense

        ExpenseRecord savedExpense = expenseRepository.save(expense);
        boolean isCash = CASH.equalsIgnoreCase(savedExpense.getPaymentMethod());

        ShopExpenseBalances balances;
        try {
            balances = expenseRepository.applyExpenseToShop(
                            shopId, savedExpense.getId(), savedExpense.getExpenseAmount(), isCash)
                    .orElse(null);
        } catch (RuntimeException exception) {
            expenseRepository.deleteById(savedExpense.getId());
            throw exception;
        }

        if (balances == null) {
            expenseRepository.deleteById(savedExpense.getId());
            throw new APIException(isCash
                    ? "You do not have sufficient petty cash to spend, please contact owner to provide petty cash"
                    : "Shop Not Found");
        }

        return toResponse(savedExpense, balances);
    }

    /**
     * Deletes an expense (shop or employee) of the shop owner's shop and reverses its
     * effect on the shop. Ported from ExpenseServiceImpl.deleteExpense.
     *
     * Steps:
     * 1. Require a logged-in SHOP_OWNER and find their shop.
     * 2. Atomically delete the expense, only if it belongs to that shop. If two delete
     *    requests race, only one gets the expense, so the refund happens only once.
     * 3. Atomically reverse it on the shop: unlink from allExpense, subtract from
     *    totalExpense (never below 0) and, for cash, add the amount back to pettyCash.
     * 4. If step 3 fails, the expense is restored so data stays consistent.
     *
     * @throws APIException "Expense not found" if it does not exist or belongs to
     *                      another shop (the same message for both, so ids of other
     *                      shops' expenses are not revealed)
     */
    @Override
    public void deleteExpense(String expenseId) {
        deleteExpense(expenseId, null);
    }

    /**
     * Deletes an expense with an optional note. The expense is first copied to the deleted_expenses archive (who,
     * when, why), then removed, then its effect on the shop is reversed (totalExpense, and petty cash for Cash).
     * If two deletes race, only one removes the expense; the other's archive copy is removed again. If reversing
     * fails, the expense is restored and its archive copy removed, so nothing is lost or counted twice.
     */
    @Override
    public void deleteExpense(String expenseId, String reason) {
        User shopOwner = requireShopOwner();
        String shopId = expenseRepository.findShopIdByEmployeeId(shopOwner.getUserId())
                .orElseThrow(() -> new APIException("User is not assigned to any shop"));
        String note = reason == null || reason.isBlank() ? null : reason.trim();
        if (note != null && note.length() > MAX_REASON_LENGTH) {
            throw new APIException("reason must be at most " + MAX_REASON_LENGTH + " characters");
        }

        ExpenseRecord existing = expenseRepository.findByIdAndShopId(expenseId, shopId)
                .orElseThrow(() -> new APIException("Expense not found"));
        String archiveId = expenseRepository.archiveExpense(existing, shopOwner.getUserId(), shopOwner.getName(),
                note, DateTimeUtils.getNowInIST());

        ExpenseRecord deletedExpense = expenseRepository.removeExpenseOfShop(expenseId, shopId).orElse(null);
        if (deletedExpense == null) {
            expenseRepository.removeArchivedExpense(archiveId); // deleted meanwhile by another request
            throw new APIException("Expense not found");
        }

        boolean isCash = CASH.equalsIgnoreCase(deletedExpense.getPaymentMethod());
        boolean reverted;
        try {
            reverted = expenseRepository.revertExpenseFromShop(
                    shopId, deletedExpense.getId(), deletedExpense.getExpenseAmount(), isCash).isPresent();
        } catch (RuntimeException exception) {
            expenseRepository.save(deletedExpense); // put it back
            expenseRepository.removeArchivedExpense(archiveId);
            throw exception;
        }
        if (!reverted) {
            expenseRepository.save(deletedExpense); // put it back
            expenseRepository.removeArchivedExpense(archiveId);
            throw new APIException("Shop Not Found");
        }

        log.info("Deleted expense {} of shop {} (amount {}, cash refunded: {}, archived as {})",
                deletedExpense.getId(), shopId, deletedExpense.getExpenseAmount(), isCash, archiveId);
    }

    /**
     * Updates an expense of the shop owner's shop: amount, notes, category, date, payment method, and for an
     * employee expense the employee.
     *
     * Money effect: totalExpense changes by (new amount - old amount); petty cash changes by the cash part
     * (amount if the method is Cash, else 0) before vs after. So a Cash -> UPI change gives the cash back to petty
     * cash, UPI -> Cash takes it out (refused when petty cash is too low).
     *
     * Steps: apply the money effect to the shop atomically, then update the expense only if its amount and payment
     * method are still the ones read; if another request changed or deleted it meanwhile, the shop change is undone
     * and the caller is asked to retry.
     */
    @Override
    public ExpenseResponse updateExpense(String expenseId, UpdateExpenseRequest request) {
        String shopId = resolveShopOwnerShopId();

        ExpenseRecord existing = expenseRepository.findByIdAndShopId(expenseId, shopId)
                .orElseThrow(() -> new APIException("Expense not found"));

        double oldAmount = existing.getExpenseAmount();
        String oldMethod = existing.getPaymentMethod();
        double newAmount = request.getExpenseAmount() != null ? request.getExpenseAmount() : oldAmount;
        String newMethod = request.getPaymentMethod() != null ? normalisePaymentMethod(request.getPaymentMethod()) : oldMethod;

        Map<String, Object> changes = new LinkedHashMap<>();
        if (Double.compare(newAmount, oldAmount) != 0) {
            changes.put("expenseAmount", newAmount);
        }
        if (request.getPaymentMethod() != null && !Objects.equals(newMethod, oldMethod)) {
            changes.put("paymentMethod", newMethod);
        }
        if (request.getNotes() != null) {
            changes.put("notes", request.getNotes());
        }
        if (request.getExpenseType() != null) {
            changes.put("expenseType", request.getExpenseType().trim());
        }
        if (request.getExpenseDate() != null) {
            changes.put("expenseDate", request.getExpenseDate());
        }
        if (request.getEmployeeUserId() != null) {
            String employeeId = request.getEmployeeUserId().trim();
            if (existing.getUserId() == null) {
                throw new APIException("This is a shop expense; it has no employee to change");
            }
            if (!expenseRepository.isEmployeeOfShop(shopId, employeeId)) {
                throw new APIException("Employee not found in your shop");
            }
            changes.put("userId", employeeId);
        }
        if (changes.isEmpty()) {
            return toResponse(existing, expenseRepository.findShopBalances(shopId)
                    .orElseThrow(() -> new APIException("Shop Not Found")));
        }

        double totalDelta = newAmount - oldAmount;
        double cashOut = cashPart(newMethod, newAmount) - cashPart(oldMethod, oldAmount);
        boolean touchesShop = Double.compare(totalDelta, 0.0) != 0 || Double.compare(cashOut, 0.0) != 0;

        ShopExpenseBalances balances = null;
        if (touchesShop) {
            balances = expenseRepository.adjustShopBalances(shopId, totalDelta, cashOut, true)
                    .orElseThrow(() -> new APIException(cashOut > 0
                            ? "You do not have sufficient petty cash to spend, please contact owner to provide petty cash"
                            : "Shop Not Found"));
        }

        ExpenseRecord updated;
        try {
            updated = expenseRepository.updateExpenseIfUnchanged(expenseId, shopId, oldAmount, oldMethod, changes)
                    .orElse(null);
        } catch (RuntimeException exception) {
            undoShopAdjustment(touchesShop, shopId, totalDelta, cashOut);
            throw exception;
        }
        if (updated == null) {
            undoShopAdjustment(touchesShop, shopId, totalDelta, cashOut);
            throw new APIException("This expense was changed or deleted by another request, please reload and try again");
        }

        if (balances == null) {
            balances = expenseRepository.findShopBalances(shopId)
                    .orElseThrow(() -> new APIException("Shop Not Found"));
        }
        log.info("Updated expense {} of shop {}: {} (amount {} -> {}, method {} -> {})",
                expenseId, shopId, changes.keySet(), oldAmount, newAmount, oldMethod, newMethod);
        return toResponse(updated, balances);
    }

    /** The part of an expense paid from petty cash: its amount when the method is Cash, else 0. */
    private static double cashPart(String paymentMethod, double amount) {
        return CASH.equalsIgnoreCase(paymentMethod == null ? null : paymentMethod.trim()) ? amount : 0.0;
    }

    /** Reverses a shop adjustment made by updateExpense (no petty cash check). */
    private void undoShopAdjustment(boolean touchesShop, String shopId, double totalDelta, double cashOut) {
        if (touchesShop) {
            expenseRepository.adjustShopBalances(shopId, -totalDelta, -cashOut, false);
        }
    }

    /**
     * Checks the caller is a SHOP_OWNER and returns the id of their shop.
     */
    private String resolveShopOwnerShopId() {
        User shopOwner = requireShopOwner();
        return expenseRepository.findShopIdByEmployeeId(shopOwner.getUserId())
                .orElseThrow(() -> new APIException("User is not assigned to any shop"));
    }

    /**
     * Builds the fields common to both expense types, including the optional
     * refreshment / miscellaneous / travel line items. Payment method defaults to
     * "Cash" (any casing of "cash" is normalised to "Cash"); date defaults to now (IST).
     */
    private ExpenseRecord buildBaseExpense(CreateExpenseRequest request) {
        return ExpenseRecord.builder()
                .expenseType(request.getExpenseType().trim())
                .expenseAmount(request.getExpenseAmount())
                .paymentMethod(normalisePaymentMethod(request.getPaymentMethod()))
                .notes(request.getNotes())
                .expenseDate(request.getExpenseDate() != null
                        ? request.getExpenseDate()
                        : DateTimeUtils.getNowInIST())
                .refreshmentDetails(mapList(request.getRefreshmentDetails(), this::toExpenseItem))
                .miscellaneousDetails(mapList(request.getMiscellaneousDetails(), this::toExpenseItem))
                .travelDetails(mapList(request.getTravelDetails(), this::toTravelItem))
                .build();
    }

    /** Blank -> Cash; a known spelling -> its canonical name (one payment-method list); unknown -> 400. */
    private static String normalisePaymentMethod(String paymentMethod) {
        return PaymentMethods.require(paymentMethod, CASH);
    }

    /**
     * Returns the logged-in user if they are a SHOP_OWNER; otherwise throws
     * AccessDeniedException (403). WebSecurityConfig already limits the routes to
     * SHOP_OWNER; this keeps the service safe on its own.
     */
    private User requireShopOwner() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Login required to record an expense");
        }

        User user = authUtil.loggedInUser();
        boolean isShopOwner = user.getRoles() != null && user.getRoles().stream()
                .anyMatch(role -> role.getRoleName() == AppRole.SHOP_OWNER);
        if (!isShopOwner) {
            throw new AccessDeniedException("Only the shop owner can record expenses");
        }
        return user;
    }

    private ExpenseItem toExpenseItem(ExpenseItemRequest item) {
        return ExpenseItem.builder()
                .amount(item.getAmount())
                .description(item.getDescription())
                .build();
    }

    private TravelExpenseItem toTravelItem(TravelItemRequest item) {
        return TravelExpenseItem.builder()
                .date(item.getDate())
                .source(item.getSource())
                .destination(item.getDestination())
                .amount(item.getAmount())
                .description(item.getDescription())
                .build();
    }

    private static <S, T> List<T> mapList(List<S> source, Function<S, T> mapper) {
        if (source == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(source.stream().map(mapper).toList());
    }

    private static ExpenseResponse toResponse(ExpenseRecord expense, ShopExpenseBalances balances) {
        return ExpenseResponse.builder()
                .id(expense.getId())
                .shopId(expense.getShopId())
                .userId(expense.getUserId())
                .expenseType(expense.getExpenseType())
                .expenseAmount(expense.getExpenseAmount())
                .paymentMethod(expense.getPaymentMethod())
                .notes(expense.getNotes())
                .expenseDate(expense.getExpenseDate())
                .refreshmentDetails(expense.getRefreshmentDetails())
                .miscellaneousDetails(expense.getMiscellaneousDetails())
                .travelDetails(expense.getTravelDetails())
                .pettyCashBalance(balances.pettyCash())
                .shopTotalExpense(balances.totalExpense())
                .build();
    }
}
