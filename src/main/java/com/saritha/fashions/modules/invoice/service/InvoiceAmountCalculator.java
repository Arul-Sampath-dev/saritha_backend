package com.saritha.fashions.modules.invoice.service;

import com.saritha.fashions.modules.invoice.exception.InvoiceRequestValidationException;
import com.saritha.fashions.modules.invoice.payload.InvoiceItemRequest;
import com.saritha.fashions.util.AmountUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure amount / GST calculation for an invoice. No database access.
 *
 * Rules (same rates as the old InvoiceServiceImpl.generateInvoice):
 * - totalAmount       = Σ itemPrice × itemQuantity
 * - taxableAmount     = totalAmount − discount
 * - same state        → CGST 2.5% + SGST 2.5%; different state → IGST 5%
 * - totalAmountWithGST = taxableAmount + GST
 * - balanceDue        = totalAmountWithGST − advancePaid
 * - paymentStatus     = "Dues" if nothing paid, "Partial" if balanceDue > 0, else "Paid"
 *
 * Fix vs. old code: paymentStatus is now based on balanceDue (with GST). The old code
 * compared the pre-GST total, so a full pre-GST payment showed "Paid" with a balance due.
 * State comparison ignores case and spaces, so "Tamil Nadu" equals "TamilNadu".
 */
@Component
public class InvoiceAmountCalculator {

    static final double CGST_RATE = 2.5;
    static final double SGST_RATE = 2.5;
    static final double IGST_RATE = 5.0;
    static final String DEFAULT_SHOP_STATE = "TamilNadu";

    public static final String STATUS_DUES = "Dues";
    public static final String STATUS_PARTIAL = "Partial";
    public static final String STATUS_PAID = "Paid";

    public InvoiceAmounts calculate(List<InvoiceItemRequest> items,
                                    Double clientTotalAmount,
                                    Double discountInput,
                                    Double advancePaidInput,
                                    String customerState,
                                    String shopState) {
        BigDecimal total = BigDecimal.ZERO;
        for (InvoiceItemRequest item : items) {
            total = total.add(BigDecimal.valueOf(item.getItemPrice())
                    .multiply(BigDecimal.valueOf(item.getItemQuantity())));
        }
        double totalAmount = AmountUtils.round(total.doubleValue());
        double discount = AmountUtils.round(discountInput == null ? 0.0 : discountInput);
        double advancePaid = AmountUtils.round(advancePaidInput == null ? 0.0 : advancePaidInput);

        Map<String, String> errors = new LinkedHashMap<>();
        if (clientTotalAmount != null && Math.abs(clientTotalAmount - totalAmount) > 0.01) {
            errors.put("totalAmount", "totalAmount " + clientTotalAmount
                    + " does not match the items total " + totalAmount);
        }
        if (discount > totalAmount) {
            errors.put("discount", "discount must not exceed the total amount " + totalAmount);
        }
        if (!errors.isEmpty()) {
            throw new InvoiceRequestValidationException(errors);
        }

        double taxableAmount = AmountUtils.round(totalAmount - discount);
        boolean intraState = normaliseState(customerState)
                .equals(normaliseState(shopState == null || shopState.isBlank() ? DEFAULT_SHOP_STATE : shopState));

        double cgstRate = intraState ? CGST_RATE : 0.0;
        double sgstRate = intraState ? SGST_RATE : 0.0;
        double igstRate = intraState ? 0.0 : IGST_RATE;
        double cgstAmount = intraState ? AmountUtils.calculateTax(taxableAmount, CGST_RATE) : 0.0;
        double sgstAmount = intraState ? AmountUtils.calculateTax(taxableAmount, SGST_RATE) : 0.0;
        double igstAmount = intraState ? 0.0 : AmountUtils.calculateTax(taxableAmount, IGST_RATE);

        double totalAmountWithGST = AmountUtils.round(taxableAmount + cgstAmount + sgstAmount + igstAmount);

        if (advancePaid > totalAmountWithGST) {
            throw InvoiceRequestValidationException.of("advancePaid",
                    "advancePaid must not exceed the total with GST " + totalAmountWithGST);
        }

        double balanceDue = AmountUtils.round(totalAmountWithGST - advancePaid);
        String paymentStatus = advancePaid <= 0 ? STATUS_DUES : (balanceDue > 0 ? STATUS_PARTIAL : STATUS_PAID);

        return new InvoiceAmounts(totalAmount, discount, taxableAmount,
                cgstRate, sgstRate, igstRate, cgstAmount, sgstAmount, igstAmount,
                totalAmountWithGST, advancePaid, balanceDue, paymentStatus);
    }

    /** "Tamil Nadu", "tamilnadu", "TAMIL-NADU" → "tamilnadu". */
    static String normaliseState(String state) {
        return state == null ? "" : state.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    /**
     * All calculated amounts of an invoice (rounded to 2 decimals).
     */
    public record InvoiceAmounts(
            double totalAmount,
            double discount,
            double taxableAmount,
            double cgstRate,
            double sgstRate,
            double igstRate,
            double cgstAmount,
            double sgstAmount,
            double igstAmount,
            double totalAmountWithGST,
            double advancePaid,
            double balanceDue,
            String paymentStatus) {
    }
}
