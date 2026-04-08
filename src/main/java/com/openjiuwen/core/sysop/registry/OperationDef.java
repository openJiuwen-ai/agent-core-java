/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.registry;

import com.openjiuwen.core.sysop.BaseOperation;
import com.openjiuwen.core.sysop.OperationMode;

import java.lang.reflect.Constructor;
import java.util.Objects;

/**
 * Definition and factory for an operation.
 * <p>
 * Mirrors Python's {@code OperationDef} dataclass in {@code sys_operation/registry.py}.
 */
public class OperationDef {

    private final Class<? extends BaseOperation> cls;
    private final String description;
    private final String name;
    private final OperationMode mode;

    public OperationDef(Class<? extends BaseOperation> cls, String name, OperationMode mode, String description) {
        this.cls = cls;
        this.name = name;
        this.mode = mode;
        this.description = description;
    }

    /**
     * Create an operation instance with the given configuration.
     * <p>
     * Tries a 4-arg constructor {@code (String, OperationMode, String, Object)} first,
     * then falls back to a single-arg {@code (Object)} constructor.
     *
     * @param runConfig runtime configuration (LocalWorkConfig or SandboxGatewayConfig)
     * @return the created operation instance
     */
    public BaseOperation createInstance(Object runConfig) {
        // Try 4-arg constructor: (name, mode, description, runConfig)
        try {
            Constructor<? extends BaseOperation> ctor = cls.getConstructor(
                    String.class, OperationMode.class, String.class, Object.class);
            return ctor.newInstance(name, mode, description, runConfig);
        } catch (NoSuchMethodException ignored) {
            // Fall through to single-arg constructor
        } catch (Exception e) {
            throw new RuntimeException("Failed to create operation instance: " + cls.getName(), e);
        }

        // Fallback: single-arg constructor (Object runConfig)
        try {
            Constructor<? extends BaseOperation> ctor = cls.getConstructor(Object.class);
            return ctor.newInstance(runConfig);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create operation instance: " + cls.getName()
                    + ". No suitable constructor found (tried 4-arg and 1-arg).", e);
        }
    }

    public Class<? extends BaseOperation> getCls() {
        return cls;
    }

    public String getDescription() {
        return description;
    }

    public String getName() {
        return name;
    }

    public OperationMode getMode() {
        return mode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OperationDef that = (OperationDef) o;
        return Objects.equals(cls, that.cls)
                && Objects.equals(name, that.name)
                && mode == that.mode
                && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cls, name, mode, description);
    }
}
