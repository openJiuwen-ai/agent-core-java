/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
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
 * 
 * @since 0.1.7
 */
public class AgentSessionApi implements Session {
    private static final int PRE_DONE = 0x1;
    private static final int POST_DONE = 0x2;

    private final String sessionId;
    private final AgentSession inner;
    private final Object card;

    // preRunDone（bit0）/ postRunDone（bit1）合并成一个 AtomicInteger 位掩码，
    // resetRunState() 才能用一次 CAS 把两个标志一起清零，避免两次 set(false) 之间的中间状态。
    /**
     * AtomicInteger.
     * 
     * @since 0.1.7
     */
    private final AtomicInteger runState = new AtomicInteger(0);
    private SimpleAgentInteraction interaction;
    private TenantContext tenantContext;

    // checkpointer 的 session key 由线程租户推导，而 preRun 与 postRun 可能落在不同线程
    // （流式场景 postRun 在池线程执行）。这里固化 preRun 时刻解析出的租户，postRun 复用它，
    // 保证两端推导出同一个 key；否则会查不到 preRun 创建的 agent store（BUG-0005）。
    private volatile TenantContext checkpointerTenant;
    private volatile boolean checkpointerTenantResolved;

    /**
     * Create a new AgentSessionApi.
     * 
     * @param sessionId the session ID (nullable, auto-generated if absent)
     * @param envs environment variables (nullable)
     * @param card the agent card (nullable)
     * @since 0.1.7
     */
    public AgentSessionApi(String sessionId, Map<String, Object> envs, Object card) {
        this(sessionId, envs, card, null);
    }

    /**
     * Create a new AgentSessionApi with explicit stream modes.
     * 
     * @param sessionId the session ID (nullable, auto-generated if absent)
     * @param envs environment variables (nullable)
     * @param card the agent card (nullable)
     * @param streamModes explicit enabled stream modes, null to use defaults
     * @since 0.1.7
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
     * AgentSessionApi.
     * 
     * @param sessionId sessionId
     * @param envs envs
     * @since 0.1.7
     */
    public AgentSessionApi(String sessionId, Map<String, Object> envs) {
        this(sessionId, envs, null);
    }

    /**
     * AgentSessionApi.
     * 
     * @param sessionId sessionId
     * @since 0.1.7
     */
    public AgentSessionApi(String sessionId) {
        this(sessionId, null, null);
    }

    /**
     * AgentSessionApi.
     * 
     * @since 0.1.7
     */
    public AgentSessionApi() {
        this(null, null, null);
    }

    /**
     * getSessionId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * getEnv.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    public Object getEnv(String key) {
        return inner.config() != null ? inner.config().getEnv(key) : null;
    }

    /**
     * getEnv.
     * 
     * @param key key
     * @param defaultValue defaultValue
     * @return the result
     * @since 0.1.7
     */
    public Object getEnv(String key, Object defaultValue) {
        Object val = getEnv(key);
        return val != null ? val : defaultValue;
    }

    /**
     * getEnvs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getEnvs() {
        return inner.config() != null ? inner.config().getEnvs() : null;
    }

    /**
     * getAgentId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getAgentId() {
        return inner.agentId();
    }

    /**
     * getAgentName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getAgentName() {
        return inner.agentName();
    }

    /**
     * getAgentDescription.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getAgentDescription() {
        return inner.agentDescription();
    }

    /**
     * updateState.
     * 
     * @param data data
     * @since 0.1.7
     */
    public void updateState(Map<String, Object> data) {
        inner.state().updateGlobal(data);
    }

    /**
     * getState.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    public Object getState(Object key) {
        return inner.state().getGlobal(key);
    }

    /**
     * getState.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object getState(String key) {
        return getState((Object) key);
    }

    /**
     * dumpState.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> dumpState() {
        return inner.state().dump();
    }

    /**
     * writeStream.
     *
     * @param data data
     * @since 0.1.7
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void writeStream(Object data) {
        StreamWriter<?> writer = inner.streamWriterManager().getOutputWriter();
        if (writer != null) {
            writer.write(data);
        }
    }

    /**
     * Check whether the underlying stream emitter has been closed.
     *
     * <p>Used by streaming callers (e.g. {@code ReActAgent.railedModelStreamCall})
     * to break out of an {@code Iterator.hasNext()} loop early once the session
     * is finished, instead of continuing to consume upstream LLM chunks that
     * would only be discarded by {@code StreamWriter.doWrite}.</p>
     *
     * @return {@code true} if the stream emitter is null or has been closed;
     *         {@code false} otherwise
     * @since 0.1.7
     */
    public boolean isStreamClosed() {
        var manager = inner.streamWriterManager();
        return manager == null
                || manager.getStreamEmitter() == null
                || manager.getStreamEmitter().isClosed();
    }

