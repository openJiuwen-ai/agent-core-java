/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner;

import com.openjiuwen.core.runner.resourcemanager.ResourceMgr;
import com.openjiuwen.core.runner.mq.LocalMessageQueue;
import com.openjiuwen.core.runner.callback.CallbackFramework;

import java.util.Map;

/**
 * Runner implementation class.
 *
 * <p>Legacy implementation retained for backward compatibility.
 * The active runner is {@link Runner}.</p>
 *
 * @deprecated Use {@link Runner} instead.
 */
@Deprecated
public class RunnerImpl {

    private static final String DEFAULT_RUNNER_ID = "global";
    private final String runnerId;
    private final ResourceMgr resourceManager;
    private final LocalMessageQueue messageQueue;
    private final CallbackFramework callbackFramework;

    public RunnerImpl() {
        this(DEFAULT_RUNNER_ID, null);
    }

    public RunnerImpl(String runnerId, RunnerConfig config) {
        this.runnerId = runnerId != null ? runnerId : DEFAULT_RUNNER_ID;
        this.resourceManager = new ResourceMgr();
        this.messageQueue = new LocalMessageQueue();
        this.callbackFramework = new CallbackFramework();

        if (config != null) {
            RunnerConfig.setRunnerConfig(config);
        } else {
            RunnerConfig.setRunnerConfig(RunnerConfig.DEFAULT);
        }
    }

    public ResourceMgr getResourceMgr() {
        return resourceManager;
    }

    public LocalMessageQueue getPubsub() {
        return messageQueue;
    }

    public CallbackFramework getCallbackFramework() {
        return callbackFramework;
    }

    public boolean start() {
        return messageQueue.start();
    }

    public boolean stop() {
        try {
            messageQueue.stop();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            resourceManager.release();
        }
    }

    public void setConfig(RunnerConfig config) {
        RunnerConfig.setRunnerConfig(config);
    }

    public RunnerConfig getConfig() {
        return RunnerConfig.getRunnerConfig();
    }

    public Object runWorkflow(Object workflow, Object inputs, Object session,
                              com.openjiuwen.core.context.ModelContext context, Map<String, Object> envs) {
        throw new UnsupportedOperationException("Use Runner.runWorkflow instead");
    }

    public Object runAgent(Object agent, Object inputs, Object session,
                           com.openjiuwen.core.context.ModelContext context, Map<String, Object> envs) {
        throw new UnsupportedOperationException("Use Runner.runAgent instead");
    }

    public Object runAgentGroup(Object agentGroup, Object inputs, Object session,
                                com.openjiuwen.core.context.ModelContext context, Map<String, Object> envs) {
        throw new UnsupportedOperationException("Use Runner.runAgentGroup instead");
    }

    public void release(String sessionId) {
        // no-op for backward compatibility
    }
}
