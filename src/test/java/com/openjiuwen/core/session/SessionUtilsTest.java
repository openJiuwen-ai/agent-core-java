/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.session;

import com.openjiuwen.core.session.utils.SessionUtils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SessionUtils.getBySchema and SessionUtils.updateDict.
 * <p>
 * Ported from Python's {@code test_session.py::TestSession::test_get_by_schema} and
 * {@code test_clean_non_value}.
 */
class SessionUtilsTest {

    // ---------- getBySchema tests ----------

    @Nested
    @DisplayName("getBySchema")
    class GetBySchemaTests {

        @Test
        @DisplayName("updateDict creates nested structure from dot-path key")
        void testUpdateDictCreateNested() {
            Map<String, Object> source = new HashMap<>();
            SessionUtils.updateDict(Map.of("a.b.nums", List.of(1, 2, 3)), source);
            assertEquals(Map.of("a", Map.of("b", Map.of("nums", List.of(1, 2, 3)))), source);
        }

        @Test
        @DisplayName("updateDict adds property to existing nested")
        void testUpdateDictAddProperty() {
            Map<String, Object> source = new HashMap<>();
            SessionUtils.updateDict(Map.of("a.b.nums", List.of(1, 2, 3)), source);
            SessionUtils.updateDict(Map.of("a.b.name", "shanghai"), source);

            @SuppressWarnings("unchecked")
            Map<String, Object> b = (Map<String, Object>) ((Map<?, ?>) source.get("a")).get("b");
            assertEquals(List.of(1, 2, 3), b.get("nums"));
            assertEquals("shanghai", b.get("name"));
        }

        @Test
        @DisplayName("updateDict merges map value into nested target")
        void testUpdateDictMergeMap() {
            Map<String, Object> source = new HashMap<>();
            SessionUtils.updateDict(Map.of("a.b.nums", List.of(1, 2, 3)), source);
            SessionUtils.updateDict(Map.of("a.b.name", "shanghai"), source);
            SessionUtils.updateDict(Map.of("a.b", Map.of("class", "hha")), source);

            @SuppressWarnings("unchecked")
            Map<String, Object> b = (Map<String, Object>) ((Map<?, ?>) source.get("a")).get("b");
            assertEquals(List.of(1, 2, 3), b.get("nums"));
            assertEquals("shanghai", b.get("name"));
            assertEquals("hha", b.get("class"));
        }

        @Test
        @DisplayName("updateDict replaces nested map with list")
        void testUpdateDictOverrideWithList() {
            Map<String, Object> source = new HashMap<>();
            SessionUtils.updateDict(Map.of("a.b.nums", List.of(1, 2, 3)), source);
            SessionUtils.updateDict(Map.of("a.b", List.of(1, 2, 3)), source);
            assertEquals(Map.of("a", Map.of("b", List.of(1, 2, 3))), source);
        }

        @Test
        @DisplayName("updateDict updates existing list index")
        void testUpdateDictExistingListIndex() {
            Map<String, Object> source = new HashMap<>();
            SessionUtils.updateDict(Map.of("a.b", List.of(1, 2, 3)), source);
            SessionUtils.updateDict(Map.of("a.b[0]", 11), source);

            assertEquals(Map.of("a", Map.of("b", List.of(11, 2, 3))), source);
        }

        @Test
        @DisplayName("updateDict creates nested list index from path")
        void testUpdateDictCreateNestedListIndex() {
            Map<String, Object> source = new HashMap<>();
            SessionUtils.updateDict(Map.of("a.b[0]", 11), source);

            assertEquals(Map.of("a", Map.of("b", List.of(11))), source);
        }

        @Test
        @DisplayName("getBySchema with simple string key returns nested value")
        void testGetBySchemaString() {
            Map<String, Object> source = Map.of("a", Map.of("b", List.of(1, 2, 3)));
            Object result = SessionUtils.getBySchema("a", source);
            assertEquals(Map.of("b", List.of(1, 2, 3)), result);
        }

        @Test
        @DisplayName("getBySchema with plain map (no refs) returns map as-is")
        void testGetBySchemaPlainMap() {
            Map<String, Object> source = Map.of("a", Map.of("b", List.of(1, 2, 3)));
            Object result = SessionUtils.getBySchema(Map.of("a", "b"), source);
            assertEquals(Map.of("a", "b"), result);
        }

