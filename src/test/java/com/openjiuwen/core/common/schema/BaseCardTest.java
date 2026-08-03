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

        assertNotNull(card.getId());
        assertEquals(32, card.getId().length());
        assertTrue(card.getId().chars().allMatch(ch -> Character.digit(ch, 16) >= 0));
        assertEquals("", card.getName());
        assertEquals("", card.getDescription());
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
}
