package com.saritha.fashions.modules.production.service;

import com.saritha.fashions.exception.APIException;

import java.util.List;
import java.util.Locale;

/**
 * Optional filters of the work queue (GET /items/queue and /items/queue/orders).
 *
 * @param statuses statuses to include (null / empty = the whole queue)
 * @param search   text in the invoice no., customer name or item name (case-insensitive); null = none
 * @param sort     OLDEST (invoice date, default), NEWEST, DUE_SOONEST (no date last)
 * @param due      null = any, TODAY, OVERDUE, WEEK (today + 6 days), NONE (no readable date)
 */
public record QueueFilter(List<String> statuses, String search, Sort sort, Due due) {

    public enum Sort { OLDEST, NEWEST, DUE_SOONEST }

    public enum Due { TODAY, OVERDUE, WEEK, NONE }

    public static final int MAX_SEARCH_LENGTH = 100;

    /** Reads the request parameters; blank values mean "not set". */
    public static QueueFilter of(List<String> statuses, String search, String sort, String due) {
        String text = search == null || search.isBlank() ? null : search.trim();
        if (text != null && text.length() > MAX_SEARCH_LENGTH) {
            throw new APIException("search must be at most " + MAX_SEARCH_LENGTH + " characters");
        }
        return new QueueFilter(statuses, text, parseSort(sort), parseDue(due));
    }

    /** True when only the statuses are set (the plain, database-paged queue can serve it). */
    public boolean isPlain() {
        return search == null && due == null && (sort == null || sort == Sort.OLDEST);
    }

    private static Sort parseSort(String value) {
        if (value == null || value.isBlank()) {
            return Sort.OLDEST;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "oldest" -> Sort.OLDEST;
            case "newest" -> Sort.NEWEST;
            case "duesoonest", "due" -> Sort.DUE_SOONEST;
            default -> throw new APIException("sort must be oldest, newest or dueSoonest");
        };
    }

    private static Due parseDue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "today" -> Due.TODAY;
            case "overdue" -> Due.OVERDUE;
            case "week" -> Due.WEEK;
            case "none" -> Due.NONE;
            default -> throw new APIException("due must be today, overdue, week or none");
        };
    }
}
