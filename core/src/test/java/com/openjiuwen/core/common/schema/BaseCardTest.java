package com.openjiuwen.core.common.schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseCardTest {

    @Test
    void defaultConstructorMatchesPythonDefaults() {
        BaseCard card = new BaseCard();

        assertGeneratedHexId(card.getId());
        assertEquals("", card.getName());
        assertEquals("", card.getDescription());
    }

    @Test
    void threeArgConstructorGeneratesIdOnlyWhenIdIsNull() {
        BaseCard nullIdCard = new BaseCard(null, "demo", "desc");
        BaseCard blankIdCard = new BaseCard("  ", "demo", "desc");

        assertGeneratedHexId(nullIdCard.getId());
        // Null id is replaced with a generated hex id; blank ids are kept so Tool can reject them.
        assertEquals("  ", blankIdCard.getId());
        assertEquals("demo", nullIdCard.getName());
        assertEquals("desc", blankIdCard.getDescription());
    }

    @Test
    void toolInfoReturnsNullLikePythonStub() {
        BaseCard card = new BaseCard();

        assertNull(card.toolInfo());
    }

    @Test
    void toStrUsesPythonFormat() {
        BaseCard card = new BaseCard("abc123", "demo", "desc");

        assertEquals("id=abc123,name=demo", card.toStr());
    }

    private static void assertGeneratedHexId(String id) {
        assertNotNull(id);
        assertEquals(32, id.length());
        assertTrue(id.chars().allMatch(ch -> Character.digit(ch, 16) >= 0));
    }
}
