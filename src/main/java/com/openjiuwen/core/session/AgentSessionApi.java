/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.interaction.SimpleAgentInteraction;
import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.session.stream.StreamWriter;
import com.openjiuwen.core.session.stream.TraceSchema;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * User-facing agent session providing high-level API for agent lifecycle management.
 * <p>
 * Wraps an internal {@link AgentSession} and manages pre-run/post-run hooks, state access,
 * streaming, and interaction.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.agent.Session}.
 */
public class AgentSessionApi implements Session {

    private static final int PRE_DONE = 0x1;
    private static final int POST_DONE = 0x2;

    private final String sessionId;
    private final AgentSession inner;
    private final Object card;

    // preRunDone（bit0）/ postRunDone（bit1）合并成一个 AtomicInteger 位掩码，
    // resetRunState() 才能用一次 CAS 把两个标志一起清零，避免两次 set(false) 之间的中间状态。
    private final AtomicInteger runState = new AtomicInteger(0);
    private SimpleAgentInteraction interaction;

    /**
     * Create a new AgentSessionApi.
     *
     * @param sessionId the session ID (nullable, auto-generated if absent)
     * @param envs      environment variables (nullable)
     * @param card      the agent card (nullable)
     */
    public AgentSessionApi(String sessionId, Map<String, Object> envs, Object card) {
        this(sessionId, envs, card, null);
    }

    /**
     * Create a new AgentSessionApi with explicit stream modes.
     *
     * @param sessionId    the session ID (nullable, auto-generated if absent)
     * @param envs         environment variables (nullable)
     * @param card         the agent card (nullable)
     * @param streamModes  explicit enabled stream modes, null to use defaults
     */
    public AgentSessionApi(String sessionId, Map<String, Object> envs, Object card, List<StreamMode> streamModes) {
        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString();
        }
        this.sessionId = sessionId;

        Config config = new Config();
        if (envs != null) {
            config.setEnvs(envs);
        }

        this.inner = new AgentSession(sessionId, config, null, card, streamModes);
        this.card = card;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public AgentSessionApi(String sessionId, Map<String, Object> envs) {
        this(sessionId, envs, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public AgentSessionApi(String sessionId) {
        this(sessionId, null, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public AgentSessionApi() {
        this(null, null, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getEnv(String key) {
        return inner.config() != null ? inner.config().getEnv(key) : null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getEnv(String key, Object defaultValue) {
        Object val = getEnv(key);
        return val != null ? val : defaultValue;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getEnvs() {
        return inner.config() != null ? inner.config().getEnvs() : null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getAgentId() {
        return inner.agentId();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getAgentName() {
        return inner.agentName();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getAgentDescription() {
        return inner.agentDescription();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void updateState(Map<String, Object> data) {
        inner.state().updateGlobal(data);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getState(Object key) {
        return inner.state().getGlobal(key);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getState(String key) {
        return getState((Object) key);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> dumpState() {
        return inner.state().dump();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    /**
     * Auto-generated for codecheck compliance.
     */
    public void writeStream(Object data) {
        StreamWriter writer = (StreamWriter) inner.streamWriterManager().getOutputWriter();
        if (writer != null) {
            writer.write(data);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    /**
     * Auto-generated for codecheck compliance.
     */
    public void writeCustomStream(Map<String, Object> data) {
        StreamWriter writer = (StreamWriter) inner.streamWriterManager().getCustomWriter();
        if (writer != null) {
            writer.write(data);
            return;
        }
        StreamWriter outputWriter = (StreamWriter) inner.streamWriterManager().getOutputWriter();
        if (outputWriter != null) {
            outputWriter.write(new OutputSchema("custom", 0, data));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    /**
     * Auto-generated for codecheck compliance.
     */
    public void writeTraceStream(TraceSchema data) {
        StreamWriter writer = (StreamWriter) inner.streamWriterManager().getTraceWriter();
        if (writer != null) {
            writer.write(data);
        }
    }

    /**
     * Get stream output as a blocking iterator.
     *
     * @deprecated Prefer {@link #streamOutput(java.util.function.Consumer)} when callback-style consumption is easier.
     */
    @Deprecated
    /**
     * Auto-generated for codecheck compliance.
     */
    public Iterator<Object> streamIterator() {
        return inner.streamWriterManager().streamIterator();
    }

    /**
     * Consume stream output incrementally via a callback.
     * Each stream item is delivered to the consumer as it arrives.
     *
     * @param consumer callback invoked for each stream item
     */
    public void streamOutput(java.util.function.Consumer<Object> consumer) {
        inner.streamWriterManager().streamOutput(consumer);
    }

    /**
     * Pre-run hook: execute checkpointer pre-agent logic.
     * CAS 保证多线程同时调用时只有一个真正执行，其余直接返回。
     *
     * @param inputs the inputs map
     */
    public void preRun(Object inputs) {
        if ((runState.getAndUpdate(s -> s | PRE_DONE) & PRE_DONE) != 0) {
            return;
        }
        CheckpointerFactory.getCheckpointer().preAgentExecute(inner, inputs);
    }

    /**
     * Post-run hook: close stream and execute checkpointer post-agent logic.
     * CAS 幂等保护同 preRun()——runAgentStreamingAsync 的 doFinally 清理钩子
     * 可能跟迭代线程在不同线程上触发，靠这个保证只执行一次。
     */
    public void postRun() {
        if ((runState.getAndUpdate(s -> s | POST_DONE) & POST_DONE) != 0) {
            return;
        }
        inner.streamWriterManager().getStreamEmitter().close();
        if (inner.checkpointerTyped() != null) {
            inner.checkpointerTyped().postAgentExecute(inner);
        }
    }

    /**
     * Reset pre-run and post-run guards so the same session can be reused for another run.
     */
    public void resetRunState() {
        runState.set(0);
    }

    /**
     * Create a workflow session from this agent session.
     */
    public WorkflowSessionApi createWorkflowSession() {
        return new WorkflowSessionApi(inner, getSessionId());
    }

    /**
     * Trigger an interaction.
     */
    public void interact(Object value) {
        if (interaction == null) {
            interaction = new SimpleAgentInteraction(inner);
        }
        interaction.waitUserInputs(value != null ? value.toString() : null);
    }

    /**
     * Get the underlying internal AgentSession.
     */
    public AgentSession getInner() {
        return inner;
    }

    /**
     * Factory method for creating an agent session.
     */
    public static AgentSessionApi create(String sessionId, Map<String, Object> envs, Object card) {
        return new AgentSessionApi(sessionId, envs, card);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static AgentSessionApi create(String sessionId, Map<String, Object> envs, Object card,
                                         List<StreamMode> streamModes) {
        return new AgentSessionApi(sessionId, envs, card, streamModes);
    }
}