        @Test
        @DisplayName("getBySchema with ${ref} resolves reference")
        void testGetBySchemaRefResolution() {
            Map<String, Object> source = Map.of("a", Map.of("b", List.of(1, 2, 3)));
            Object result = SessionUtils.getBySchema(Map.of("result", "${a.b}"), source);
            assertEquals(Map.of("result", List.of(1, 2, 3)), result);
        }

        @Test
        @DisplayName("getBySchema with list containing mixed refs and plain")
        void testGetBySchemaListMixed() {
            Map<String, Object> source = Map.of("a", Map.of("b", List.of(1, 2, 3)));
            Object result = SessionUtils.getBySchema(Map.of("result", List.of("abc", "${a}")), source);
            assertEquals(Map.of("result", List.of("abc", Map.of("b", List.of(1, 2, 3)))), result);
        }

        @Test
        @DisplayName("getBySchema with plain string list returns as-is")
        void testGetBySchemaPlainList() {
            Map<String, Object> source = Map.of("a", Map.of("b", List.of(1, 2, 3)));
            Object result = SessionUtils.getBySchema(Map.of("result", List.of("abc", "cde")), source);
            assertEquals(Map.of("result", List.of("abc", "cde")), result);
        }

        @Test
        @DisplayName("getBySchema with non-existent ref returns null in result")
        void testGetBySchemaNonExistentRef() {
            Map<String, Object> source = Map.of("a", Map.of("b", List.of(1, 2, 3)));
            Object result = SessionUtils.getBySchema(Map.of("result", Map.of("abc", "cde", "result", "${1}")), source);
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            assertEquals("cde", ((Map<?, ?>) resultMap.get("result")).get("abc"));
            assertNull(((Map<?, ?>) resultMap.get("result")).get("result"));
        }

        @Test
        @DisplayName("getBySchema with list index access (negative index)")
        void testGetBySchemaListIndex() {
            Map<String, Object> source = new HashMap<>();
            SessionUtils.updateDict(Map.of("a.b", List.of(1, 2, 3)), source);
            Object result = SessionUtils.getBySchema(Map.of("a", "${a.b[-1]}"), source);
            assertEquals(3, ((Map<?, ?>) result).get("a"));
        }

        @Test
        @DisplayName("getBySchema with non-existent ref in list returns null")
        void testGetBySchemaNonExistentRefInList() {
            Map<String, Object> source = Map.of("a", Map.of("b", List.of(1, 2, 3)));
            Object result = SessionUtils.getBySchema(Map.of("result", List.of("${abc}", "cde")), source);
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) resultMap.get("result");
            assertNull(list.get(0));
            assertEquals("cde", list.get(1));
        }

        @Test
        @DisplayName("getBySchema with nested map ref resolution")
        void testGetBySchemaNestedMapRef() {
            Map<String, Object> source = Map.of("a", Map.of("b", List.of(1, 2, 3)));
            Object result = SessionUtils.getBySchema(Map.of("result", Map.of("abc", "cde", "result", "${a}")), source);
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            @SuppressWarnings("unchecked")
            Map<String, Object> inner = (Map<String, Object>) resultMap.get("result");
            assertEquals("cde", inner.get("abc"));
            assertEquals(Map.of("b", List.of(1, 2, 3)), inner.get("result"));
        }

