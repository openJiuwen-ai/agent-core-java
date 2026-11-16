/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.testsupport;

import com.openjiuwen.core.common.clients.ClientRegistry;
import com.openjiuwen.core.foundation.llm.Model;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Restores process-wide model registrations after tests that install fixture providers.
 * Test execution must remain serial while these shared registries are replaced.
 *
 * @since 0.1.18
 */
public final class ModelRegistryIsolation implements BeforeEachCallback, AfterEachCallback {
    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(ModelRegistryIsolation.class);

    /**
     * Saves model registrations before a test can replace a provider.
     *
     * @param context current test context
     * @throws ReflectiveOperationException if the registry fields cannot be accessed
     */
    @Override
    public void beforeEach(ExtensionContext context) throws ReflectiveOperationException {
        // Model registration also removes invoke-only providers and exposes factories to ClientRegistry.
        // Snapshot all affected maps because the public API cannot restore an overwritten factory.
        List<RegistrySnapshot> snapshots = List.of(
                snapshot(Model.class, null, "CLIENT_FACTORIES"),
                snapshot(Model.class, null, "INVOKERS"),
                snapshot(ClientRegistry.class, ClientRegistry.getClientRegistry(), "factories"));
        context.getStore(NAMESPACE).put("registries", snapshots);
    }

    /**
     * Restores registrations even when the test fails.
     *
     * @param context current test context
     */
    @Override
    @SuppressWarnings("unchecked")
    public void afterEach(ExtensionContext context) {
        List<RegistrySnapshot> snapshots =
                (List<RegistrySnapshot>) context.getStore(NAMESPACE).remove("registries");
        if (snapshots != null) {
            snapshots.forEach(RegistrySnapshot::restore);
        }
    }

    @SuppressWarnings("unchecked")
    private static RegistrySnapshot snapshot(Class<?> type, Object owner, String fieldName)
            throws ReflectiveOperationException {
        Field field = type.getDeclaredField(fieldName);
        field.setAccessible(true);
        Map<String, Object> registry = (Map<String, Object>) field.get(owner);
        return new RegistrySnapshot(registry, new LinkedHashMap<>(registry));
    }

    private record RegistrySnapshot(Map<String, Object> registry, Map<String, Object> original) {
        private void restore() {
            registry.clear();
            registry.putAll(original);
        }
    }
}
