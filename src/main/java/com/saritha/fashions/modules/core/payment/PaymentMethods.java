package com.saritha.fashions.modules.core.payment;

import com.saritha.fashions.exception.APIException;
import org.bson.Document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The ONE list of payment methods (invoices, payments, expenses, reports).
 *
 * <p>Stored values are the canonical names {@link #CASH}, {@link #UPI}, {@link #CARD}, {@link #BANK_TRANSFER} and
 * {@link #OTHER}. Older data and the old frontend use other spellings ("cash", "BANK", "Debit/Credit Cards",
 * "other", ...). They are accepted as aliases on input and mapped to the canonical name in reports and filters
 * ({@link #canonicalExpression}), so figures are right even before the data migration has run.
 *
 * <p>Spellings are compared ignoring case, spaces and the characters {@code / - _ .} ("Debit/Credit Cards" =
 * "debit credit cards").
 */
public final class PaymentMethods {

    public static final String CASH = "Cash";
    public static final String UPI = "UPI";
    public static final String CARD = "Card";
    public static final String BANK_TRANSFER = "Bank Transfer";
    public static final String OTHER = "Other";

    /** Canonical names, in display order. */
    public static final List<String> ALL = List.of(CASH, UPI, CARD, BANK_TRANSFER, OTHER);

    /** Normalised alias -> canonical name. "BANK" (old expense forms) is read as Bank Transfer. */
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    static {
        alias(CASH, "cash", "cashpayment");
        alias(UPI, "upi", "gpay", "googlepay", "phonepe", "paytm", "bhim", "upipayment");
        alias(CARD, "card", "cards", "debitcard", "creditcard", "debitcards", "creditcards", "debitcreditcard",
                "debitcreditcards", "creditdebitcard", "creditdebitcards", "debitorcreditcard");
        alias(BANK_TRANSFER, "bank", "banktransfer", "banktransfers", "neft", "imps", "rtgs", "netbanking",
                "onlinebanking");
        alias(OTHER, "other", "others");
    }

    private PaymentMethods() {
    }

    private static void alias(String canonical, String... keys) {
        for (String key : keys) {
            ALIASES.put(key, canonical);
        }
    }

    /** Lower case without spaces and / - _ . */
    static String key(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s/\\-_.]", "");
    }

    /** The canonical name for a spelling, if it is known. */
    public static Optional<String> canonical(String value) {
        return Optional.ofNullable(ALIASES.get(key(value)));
    }

    /**
     * For input: blank -> the default (or null when there is none); a known spelling -> its canonical name;
     * anything else -> 400 listing the allowed values.
     */
    public static String require(String value, String defaultIfBlank) {
        if (value == null || value.isBlank()) {
            return defaultIfBlank;
        }
        return canonical(value).orElseThrow(() -> new APIException("paymentMethod must be one of " + ALL
                + " (got \"" + value.trim() + "\")"));
    }

    /** All aliases of a canonical name (normalised keys), e.g. for diagnostics. */
    public static List<String> aliasesOf(String canonical) {
        List<String> keys = new ArrayList<>();
        ALIASES.forEach((key, name) -> {
            if (name.equals(canonical)) {
                keys.add(key);
            }
        });
        return keys;
    }

    /**
     * MongoDB aggregation expression mapping a stored payment method (field path such as "$paymentMethod") to its
     * canonical name; an unknown spelling is returned trimmed as it is, a missing / blank one as "Unknown".
     * Needs MongoDB 4.4+ ($replaceAll).
     */
    public static Document canonicalExpression(String fieldPath) {
        Object text = new Document("$trim", new Document("input", new Document("$convert",
                new Document("input", fieldPath).append("to", "string").append("onError", "").append("onNull", ""))));
        Object key = new Document("$toLower", text);
        for (String character : List.of(" ", "/", "-", "_", ".")) {
            key = new Document("$replaceAll", new Document("input", key).append("find", character).append("replacement", ""));
        }
        List<Document> branches = new ArrayList<>();
        for (String canonical : ALL) {
            branches.add(new Document("case", new Document("$in", List.of(key, aliasesOf(canonical))))
                    .append("then", canonical));
        }
        Document fallback = new Document("$cond", List.of(new Document("$eq", List.of(text, "")), "Unknown", text));
        return new Document("$switch", new Document("branches", branches).append("default", fallback));
    }
}
