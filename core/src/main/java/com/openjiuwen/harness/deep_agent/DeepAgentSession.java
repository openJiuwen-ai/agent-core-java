/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deep_agent;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepAgent product session. Delegates to the official {@link AgentSession} facade
 * so state, stream, and lifecycle share one kernel.
 *
 * <p>The legacy {@code config} constructor argument accepts an environment map whose keys
 * must be non-null strings. Invalid keys fail construction with {@link IllegalArgumentException}.
 * Other configuration objects retain the default environment behavior.</p>
 *
 * @since 0.1.14
 */
public class DeepAgentSession implements AgentSessionApi {

    private final AgentSession facade;

    public DeepAgentSession(String sessionId) {
        this.facade = new AgentSession(sessionId, null, null);
    }

    /**
     * Creates a session with request environments in the backing configuration.
     *
     * @param sessionId session identifier
     * @param config environment map with non-null String keys; null or non-Map values use defaults
     * @param card agent card
     * @throws IllegalArgumentException if an environment map contains a null or non-String key
     * @since 0.1.17
     */
    public DeepAgentSession(String sessionId, Object config, AgentCard card) {
        this.facade = new AgentSession(sessionId, environments(config), card);
    }

    /**
     * Creates a session using the legacy stream-mode constructor.
     *
     * @param sessionId session identifier
     * @param config environment map with non-null String keys; null or non-Map values use defaults
     * @param card agent card
     * @param streamModes legacy stream modes, currently ignored
     * @throws IllegalArgumentException if an environment map contains a null or non-String key
     * @since 0.1.17
     */
    public DeepAgentSession(String sessionId, Object config, AgentCard card, List<StreamMode> streamModes) {
        this(sessionId, config, card);
    }

    /**
     * Creates a session using the legacy checkpointer constructor.
     *
     * @param sessionId session identifier
     * @param config environment map with non-null String keys; null or non-Map values use defaults
     * @param checkpointer legacy checkpointer, currently ignored
     * @param card agent card
     * @throws IllegalArgumentException if an environment map contains a null or non-String key
     * @since 0.1.17
     */
    public DeepAgentSession(String sessionId, Object config, Object checkpointer, AgentCard card) {
        this(sessionId, config, card);
    }

    private static Map<String, Object> environments(Object config) {
        if (!(config instanceof Map<?, ?> values)) {
            return Map.of();
        }
        Map<String, Object> envs = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("Environment keys must be strings");
            }
            envs.put(key, entry.getValue());
        }
        return envs;
    }

    @Override
    public String getSessionId() {
        return facade.getSessionId();
    }

    @Override
    public Object getState(String key) {
        return facade.getState(key);
    }

    @Override
    public void updateState(Map<String, Object> data) {
        facade.updateState(data);
    }

    @Override
    public void writeStream(Object data) {
        facade.writeStream(data);
    }

    @Override
    public Iterator<Object> streamIterator() {
        return facade.streamIterator();
    }

    @Override
    public Iterator<Object> streamIterator(RuntimeException failure) {
        return facade.streamIterator(failure);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long renewModelCallTimeout(long leaseId, long idleTimeoutMs, long delayMs) {
        return facade.renewModelCallTimeout(leaseId, idleTimeoutMs, delayMs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endModelCallTimeout(long leaseId) {
        facade.endModelCallTimeout(leaseId);
    }

    @Override
    public TenantContext getTenantContext() {
        return facade.getTenantContext();
    }

    @Override
    public DeepAgentSession withTenantContext(TenantContext ctx) {
        facade.withTenantContext(ctx);
        return this;
    }

    @Override
    public DeepAgentSession preRun(Map<String, Object> inputs) {
        facade.preRun(inputs == null ? Map.of() : Map.of("inputs", inputs));
        return this;
    }

    @Override
    public void markPreRunDone() {
        facade.markPreRunDone();
    }

    @Override
    public void markPostRunDone() {
        facade.markPostRunDone();
    }

    @Override
    public boolean isPreRunDone() {
        return facade.isPreRunDone();
    }

    @Override
    public boolean isPostRunDone() {
        return facade.isPostRunDone();
    }

    @Override
    public void resetPostRunState() {
        facade.resetPostRunState();
    }

    public void postRun() {
        facade.postRun();
    }

    @Override
    public void closeStream() {
        facade.closeStream();
    }

    @Override
    public void commit() {
        facade.commit();
    }

    public com.openjiuwen.core.session.internal.AgentSession getInner() {
        return facade.getInner();
    }

    /**
     * Returns the environment snapshot used by the underlying session.
     *
     * @return environment snapshot
     * @since 0.1.17
     */
    @Override
    public Map<String, Object> getEnvs() {
        return facade.getEnvs();
    }

    /**
     * Merges environment values into the underlying session configuration.
     *
     * @param envs values to merge; {@code null} leaves existing values unchanged
     * @since 0.1.17
     */
    public void setEnvs(Map<String, Object> envs) {
        facade.getInner().config().setEnvs(envs);
    }
}
