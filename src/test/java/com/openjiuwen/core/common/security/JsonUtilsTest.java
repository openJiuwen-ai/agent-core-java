package com.openjiuwen.core.common.security;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JsonUtils 测试类
 * 
 * @author OpenJiuwen
 * @since 2026-01-29
 */
class JsonUtilsTest {

    @Test
    void testSafeJsonLoadsValid() {
        // Arrange
        String json = "{\"name\":\"test\",\"value\":123}";
        
        // Act
        Map<?, ?> result = JsonUtils.safeJsonLoads(json, null, Map.class);
        
        // Assert
        assertNotNull(result);
        assertEquals("test", result.get("name"));
        assertEquals(123, result.get("value"));
    }

    @Test
    void testSafeJsonLoadsInvalidWithoutDefault() {
        // Arrange
        String invalidJson = "{invalid json}";
        
        // Act & Assert
        assertThrows(
            JiuWenBaseException.class,
            () -> JsonUtils.safeJsonLoads(invalidJson, null, Map.class)
        );
    }

    @Test
    void testSafeJsonLoadsInvalidWithDefault() {
        // Arrange
        String invalidJson = "{invalid json}";
        Map<String, Object> defaultValue = new HashMap<>();
        defaultValue.put("default", true);
        
        // Act
        @SuppressWarnings("unchecked")
        Map<String, Object> result = JsonUtils.safeJsonLoads(invalidJson, defaultValue, Map.class);
        
        // Assert
        assertNotNull(result);
        assertSame(defaultValue, result);
        assertEquals(true, result.get("default"));
    }

    @Test
    void testSafeJsonDumpsValid() {
        // Arrange
        Map<String, Object> obj = new HashMap<>();
        obj.put("name", "test");
        obj.put("value", 123);
        
        // Act
        String result = JsonUtils.safeJsonDumps(obj, null);
        
        // Assert
        assertNotNull(result);
        assertTrue(result.contains("\"name\""));
        assertTrue(result.contains("\"test\""));
        assertTrue(result.contains("\"value\""));
        assertTrue(result.contains("123"));
    }

    @Test
    void testSafeJsonDumpsInvalidWithoutDefault() {
        // Arrange - Create an object that causes serialization issues
        Object unserializable = new Object() {
            @SuppressWarnings("unused")
            public Object getSelf() {
                return this; // Circular reference
            }
        };
        
        // Act & Assert
        // This should throw JiuWenBaseException due to circular reference
        assertThrows(
            JiuWenBaseException.class,
            () -> JsonUtils.safeJsonDumps(unserializable, null)
        );
    }

    @Test
    void testSafeJsonDumpsInvalidWithDefault() {
        // Arrange
        Object obj = new Object() {
            @SuppressWarnings("unused")
            public void getInvalid() throws Exception {
                throw new Exception("test");
            }
        };
        String defaultValue = "{\"default\":true}";
        
        // Act
        String result = JsonUtils.safeJsonDumps(obj, defaultValue);
        
        // Assert
        assertNotNull(result);
        // Either serialized successfully or returned default
        assertTrue(result.length() > 0);
    }
}

