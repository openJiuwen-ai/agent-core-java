
package com.openjiuwen.core.memory.manage.mem_model;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DataIdManagerTest {
    @Test
    void generateNextIdReturnsPythonShapedTwentyFourHexChars() {
        DataIdManager manager = new DataIdManager();

        String first = manager.generateNextId("user-1");
        String second = manager.generateNextId("user-1");

        assertTrue(first.matches("[0-9a-f]{24}"));
        assertTrue(second.matches("[0-9a-f]{24}"));
        assertNotEquals(first, second);
    }
}