    /**
     * writeCustomStream.
     * 
     * @param data data
     * @since 0.1.7
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void writeCustomStream(Map<String, Object> data) {
        StreamWriter<?> writer = inner.streamWriterManager().getCustomWriter();
        if (writer != null) {
            writer.write(data);
            return;
        }
        StreamWriter<?> outputWriter = inner.streamWriterManager().getOutputWriter();
        if (outputWriter != null) {
            outputWriter.write(new OutputSchema("custom", 0, data));
        }
    }

    /**
     * writeTraceStream.
     * 
     * @param data data
     * @since 0.1.7
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void writeTraceStream(TraceSchema data) {
        StreamWriter<?> writer = inner.streamWriterManager().getTraceWriter();
        if (writer != null) {
            writer.write(data);
        }
    }

    /**
     * streamIterator.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Deprecated
    public Iterator<Object> streamIterator() {
        return inner.streamWriterManager().streamIterator();
    }

    /**
     * Consume stream output incrementally via a callback.
     * Each stream item is delivered to the consumer as it arrives.
     * 
     * @param consumer callback invoked for each stream item
     * @since 0.1.7
     */
    public void streamOutput(java.util.function.Consumer<Object> consumer) {
        inner.streamWriterManager().streamOutput(consumer);
    }

    /**
     * Pre-run hook: execute checkpointer pre-agent logic.
     * CAS 保证多线程同时调用时只有一个真正执行，其余直接返回。
     * 
     * @param inputs the inputs map
     * @since 0.1.7
     */
    public void preRun(Object inputs) {
        if ((runState.getAndUpdate(s -> s | PRE_DONE) & PRE_DONE) != 0) {
            return;
        }
        resolveCheckpointerTenant();
        CheckpointerFactory.getCheckpointer().preAgentExecute(inner, inputs);
    }

    /**
     * Post-run hook: close stream and execute checkpointer post-agent logic.
     * CAS 幂等保护同 preRun()——runAgentStreamingAsync 的 doFinally 清理钩子
     * 可能跟迭代线程在不同线程上触发，靠这个保证只执行一次。
     * 
     * @since 0.1.7
     */
    public void postRun() {
        if ((runState.getAndUpdate(s -> s | POST_DONE) & POST_DONE) != 0) {
            return;
        }
        // Close_stream first, then commit/post_agent_execute.
        inner.streamWriterManager().getStreamEmitter().close();
        if (inner.checkpointerTyped() != null) {
            runWithCheckpointerTenant(() -> inner.checkpointerTyped().postAgentExecute(inner));
        }
    }

    /**
     * Resolve and remember the tenant that the checkpointer actually consumed during preRun.
     * <p>
     * 必须直接取当前线程值：preAgentExecute 本身就是按线程租户推导 key，若改用其他来源
     * （例如会话自带租户）会让 postRun 复用一个 preRun 从未使用过的值，反而制造不一致。
     *
     * @since 0.1.7
     */
    private void resolveCheckpointerTenant() {
        TenantContext ctx = TenantContextHolder.getCurrentTenant();
        checkpointerTenant = (ctx != null && ctx.isTenantAware()) ? ctx : null;
        checkpointerTenantResolved = true;
    }

