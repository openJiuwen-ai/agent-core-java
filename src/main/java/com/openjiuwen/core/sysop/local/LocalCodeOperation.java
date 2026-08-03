/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.local;

import com.openjiuwen.core.sysop.BaseCodeOperation;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.result.ExecuteCodeChunkData;
import com.openjiuwen.core.sysop.result.ExecuteCodeData;
import com.openjiuwen.core.sysop.result.ExecuteCodeResult;
import com.openjiuwen.core.sysop.result.ExecuteCodeStreamResult;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;

/**
 * Backward-compatible facade for the moved local code operation.
 *
 * <p>Mirrors Python's {@code CodeOperation} in
 * {@code openjiuwen/core/sys_operation/local/code_operation.py}.</p>
 *
 * @deprecated Use {@link com.openjiuwen.core.sys_operation.local.LocalCodeOperation}.
 */
@Deprecated(since = "0.1.14", forRemoval = false)
public class LocalCodeOperation extends BaseCodeOperation {

    private final com.openjiuwen.core.sys_operation.local.LocalCodeOperation delegate;

    public LocalCodeOperation(Object runConfig) {
        super("code", com.openjiuwen.core.sys_operation.OperationMode.LOCAL, "local code operation", runConfig);
        this.delegate = new com.openjiuwen.core.sys_operation.local.LocalCodeOperation(
                "code",
                com.openjiuwen.core.sys_operation.OperationMode.LOCAL,
                "local code operation",
                runConfig);
    }

    /**
     * Four-parameter constructor required by {@link com.openjiuwen.core.sys_operation.OperationDef#createInstance}.
     *
     * @deprecated Use {@link com.openjiuwen.core.sys_operation.local.LocalCodeOperation}.
     */
    @Deprecated(since = "0.1.14", forRemoval = false)
    public LocalCodeOperation(String name, com.openjiuwen.core.sys_operation.OperationMode mode,
                              String description, Object runConfig) {
        super(name, mode, description, runConfig);
        this.delegate = new com.openjiuwen.core.sys_operation.local.LocalCodeOperation(
                name, mode, description, runConfig);
    }

    @Override
    public ExecuteCodeResult executeCode(String code,
                                         String language,
                                         int timeout,
                                         Map<String, String> environment,
                                         Map<String, Object> options) {
        com.openjiuwen.core.sys_operation.result.ExecuteCodeResult result = delegate.executeCode(
                code,
                language,
                timeout,
                environment,
                workDir(),
                options).join();
        return copyResult(result);
    }

    @Override
    public Iterator<ExecuteCodeStreamResult> executeCodeStream(String code,
                                                               String language,
                                                               int timeout,
                                                               Map<String, String> environment,
                                                               Map<String, Object> options) {
        Flow.Publisher<com.openjiuwen.core.sys_operation.result.ExecuteCodeStreamResult> publisher =
                delegate.executeCodeStream(code, language, timeout, environment, workDir(), options);
        return collect(publisher).iterator();
    }

    private String workDir() {
        Object config = getRunConfig();
        if (config instanceof LocalWorkConfig localWorkConfig) {
            return localWorkConfig.getWorkDir();
        }
        return null;
    }

    private static List<ExecuteCodeStreamResult> collect(
            Flow.Publisher<com.openjiuwen.core.sys_operation.result.ExecuteCodeStreamResult> publisher) {
        List<ExecuteCodeStreamResult> results = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(com.openjiuwen.core.sys_operation.result.ExecuteCodeStreamResult item) {
                results.add(copyStreamResult(item));
            }

            @Override
            public void onError(Throwable throwable) {
                done.countDown();
            }

            @Override
            public void onComplete() {
                done.countDown();
            }
        });
        try {
            done.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        return results;
    }

    private static ExecuteCodeResult copyResult(
            com.openjiuwen.core.sys_operation.result.ExecuteCodeResult source) {
        ExecuteCodeResult target = new ExecuteCodeResult();
        target.setCode(source.getCode());
        target.setMessage(source.getMessage());
        target.setData(copyData(source.getData()));
        return target;
    }

    private static ExecuteCodeStreamResult copyStreamResult(
            com.openjiuwen.core.sys_operation.result.ExecuteCodeStreamResult source) {
        ExecuteCodeStreamResult target = new ExecuteCodeStreamResult();
        target.setCode(source.getCode());
        target.setMessage(source.getMessage());
        target.setData(copyChunkData(source.getData()));
        return target;
    }

    private static ExecuteCodeData copyData(
            com.openjiuwen.core.sys_operation.result.ExecuteCodeData source) {
        if (source == null) {
            return null;
        }
        ExecuteCodeData target = new ExecuteCodeData();
        target.setCodeContent(source.getCodeContent());
        target.setLanguage(source.getLanguage());
        target.setExitCode(source.getExitCode());
        target.setStdout(source.getStdout());
        target.setStderr(source.getStderr());
        return target;
    }

    private static ExecuteCodeChunkData copyChunkData(
            com.openjiuwen.core.sys_operation.result.ExecuteCodeChunkData source) {
        if (source == null) {
            return null;
        }
        ExecuteCodeChunkData target = new ExecuteCodeChunkData();
        target.setText(source.getText());
        target.setType(source.getType());
        target.setChunkIndex(source.getChunkIndex());
        target.setExitCode(source.getExitCode());
        target.setMetadata(source.getMetadata());
        return target;
    }
}
