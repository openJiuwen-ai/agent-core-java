package com.openjiuwen.core.memory.lite;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontmatterUtilsTest {

    @Test
    void parseAndValidateFrontmatterMatchPythonRules() {
        Map<String, String> parsed = FrontmatterUtils.parseFrontmatter("""
                ---
                name: note
                description: desc
                type: user
                ---

                body
                """);

        assertEquals("note", parsed.get("name"));
        assertEquals("desc", parsed.get("description"));
        assertTrue(FrontmatterUtils.validateFrontmatter(parsed).valid());
        assertEquals("", FrontmatterUtils.validateFrontmatter(parsed).message());
        assertNull(FrontmatterUtils.parseFrontmatter("body only"));
    }

    @Test
    void enrichFrontmatterAddsDatesLikePython() {
        Map<String, String> fm = new LinkedHashMap<>();
        fm.put("name", "note");
        fm.put("description", "desc");
        fm.put("type", "user");

        Map<String, String> enriched = FrontmatterUtils.enrichFrontmatter(fm, false);
        String today = LocalDate.now().toString();

        assertEquals(today, enriched.get("created_at"));
        assertEquals(today, enriched.get("updated_at"));
    }

    @Test
    void rebuildContentPreservesBody() {
        String rebuilt = FrontmatterUtils.rebuildContentWithFrontmatter(
                """
                ---
                old: x
                ---

                body text
                """,
                new LinkedHashMap<>(Map.of("name", "n", "description", "d", "type", "user"))
        );

        assertTrue(rebuilt.startsWith("---"));
        assertTrue(rebuilt.contains("name: n"));
        assertTrue(rebuilt.endsWith("body text"));
    }
}
