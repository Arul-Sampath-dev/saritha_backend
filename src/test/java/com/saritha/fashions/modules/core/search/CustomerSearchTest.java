package com.saritha.fashions.modules.core.search;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class CustomerSearchTest {

    private static Document query(String search) {
        return CustomerSearch.criteria(search).map(Criteria::getCriteriaObject).orElse(null);
    }

    @Test
    void fullPhoneNumberIsAnExactLookup() {
        assertEquals(new Document("phone", "9876543210"), query(" 9876543210 "));
    }

    @Test
    void partOfAPhoneNumberMatchesThePhoneOnly() {
        Document query = query("3210");
        assertEquals(1, query.size());
        Pattern phone = (Pattern) query.get("phone");
        assertTrue(phone.matcher("9876543210").find(), "matches anywhere in the phone");
        assertFalse(phone.matcher("9876543211").find());
    }

    @Test
    void textMatchesTheNameFromThreeCharacters() {
        Pattern name = (Pattern) query("pri").get("name");
        assertTrue(name.matcher("Priya K").find());
        assertNull(query("pr"), "too short: invoice numbers only");
        assertNull(query("  "));
        assertNull(query(null));
    }

    @Test
    void specialCharactersAreLiteral() {
        Pattern name = (Pattern) query("a.b(").get("name");
        assertFalse(name.matcher("axb(").find());
        assertTrue(name.matcher("xa.b(y").find());
    }
}
