package com.openjiuwen.core.memory.lite;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemorySettingsTest {

    @Test
    void defaultsMatchPythonConfiguration() {
        MemorySettings settings = MemorySettings.createMemorySettings();

        assertEquals("openai_compatible", settings.getProvider());
        assertEquals("text-embedding-v3", settings.getModel());
        assertEquals("mock", settings.getFallback());
        assertEquals(List.of("memory", "sessions"), settings.getSources());
        assertEquals(256, settings.getChunking().get("tokens"));
        assertEquals(32, settings.getChunking().get("overlap"));
        assertEquals(10, ((Number) settings.getQuery().get("max_results")).intValue());
        assertEquals(0.3d, ((Number) settings.getQuery().get("min_score")).doubleValue());
        assertTrue((Boolean) ((Map<?, ?>) settings.getQuery().get("hybrid")).get("enabled"));
    }

    @Test
    void overridesReplaceKnownFieldsOnly() {
        MemorySettings settings = MemorySettings.createMemorySettings(Map.of(
                "provider", "custom",
                "sources", List.of("a"),
                "extra_paths", List.of("x.md"),
                "cache", Map.of("enabled", false),
                "unknown", "ignored"
        ));

        assertEquals("custom", settings.getProvider());
        assertEquals(List.of("a"), settings.getSources());
        assertEquals(List.of("x.md"), settings.getExtraPaths());
        assertEquals(false, settings.getCache().get("enabled"));
        assertEquals("text-embedding-v3", settings.getModel());
    }
}
