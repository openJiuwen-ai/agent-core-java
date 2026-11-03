package com.openjiuwen.core.graph.pregel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for {@link PregelConfig}.
 *
 * <p>Mirrors the Pregel config assertions in
 * {@code tests/unit_tests/core/graph/pregel}.</p>
 */
class PregelConfigTest {

    @Test
    @DisplayName("default config uses MAX_RECURSIVE_LIMIT")
    void testDefaultConfig() {
        PregelConfig config = new PregelConfig();
        assertEquals(PregelConstants.MAX_RECURSIVE_LIMIT, config.getRecursionLimit());
        assertNull(config.getSessionId());
        assertNull(config.getNs());
    }

    @Test
    @DisplayName("parameterized constructor preserves fields")
    void testParameterizedConstructor() {
        PregelConfig config = new PregelConfig("session1", "ns1", 100);
        assertEquals("session1", config.getSessionId());
        assertEquals("ns1", config.getNs());
        assertEquals(100, config.getRecursionLimit());
    }

    @Test
    @DisplayName("get by key name")
    void testGetByKey() {
        PregelConfig config = new PregelConfig("sid", "myns", 50);
        assertEquals("sid", config.get(PregelConstants.SESSION_ID));
        assertEquals("myns", config.get(PregelConstants.NS));
        assertEquals(50, config.get(PregelConstants.RECURSION_LIMIT));
        assertNull(config.get("unknown_key"));
    }

    @Test
    @DisplayName("toMap contains all fields")
    void testToMap() {
        PregelConfig config = new PregelConfig("sid", "ns", 100);
        config.setParentNs("parent");
        Map<String, Object> map = config.toMap();
        assertEquals("sid", map.get(PregelConstants.SESSION_ID));
        assertEquals("ns", map.get(PregelConstants.NS));
        assertEquals("parent", map.get(PregelConstants.PARENT_NS));
        assertEquals(100, map.get(PregelConstants.RECURSION_LIMIT));
    }

    @Test
    @DisplayName("createInnerConfig copies fields")
    void testCreateInnerConfig() {
        PregelConfig original = new PregelConfig("sid", "ns", 200);
        original.setParentNs("parent");
        PregelConfig inner = PregelConfig.createInnerConfig(original);
        assertEquals("sid", inner.getSessionId());
        assertEquals("ns", inner.getNs());
        assertEquals("parent", inner.getParentNs());
        assertEquals(200, inner.getRecursionLimit());
    }

    @Test
    @DisplayName("createInnerConfig uses default recursion limit when missing")
    void testCreateInnerConfigDefaultRecursionLimit() {
        PregelConfig original = new PregelConfig("sid", "ns", 0);
        PregelConfig inner = PregelConfig.createInnerConfig(original);
        assertEquals(PregelConstants.MAX_RECURSIVE_LIMIT, inner.getRecursionLimit());
    }

    @Test
    @DisplayName("createInnerConfig from null uses defaults")
    void testCreateInnerConfigNull() {
        PregelConfig inner = PregelConfig.createInnerConfig(null);
        assertEquals(PregelConstants.MAX_RECURSIVE_LIMIT, inner.getRecursionLimit());
        assertNull(inner.getSessionId());
        assertNull(inner.getNs());
        assertNull(inner.getParentNs());
    }
}
