/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.local;

import com.openjiuwen.core.sys_operation.Cwd;
import com.openjiuwen.core.sys_operation.CwdState;
import com.openjiuwen.core.sys_operation.BaseFsOperation.FileMode;
import com.openjiuwen.core.sys_operation.protocal.BaseFsProtocal;
import com.openjiuwen.core.sysop.BaseFsOperation;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.result.ReadFileData;
import com.openjiuwen.core.sysop.result.ReadFileResult;

import java.util.Map;

/**
 * Backward-compatible facade for the moved local file-system operation.
 *
 * <p>Mirrors Python's {@code FsOperation} in
 * {@code openjiuwen/core/sys_operation/local/fs_operation.py}.</p>
 *
 * @deprecated Use {@link com.openjiuwen.core.sys_operation.local.LocalFsOperation}.
 */
@Deprecated(since = "0.1.14", forRemoval = false)
public class LocalFsOperation extends BaseFsOperation {

    private final com.openjiuwen.core.sys_operation.local.LocalFsOperation delegate;

    public LocalFsOperation(Object runConfig) {
        super("fs", com.openjiuwen.core.sys_operation.OperationMode.LOCAL, "local fs operation", runConfig);
        this.delegate = new com.openjiuwen.core.sys_operation.local.LocalFsOperation(
                "fs",
                com.openjiuwen.core.sys_operation.OperationMode.LOCAL,
                "local fs operation",
                runConfig);
    }

    /**
     * Four-parameter constructor required by {@link com.openjiuwen.core.sys_operation.OperationDef#createInstance}.
     *
     * @deprecated Use {@link com.openjiuwen.core.sys_operation.local.LocalFsOperation}.
     */
    @Deprecated(since = "0.1.14", forRemoval = false)
    public LocalFsOperation(String name, com.openjiuwen.core.sys_operation.OperationMode mode,
                            String description, Object runConfig) {
        super(name, mode, description, runConfig);
        this.delegate = new com.openjiuwen.core.sys_operation.local.LocalFsOperation(
                name, mode, description, runConfig);
    }

    @Override
    public ReadFileResult readFile(String path,
                                   String mode,
                                   Integer head,
                                   Integer tail,
                                   int[] lineRange,
                                   String encoding,
                                   int chunkSize,
                                   Map<String, Object> options) {
        return withLegacyCwd(() -> {
            com.openjiuwen.core.sys_operation.result.ReadFileResult result = delegate.readFile(
                    path,
                    FileMode.fromValue(mode),
                    head,
                    tail,
                    toLineRange(lineRange),
                    encoding,
                    chunkSize,
                    options).join();
            return copyResult(result);
        });
    }

    private <T> T withLegacyCwd(SupplierWithException<T> supplier) {
        CwdState previous = Cwd.getState();
        CwdState snapshot = new CwdState(
                previous.getCwd(),
                previous.getOriginalCwd(),
                previous.getProjectRoot(),
                previous.getWorkspace(),
                previous.getTeamWorkspace());
        String workDir = workDir();
        if (workDir != null && !workDir.isBlank()) {
            Cwd.initCwd(workDir);
        }
        try {
            return supplier.get();
        } finally {
            Cwd.setState(snapshot);
        }
    }

    private String workDir() {
        Object config = getRunConfig();
        if (config instanceof LocalWorkConfig localWorkConfig) {
            return localWorkConfig.getWorkDir();
        }
        return null;
    }

    private static BaseFsProtocal.LineRange toLineRange(int[] lineRange) {
        if (lineRange == null || lineRange.length < 2) {
            return null;
        }
        return new BaseFsProtocal.LineRange(lineRange[0], lineRange[1]);
    }

    private static ReadFileResult copyResult(
            com.openjiuwen.core.sys_operation.result.ReadFileResult source) {
        ReadFileResult target = new ReadFileResult();
        target.setCode(source.getCode());
        target.setMessage(source.getMessage());
        target.setData(copyData(source.getData()));
        return target;
    }

    private static ReadFileData copyData(
            com.openjiuwen.core.sys_operation.result.ReadFileData source) {
        if (source == null) {
            return null;
        }
        ReadFileData target = new ReadFileData();
        target.setPath(source.getPath());
        target.setContent(source.getContent());
        target.setMode(source.getMode());
        return target;
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get();
    }
}
