/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

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

/**
 * User-facing agent session providing high-level API for agent lifecycle management.
 * <p>
 * Wraps an internal {@link AgentSession} and manages pre-run/post-run hooks, state access,
 * streaming, and interaction.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.agent.Session}.
 */
public class AgentSessionApi implements Session {

    private final String sessionId;
    private final AgentSession inner;
    private final Object card;
    private boolean preRunDone;
    private boolean postRunDone;
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
        this.preRunDone = false;
        this.postRunDone = false;
    }

    public AgentSessionApi(String sessionId, Map<String, Object> envs) {
        this(sessionId, envs, null);
    }

    public AgentSessionApi(String sessionId) {
        this(sessionId, null, null);
    }

    public AgentSessionApi() {
        this(null, null, null);
    }

    public String getSessionId() {
        return sessionId;
    }

    public Object getEnv(String key) {
        return inner.config() != null ? inner.config().getEnv(key) : null;
    }

    public Object getEnv(String key, Object defaultValue) {
        Object val = getEnv(key);
        return val != null ? val : defaultValue;
    }

    public Map<String, Object> getEnvs() {
        return inner.config() != null ? inner.config().getEnvs() : null;
    }

    public String getAgentId() {
        return inner.agentId();
    }

    public String getAgentName() {
        return inner.agentName();
    }

    public String getAgentDescription() {
        return inner.agentDescription();
    }

    public void updateState(Map<String, Object> data) {
        inner.state().updateGlobal(data);
    }

    public Object getState(Object key) {
        return inner.state().getGlobal(key);
    }

    @Override
    public Object getState(String key) {
        return getState((Object) key);
    }

    public Map<String, Object> dumpState() {
        return inner.state().dump();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void writeStream(Object data) {
        StreamWriter writer = (StreamWriter) inner.streamWriterManager().getOutputWriter();
        if (writer != null) {
            writer.write(data);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void writeCustomStream(Map<String, Object> data) {
        StreamWriter writer = (StreamWriter) inner.streamWriterManager().getCustomWriter();
        if (writer != null) {
            writer.write(data);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
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
     *
     * @param inputs the inputs map
     */
    public void preRun(Object inputs) {
        if (preRunDone) {
            return;
        }
        CheckpointerFactory.getCheckpointer().preAgentExecute(inner, inputs);
        preRunDone = true;
    }

    /**
     * Post-run hook: close stream and execute checkpointer post-agent logic.
     */
    public void postRun() {
        if (postRunDone) {
            return;
        }
        inner.streamWriterManager().getStreamEmitter().close();
        if (inner.checkpointerTyped() != null) {
            inner.checkpointerTyped().postAgentExecute(inner);
        }
        postRunDone = true;
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

    public static AgentSessionApi create(String sessionId, Map<String, Object> envs, Object card,
                                         List<StreamMode> streamModes) {
        return new AgentSessionApi(sessionId, envs, card, streamModes);
    }
}
