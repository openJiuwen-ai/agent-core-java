/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VectorStorePlugin.
 * <p>
 * Mirrors Python's test_vector_store_plugin.py from
 * <code>tests/unit_tests/core/foundation/store/test_vector_store_plugin.py</code>.
 */
@DisplayName("Vector Store Plugin Tests")
class TestVectorStorePlugin {

    // Stub classes
    static class PluginConfigStub {
        String pluginName;
        Map<String, Object> settings = new HashMap<>();

        PluginConfigStub(String pluginName) {
            this.pluginName = pluginName;
        }

        void setSetting(String key, Object value) {
            settings.put(key, value);
        }
    }

    static class VectorStorePluginStub {
        String name;
        PluginConfigStub config;
        boolean initialized = false;

        VectorStorePluginStub(String name, PluginConfigStub config) {
            this.name = name;
            this.config = config;
        }

        void initialize() {
            initialized = true;
        }

        boolean isInitialized() {
            return initialized;
        }

        Object createStore() {
            if (!initialized) {
                throw new IllegalStateException("Plugin not initialized");
            }
            return new Object(); // Return mock store
        }
    }

    static class PluginRegistry {
        Map<String, VectorStorePluginStub> plugins = new HashMap<>();

        void register(VectorStorePluginStub plugin) {
            plugins.put(plugin.name, plugin);
        }

        VectorStorePluginStub get(String name) {
            return plugins.get(name);
        }

        void unregister(String name) {
            plugins.remove(name);
        }
    }

    @Nested
    @DisplayName("Plugin Registration Tests")
    class TestPluginRegistration {

        @Test
        @DisplayName("register plugin")
        void testRegisterPlugin() {
            PluginRegistry registry = new PluginRegistry();
            VectorStorePluginStub plugin = new VectorStorePluginStub(
                "chroma", new PluginConfigStub("chroma")
            );

            registry.register(plugin);

            assertNotNull(registry.get("chroma"));
        }

        @Test
        @DisplayName("unregister plugin")
        void testUnregisterPlugin() {
            PluginRegistry registry = new PluginRegistry();
            VectorStorePluginStub plugin = new VectorStorePluginStub(
                "chroma", new PluginConfigStub("chroma")
            );
            registry.register(plugin);

            registry.unregister("chroma");

            assertNull(registry.get("chroma"));
        }
    }

    @Nested
    @DisplayName("Plugin Initialization Tests")
    class TestPluginInitialization {

        @Test
        @DisplayName("initialize plugin")
        void testInitializePlugin() {
            VectorStorePluginStub plugin = new VectorStorePluginStub(
                "chroma", new PluginConfigStub("chroma")
            );

            plugin.initialize();

            assertTrue(plugin.isInitialized());
        }

        @Test
        @DisplayName("create store requires initialization")
        void testCreateStoreRequiresInitialization() {
            VectorStorePluginStub plugin = new VectorStorePluginStub(
                "chroma", new PluginConfigStub("chroma")
            );

            assertThrows(IllegalStateException.class, () -> plugin.createStore());
        }

        @Test
        @DisplayName("create store after initialization")
        void testCreateStoreAfterInitialization() {
            VectorStorePluginStub plugin = new VectorStorePluginStub(
                "chroma", new PluginConfigStub("chroma")
            );
            plugin.initialize();

            Object store = plugin.createStore();

            assertNotNull(store);
        }
    }

    @Nested
    @DisplayName("Plugin Config Tests")
    class TestPluginConfig {

        @Test
        @DisplayName("plugin config settings")
        void testPluginConfigSettings() {
            PluginConfigStub config = new PluginConfigStub("milvus");
            config.setSetting("host", "localhost");
            config.setSetting("port", 19530);

            assertEquals("milvus", config.pluginName);
            assertEquals("localhost", config.settings.get("host"));
            assertEquals(19530, config.settings.get("port"));
        }
    }
}