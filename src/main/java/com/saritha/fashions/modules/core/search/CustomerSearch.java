package com.saritha.fashions.modules.core.search;

import org.springframework.data.mongodb.core.query.Criteria;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * How a search box text matches customers (the "customers" collection is shared by all branches, so this must not
 * read every customer):
 * - only digits: the phone. A full 10-digit number is an exact (indexed) lookup; fewer digits match anywhere in the
 *   phone, read from the phone index rather than the documents;
 * - other text of 3+ characters: the name, case-insensitive, read from the name index;
 * - shorter text: no customer match (the caller still matches invoice / receipt numbers).
 * The indexes are created by MongoIndexInitializer.
 */
public final class CustomerSearch {

    /** Text shorter than this does not search customer names. */
    public static final int MIN_NAME_LENGTH = 3;

    private CustomerSearch() {
    }

    /** The customer criteria for the search text, or empty when it should not match customers. */
    public static Optional<Criteria> criteria(String search) {
        String text = search == null ? "" : search.trim();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        if (text.chars().allMatch(Character::isDigit)) {
            return Optional.of(text.length() == 10
                    ? Criteria.where("phone").is(text)
                    : Criteria.where("phone").regex(Pattern.compile(Pattern.quote(text))));
        }
        if (text.length() >= MIN_NAME_LENGTH) {
            return Optional.of(Criteria.where("name").regex(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE)));
        }
        return Optional.empty();
    }
}
