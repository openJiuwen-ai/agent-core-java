/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop;

import com.openjiuwen.core.sysop.local.LocalCodeOperation;
import com.openjiuwen.core.sysop.local.LocalFsOperation;
import com.openjiuwen.core.sysop.local.LocalShellOperation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Backward-compatible facade for the moved system operation package.
 *
 * <p>Mirrors Python's {@code SysOperation} in
 * {@code openjiuwen/core/sys_operation/sys_operation.py}.</p>
 *
 * @deprecated Use {@link com.openjiuwen.core.sys_operation.SysOperation}.
 */
@Deprecated(since = "0.1.14", forRemoval = false)
public class SysOperation {

    private final String id;
    private final OperationMode mode;
    private final Object runConfig;
    private final com.openjiuwen.core.sys_operation.SysOperation delegate;
    private final Map<String, BaseOperation> instances = new LinkedHashMap<>();

    public SysOperation(SysOperationCard card) {
        this.id = card.getId();
        this.mode = OperationMode.fromNewMode(card.getMode());
        this.delegate = new com.openjiuwen.core.sys_operation.SysOperation(card);
        this.runConfig = delegate.getRunConfig();
    }

    public OperationMode getLegacyMode() {
        return mode;
    }

    public String getId() {
        return id;
    }

    public OperationMode getMode() {
        return mode;
    }

    public Object getRunConfig() {
        return runConfig;
    }

    public String getIsolationKeyTemplate() {
        return delegate.getIsolationKeyTemplate();
    }

    public BaseOperation fs() {
        return getOperation("fs");
    }

    public BaseOperation code() {
        return getOperation("code");
    }

    public BaseOperation shell() {
        return getOperation("shell");
    }

    public BaseOperation getOperation(String name) {
        if (instances.containsKey(name)) {
            return instances.get(name);
        }
        BaseOperation operation = createOperation(name);
        if (operation != null) {
            instances.put(name, operation);
        }
        return operation;
    }

    private BaseOperation createOperation(String name) {
        if ("fs".equals(name)) {
            return new LocalFsOperation(runConfig);
        }
        if ("code".equals(name)) {
            return new LocalCodeOperation(runConfig);
        }
        if ("shell".equals(name)) {
            return new LocalShellOperation(runConfig);
        }
        return null;
    }
}