    /**
     * Run the given action with the tenant captured at pre-run time bound to the current thread.
     * <p>
     * postRun 通常在池线程上执行，该线程的 ThreadLocal 既可能为空，也可能残留继承自其他请求的
     * 租户；若此时按线程租户推导 session key，就会与 preRun 不一致。这里显式复用捕获值，
     * 结束后恢复线程原值。未经过 preRun 的会话保持原有线程语义不变。
     *
     * @param action the action to run
     * @since 0.1.7
     */
    private void runWithCheckpointerTenant(Runnable action) {
        if (!checkpointerTenantResolved) {
            action.run();
            return;
        }
        TenantContext previous = TenantContextHolder.getCurrentTenant();
        try {
            // setCurrentTenant(null) 会覆盖从父线程继承来的残留租户，使推导结果回到无租户语义。
            TenantContextHolder.setCurrentTenant(checkpointerTenant);
            action.run();
        } finally {
            if (previous == null) {
                TenantContextHolder.clearCurrentTenant();
            } else {
                TenantContextHolder.setCurrentTenant(previous);
            }
        }
    }

    /**
     * Reset pre-run and post-run guards so the same session can be reused for another run.
     * 
     * @since 0.1.7
     */
    public void resetRunState() {
        runState.set(0);
        // 会话可能被复用给另一租户，清除上一轮捕获的租户，由下一次 preRun 重新解析。
        checkpointerTenant = null;
        checkpointerTenantResolved = false;
    }

    /**
     * Copy only the PRE_DONE bit from the given session, leaving any already-set
     * POST_DONE bit untouched. Called before preRun on a downstream/effective
     * session so its preRun CAS guard is skipped (avoids a redundant checkpoint
     * read) without accidentally importing a stale POST_DONE from a reused
     * upstream session.
     *
     * @param source the session whose PRE_DONE bit to copy
     * @since 0.1.15
     */
    public void copyPreRunState(AgentSessionApi source) {
        if (source != null && (source.runState.get() & PRE_DONE) != 0) {
            runState.getAndUpdate(s -> s | PRE_DONE);
            // 本实例因沿用上游状态而跳过 preRun，仍需固化租户供 postRun 复用（BUG-0005）；
            // 此处运行在调用线程，租户仍可用。
            resolveCheckpointerTenant();
        }
    }

    /**
     * Copy the full run-state bitmask from the given session.
     * Used after postRun to propagate downstream's PRE_DONE | POST_DONE back to
     * the upstream session so upstream postRun is also skipped (checkpoint was
     * already saved by the downstream session).
     *
     * @param source the session whose run-state to copy
     * @since 0.1.15
     */
    public void copyRunState(AgentSessionApi source) {
        if (source != null) {
            runState.set(source.runState.get());
        }
    }

    /**
     * Create a workflow session from this agent session.
     * 
     * @return the result
     * @since 0.1.7
     */
    public WorkflowSessionApi createWorkflowSession() {
        return new WorkflowSessionApi(inner, getSessionId());
    }

    /**
     * Trigger an interaction.
     * 
     * @param value value
     * @since 0.1.7
     */
    public void interact(Object value) {
        if (interaction == null) {
            interaction = new SimpleAgentInteraction(inner);
        }
        interaction.waitUserInputs(value != null ? value.toString() : null);
    }

    /**
     * Get the underlying internal AgentSession.
     * 
     * @return the result
     * @since 0.1.7
     */
    public AgentSession getInner() {
        return inner;
    }

    /**
     * Get the tenant context associated with this session.
     *
     * @return the tenant context, or null if not set
     * @since 0.1.7
     */
    public TenantContext getTenantContext() {
        return tenantContext;
    }

    /**
     * Set the tenant context for this session (chain-style).
     *
     * @param ctx the tenant context to associate (nullable)
     * @return this session for chaining
     * @since 0.1.7
     */
    public AgentSessionApi withTenantContext(TenantContext ctx) {
        this.tenantContext = ctx;
        return this;
    }

    /**
     * Factory method for creating an agent session.
     * 
     * @param sessionId sessionId
     * @param envs envs
     * @param card card
     * @return the result
     * @since 0.1.7
     */
    public static AgentSessionApi create(String sessionId, Map<String, Object> envs, Object card) {
        return new AgentSessionApi(sessionId, envs, card);
    }

    /**
     * create.
     * 
     * @param sessionId sessionId
     * @param envs envs
     * @param card card
     * @param streamModes streamModes
     * @return the result
     * @since 0.1.7
     */
    public static AgentSessionApi create(String sessionId, Map<String, Object> envs, Object card,
            List<StreamMode> streamModes) {
        return new AgentSessionApi(sessionId, envs, card, streamModes);
    }
}
