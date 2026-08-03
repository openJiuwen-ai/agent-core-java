/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import com.openjiuwen.core.session.utils.SessionUtils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionUtilsTest {
    @Test
    @DisplayName("getBySchema mirrors nested update and reference resolution")
    void testGetBySchema() {
        Map<String, Object> source = new LinkedHashMap<>();
        SessionUtils.updateDict(Map.of("a.b.nums", List.of(1, 2, 3)), source);
        assertEquals(Map.of("a", Map.of("b", Map.of("nums", List.of(1, 2, 3)))), source);

        SessionUtils.updateDict(Map.of("a.b.name", "shanghai"), source);
        assertEquals(Map.of("a", Map.of("b", Map.of("nums", List.of(1, 2, 3), "name", "shanghai"))), source);

        SessionUtils.updateDict(Map.of("a.b", Map.of("class", "hha")), source);
        assertEquals(
                Map.of("a", Map.of("b", Map.of("nums", List.of(1, 2, 3), "name", "shanghai", "class", "hha"))),
                source
        );

        SessionUtils.updateDict(Map.of("a.b", List.of(1, 2, 3)), source);
        assertEquals(Map.of("a", Map.of("b", List.of(1, 2, 3))), source);
        assertEquals(Map.of("b", List.of(1, 2, 3)), SessionUtils.getBySchema("a", source));
        assertEquals(Map.of("a", "b"), SessionUtils.getBySchema(Map.of("a", "b"), source));
        assertEquals(Map.of("result", List.of(1, 2, 3)), SessionUtils.getBySchema(Map.of("result", "${a.b}"), source));
        assertEquals(
                Map.of("result", List.of("abc", Map.of("b", List.of(1, 2, 3)))),
                SessionUtils.getBySchema(Map.of("result", List.of("abc", "${a}")), source)
        );
        assertEquals(
                Map.of("result", List.of("abc", "cde")),
                SessionUtils.getBySchema(Map.of("result", List.of("abc", "cde")), source)
        );
        assertEquals(
                linkedMapOf("result", linkedMapOf("abc", "cde", "result", null)),
                SessionUtils.getBySchema(Map.of("result", Map.of("abc", "cde", "result", "${1}")), source)
        );
        assertEquals(Map.of("a", 3), SessionUtils.getBySchema(Map.of("a", "${a.b[-1]}"), source));

        Map<String, Object> source1 = Map.of("a", Map.of("b", List.of("cc", "dd", "ee")));
        assertEquals(
                Map.of("result", "dd"),
                SessionUtils.getBySchema(Map.of("result", "${a.b[1]}"), source1)
        );
        assertEquals(
                linkedMapOf("result", mutableList(null, "cde")),
                SessionUtils.getBySchema(Map.of("result", List.of("${abc}", "cde")), source)
        );
        assertEquals(
                Map.of("result", Map.of("abc", "cde", "result", Map.of("b", List.of(1, 2, 3)))),
                SessionUtils.getBySchema(Map.of("result", Map.of("abc", "cde", "result", "${a}")), source)
        );
    }

    @Test
    @DisplayName("updateDict keeps Python delete semantics")
    void testCleanNonValue() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("a", new LinkedHashMap<>(Map.of("a1", 1, "a2", 2)));
        data.put("b", new LinkedHashMap<>(Map.of("b1", linkedMapOf("b11", "1", "b12", mutableList(1, 2, null), "b13", "2"))));
        data.put("c", 2);

        SessionUtils.updateDict(linkedMapOf("c", null), data);
        assertFalse(data.containsKey("c"));

        SessionUtils.updateDict(linkedMapOf("a.a1", null), data);
        assertEquals(
                Map.of(
                        "a", Map.of("a2", 2),
                        "b", Map.of("b1", linkedMapOf("b11", "1", "b12", mutableList(1, 2, null), "b13", "2"))
                ),
                data
        );

        Map<String, Object> ignoreDeleteData = new LinkedHashMap<>(Map.of("a", 1, "b", 2));
        SessionUtils.updateDict(linkedMapOf("a", null), ignoreDeleteData, true);
        assertEquals(linkedMapOf("a", null, "b", 2), ignoreDeleteData);
    }

    @Test
    @DisplayName("splitNestedPath supports dotted keys and quoted map keys")
    void testSplitNestedPath() {
        assertEquals(List.of("a", "b", "c"), SessionUtils.splitNestedPath("a.b.c"));
        assertEquals(List.of("a", "b", 1, "c"), SessionUtils.splitNestedPath("a.b[1].c"));
        assertEquals(List.of("a", "b", 0, "key"), SessionUtils.splitNestedPath("a.b[0]['key']"));
        assertEquals(List.of("a", "b", -1), SessionUtils.splitNestedPath("a.b[-1]"));
        assertEquals(List.of(), SessionUtils.splitNestedPath("abc"));
    }

    @Test
    @DisplayName("reference helpers match Python semantics")
    void testReferenceHelpers() {
        assertTrue(SessionUtils.isRefPath("${a.b.c}"));
        assertFalse(SessionUtils.isRefPath("${}"));
        assertFalse(SessionUtils.isRefPath("abc"));
        assertEquals("start.a", SessionUtils.extractOriginKey("${start.a}"));
        assertEquals("plain", SessionUtils.extractOriginKey("plain"));
        assertNull(SessionUtils.extractOriginKey(null));
    }

    @Test
    @DisplayName("createWrapperClass proxies interface methods and unwraps the source object")
    void testCreateWrapperClass() {
        GreetingImpl original = new GreetingImpl();
        Greeting wrapped = SessionUtils.createWrapperClass(original, "GreetingWrapper");

        assertEquals("hello, codex", wrapped.greet("codex"));
        assertTrue(wrapped instanceof SessionUtils.WrappedObject<?>);
        assertSame(original, ((SessionUtils.WrappedObject<?>) wrapped).getWrapped());
    }

    @Test
    @DisplayName("rootToIndex creates and resolves nested list paths")
    void testRootToIndex() {
        List<Object> source = new ArrayList<>();
        SessionUtils.rootToIndex(List.of(1, 2, 3), source, true);
        assertEquals(mutableList(null, mutableList(null, null, mutableList(null, null, null, Map.of()))), source);

        Object[] created = SessionUtils.rootToIndex(List.of(1, 2, 3), source);
        assertEquals(3, created[0]);
        assertEquals(((List<?>) ((List<?>) source.get(1)).get(2)), created[1]);

        List<Object> existing = mutableList(
                1,
                mutableList(2, List.of(2, mutableList(3, 4, 5, mutableList(7, 8, 9))))
        );
        Object[] existingPath = SessionUtils.rootToIndex(List.of(1, 1, 1, 3, 2), existing);
        assertEquals(2, existingPath[0]);
        assertEquals(9, ((List<?>) existingPath[1]).get((Integer) existingPath[0]));

        assertArrayEquals(new Object[]{2, List.of(1, 2, 3)}, SessionUtils.rootToIndex(List.of(-1), List.of(1, 2, 3)));
        assertArrayEquals(new Object[]{null, null}, SessionUtils.rootToIndex(List.of(-5), List.of(1, 2, 3)));

        List<Object> singleLevel = new ArrayList<>();
        assertArrayEquals(new Object[]{0, singleLevel}, SessionUtils.rootToIndex(List.of(0), singleLevel, true));
        assertEquals(List.of(Map.of()), singleLevel);

        List<Object> twoLevels = new ArrayList<>();
        Object[] twoLevelResult = SessionUtils.rootToIndex(List.of(0, 1), twoLevels, true);
        assertEquals(1, twoLevelResult[0]);
        assertEquals(mutableList(mutableList(null, Map.of())), twoLevels);

        List<Object> tupleLikeRoot = List.of(1, new ArrayList<>(List.of(2, 3)));
        Object[] tupleLikeResult = SessionUtils.rootToIndex(List.of(1, 5), tupleLikeRoot, true);
        assertEquals(5, tupleLikeResult[0]);
        assertEquals(mutableList(2, 3, null, null, null, Map.of()), tupleLikeRoot.get(1));

        assertThrows(
                IllegalArgumentException.class,
                () -> SessionUtils.rootToIndex(Arrays.asList(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), new ArrayList<>())
        );

        List<Object> maxIndexSource = new ArrayList<>();
        assertArrayEquals(new Object[]{null, null}, SessionUtils.rootToIndex(List.of(10000), maxIndexSource, true));
        assertThrows(IllegalArgumentException.class, () -> SessionUtils.rootToIndex(List.of(10001), new ArrayList<>()));

        List<Object> originalData = mutableList(1, mutableList(2, 3));
        List<Object> originalCopy = mutableList(1, mutableList(2, 3));
        assertArrayEquals(new Object[]{null, null}, SessionUtils.rootToIndex(List.of(1, 5), originalData, false));
        assertEquals(originalCopy, originalData);

        List<Object> negativeChain = mutableList(1, mutableList(2, 3, mutableList(4, 5, 6)), 7);
        Object[] negativeChainResult = SessionUtils.rootToIndex(List.of(1, -1, -1), negativeChain);
        assertEquals(2, negativeChainResult[0]);
        assertEquals(6, ((List<?>) negativeChainResult[1]).get((Integer) negativeChainResult[0]));
    }

    @Test
    @DisplayName("EndFrame mirrors Python dataclass equality")
    void testEndFrame() {
        SessionUtils.EndFrame frame = new SessionUtils.EndFrame("node-a");
        assertEquals("node-a", frame.source());
        assertEquals("node-a", frame.getSource());
        assertEquals(new SessionUtils.EndFrame("node-a"), frame);
    }

    private static List<Object> mutableList(Object... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    private static Map<String, Object> linkedMapOf(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private interface Greeting {
        String greet(String name);
    }

    private static final class GreetingImpl implements Greeting {
        @Override
        public String greet(String name) {
            return "hello, " + name;
        }
    }
}