        @Test
        @DisplayName("getBySchema with positive list index")
        void testGetBySchemaPositiveListIndex() {
            Map<String, Object> source1 = Map.of("a", Map.of("b", List.of("cc", "dd", "ee")));
            Object result = SessionUtils.getBySchema(Map.of("result", "${a.b[1]}"), source1);
            assertEquals("dd", ((Map<?, ?>) result).get("result"));
        }
    }

    // ---------- updateDict with null values (clean/delete) ----------

    @Nested
    @DisplayName("updateDict null value deletion")
    class UpdateDictClean {

        @Test
        @DisplayName("null value removes top-level key")
        void testNullRemovesTopLevel() {
            Map<String, Object> data = new HashMap<>();
            data.put("a", new HashMap<>(Map.of("a1", 1, "a2", 2)));
            data.put("b", new HashMap<>(Map.of("b1",
                    new HashMap<>(Map.of("b11", "1", "b12", Arrays.asList(1, 2, null), "b13", "2")))));
            data.put("c", 2);

            Map<String, Object> update = new HashMap<>();
            update.put("c", null);
            SessionUtils.updateDict(update, data);
            assertFalse(data.containsKey("c"));
        }

        @Test
        @DisplayName("nested null value removes nested key via dot-path")
        void testNestedNullRemoves() {
            Map<String, Object> data = new HashMap<>();
            data.put("a", new HashMap<>(Map.of("a1", 1, "a2", 2)));

            Map<String, Object> update = new HashMap<>();
            update.put("a.a1", null);
            SessionUtils.updateDict(update, data);

            @SuppressWarnings("unchecked")
            Map<String, Object> a = (Map<String, Object>) data.get("a");
            assertFalse(a.containsKey("a1"));
            assertEquals(2, a.get("a2"));
        }

        @Test
        @DisplayName("updateDict ignoreDelete flag prevents deletion")
        void testIgnoreDeleteFlag() {
            Map<String, Object> data = new HashMap<>();
            data.put("a", 1);
            data.put("b", 2);

            Map<String, Object> update = new HashMap<>();
            update.put("a", null);
            SessionUtils.updateDict(update, data, true);

            // With ignoreDelete=true, key should not be removed
            assertTrue(data.containsKey("a"));
        }
    }

    // ---------- splitNestedPath ----------

    @Nested
    @DisplayName("splitNestedPath")
    class SplitNestedPathTests {

        @Test
        @DisplayName("simple dot path splits correctly")
        void testSimpleDotPath() {
            List<Object> result = SessionUtils.splitNestedPath("a.b.c");
            assertEquals(List.of("a", "b", "c"), result);
        }

        @Test
        @DisplayName("path with array index splits correctly")
        void testArrayIndexPath() {
            List<Object> result = SessionUtils.splitNestedPath("a.b[1].c");
            assertEquals(List.of("a", "b", 1, "c"), result);
        }

        @Test
        @DisplayName("simple key without dots returns empty list")
        void testSimpleKeyNoDots() {
            List<Object> result = SessionUtils.splitNestedPath("abc");
            assertEquals(List.of(), result);
        }

        @Test
        @DisplayName("negative index in path")
        void testNegativeIndex() {
            List<Object> result = SessionUtils.splitNestedPath("a.b[-1]");
            assertEquals(List.of("a", "b", -1), result);
        }
    }

    // ---------- isRefPath / extractOriginKey ----------

    @Nested
    @DisplayName("Reference path utilities")
    class RefPathTests {

        @Test
        @DisplayName("isRefPath recognizes ${xxx} pattern")
        void testIsRefPath() {
            assertTrue(SessionUtils.isRefPath("${a.b.c}"));
            assertTrue(SessionUtils.isRefPath("${start.a}"));
            assertFalse(SessionUtils.isRefPath("abc"));
            assertFalse(SessionUtils.isRefPath("${}")); // too short
            assertFalse(SessionUtils.isRefPath(null));
        }

        @Test
        @DisplayName("extractOriginKey extracts inner key")
        void testExtractOriginKey() {
            assertEquals("start.a", SessionUtils.extractOriginKey("${start.a}"));
            assertEquals("a.b.c", SessionUtils.extractOriginKey("${a.b.c}"));
            assertEquals("plain", SessionUtils.extractOriginKey("plain"));
            assertNull(SessionUtils.extractOriginKey(null));
        }
    }

    @Nested
    @DisplayName("EndFrame")
    class EndFrameTests {

        @Test
        @DisplayName("EndFrame carries source id like Python dataclass")
        void testEndFrameCarriesSource() {
            SessionUtils.EndFrame frame = new SessionUtils.EndFrame("node-a");

            assertEquals("node-a", frame.source());
            assertEquals("node-a", frame.getSource());
            assertEquals(new SessionUtils.EndFrame("node-a"), frame);
            assertEquals("all streaming outputs finish", SessionUtils.EndFrame.MESSAGE);
        }
    }
}
