package com.saritha.fashions.modules.core.date;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Expected delivery dates.
 *
 * Invoices store expectedDeliveryDate as text. New invoices store it as yyyy-MM-dd (validated
 * on create / edit), but older records may use other day-first formats. This class reads all
 * of them, so the delivery dashboard also finds old orders.
 *
 * Accepted: 2026-11-25, 2026/11/25, 25-11-2026, 25/11/2026, 25.11.2026, and an ISO date-time
 * that starts with a date (2026-11-25T10:00...). Day-first only (Indian format): 05/11/2026 is
 * 5 November. Impossible dates (31-02-2026) are rejected.
 */
public final class DeliveryDates {

    private static final Pattern ISO_PREFIX = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})(T.*)?$");
    private static final List<DateTimeFormatter> FORMATS = List.of(
            strict("uuuu-MM-dd"),
            strict("uuuu/MM/dd"),
            strict("d-M-uuuu"),
            strict("d/M/uuuu"),
            strict("d.M.uuuu"));

    private DeliveryDates() {
    }

    /** The date in the text, if it is a readable date. */
    public static Optional<LocalDate> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String value = text.trim();
        Matcher iso = ISO_PREFIX.matcher(value);
        if (iso.matches()) {
            value = iso.group(1);
        }
        for (DateTimeFormatter format : FORMATS) {
            try {
                return Optional.of(LocalDate.parse(value, format));
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        return Optional.empty();
    }

    /** The stored form of a delivery date: yyyy-MM-dd. */
    public static String format(LocalDate date) {
        return date.toString();
    }

    private static DateTimeFormatter strict(String pattern) {
        return DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT);
    }
}
