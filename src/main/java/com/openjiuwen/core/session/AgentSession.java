/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import com.openjiuwen.core.runner.callback.AsyncCallbackFramework;
import com.openjiuwen.core.runner.callback.CallbackUtils;
import com.openjiuwen.core.runner.callback.SessionEvents;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.interaction.SimpleAgentInteraction;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamWriterManager;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Public single-agent session facade.
 *
 * <p>Mirrors Python's {@code Session} in
 * {@code openjiuwen/core/session/agent.py}.</p>
 */
public class AgentSession implements AgentSessionApi {

    private final String sessionId;
    private final Object card;
    private final com.openjiuwen.core.session.internal.AgentSession inner;
    private boolean preRunDone;
    private boolean postRunDone;
    private SimpleAgentInteraction interaction;
    private final boolean closeStreamOnPostRun;
    private final Map<String, Object> sourceMetadata;

    public AgentSession(String sessionId, Map<String, Object> envs, Object card,
                        StreamWriterManager streamWriterManager,
                        boolean closeStreamOnPostRun,
                        Map<String, Object> sourceMetadata) {
        this.sessionId = sessionId == null ? UUID.randomUUID().toString() : sessionId;
        Config config = new Config();
        if (envs != null) {
            config.setEnvs(envs);
        }
        this.inner = new com.openjiuwen.core.session.internal.AgentSession(
                this.sessionId, config, card, streamWriterManager);
        this.card = card;
        this.closeStreamOnPostRun = closeStreamOnPostRun;
        this.sourceMetadata = sourceMetadata == null ? Map.of() : new LinkedHashMap<>(sourceMetadata);
    }

    public AgentSession(String sessionId, Map<String, Object> envs, Object card) {
        this(sessionId, envs, card, null, true, null);
    }

    public AgentSession() {
        this(null, null, null);
    }

    public static AgentSession createAgentSession(String sessionId, Map<String, Object> envs, Object card) {
        return new AgentSession(sessionId, envs, card);
    }

    public String getSessionId() {
        return sessionId;
    }

    public Object getEnv(String key) {
        return inner.config().getEnv(key);
    }

    public Object getEnv(String key, Object defaultValue) {
        return inner.config().getEnv(key, defaultValue);
    }

    public Map<String, Object> getEnvs() {
        return inner.config().getEnvs();
    }

    public Object getAgentId() {
        return readCardProperty("getId", sessionId);
    }

    public Object getAgentName() {
        return readCardProperty("getName", "");
    }

    public Object getAgentDescription() {
        return readCardProperty("getDescription", "");
    }

    public void updateState(Map<String, Object> data) {
        inner.state().updateGlobal(data);
    }

    @Override
    public Object getState(String key) {
        return getState((Object) key);
    }

    public Object getState(Object key) {
        return inner.state().getGlobal(key);
    }

    public Map<String, Object> dumpState() {
        return inner.state().dump();
    }

    public void writeStream(Object data) {
        OutputSchema streamData = normalizeOutput(tagStreamPayload(data));
        triggerWriteStream(streamData);
        inner.streamWriterManager().getOutputWriter().write(streamData);
    }

    public void writeCustomStream(Object data) {
        Object streamData = tagStreamPayload(data);
        triggerWriteStream(streamData);
        inner.streamWriterManager().getCustomWriter().write(streamData);
    }

    public Iterator<Object> streamIterator() {
        return inner.streamWriterManager().streamIterator();
    }

    public void closeStream() {
        inner.streamWriterManager().streamEmitter().close();
        if (CallbackUtils.getCallbackFramework() instanceof AsyncCallbackFramework framework) {
            framework.unregisterEvent(sessionId + "write_stream");
        }
    }

    public AgentSession preRun(Map<String, Object> kwargs) {
        if (preRunDone) {
            return this;
        }
        Map<String, Object> callbackKwargs = new LinkedHashMap<>();
        callbackKwargs.put("session_id", getSessionId());
        callbackKwargs.put("card", card);
        callbackKwargs.put("session", this);
        CallbackUtils.trigger(SessionEvents.AGENT_SESSION_CREATED, callbackKwargs);
        Object inputs = kwargs == null ? null : kwargs.get("inputs");
        if (inner.checkpointer() instanceof com.openjiuwen.core.session.checkpointer.Checkpointer checkpointer) {
            checkpointer.preAgentExecute(inner, inputs);
        }
        preRunDone = true;
        return this;
    }

    public AgentSession postRun() {
        if (postRunDone) {
            return this;
        }
        if (closeStreamOnPostRun) {
            closeStream();
        }
        commit();
        postRunDone = true;
        return this;
    }

    public void commit() {
        if (inner.checkpointer() instanceof com.openjiuwen.core.session.checkpointer.Checkpointer checkpointer) {
            checkpointer.postAgentExecute(inner);
        }
    }

    public WorkflowSessionApi createWorkflowSession() {
        return new WorkflowSessionApi(inner, getSessionId(), getEnvs());
    }

    public Object interact(Object value) {
        if (interaction == null) {
            interaction = new SimpleAgentInteraction(inner);
        }
        return interaction.waitUserInputs(value);
    }

    public com.openjiuwen.core.session.internal.AgentSession getInner() {
        return inner;
    }

    private Object tagStreamPayload(Object data) {
        if (sourceMetadata.isEmpty()) {
            return data;
        }
        if (data instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> tagged = mapToStringKeyMap(map);
            tagged.putAll(sourceMetadata);
            return tagged;
        }
        if (data instanceof OutputSchema outputSchema) {
            Object payload = outputSchema.getPayload();
            LinkedHashMap<String, Object> taggedPayload;
            if (payload instanceof Map<?, ?> map) {
                taggedPayload = mapToStringKeyMap(map);
            } else {
                taggedPayload = new LinkedHashMap<>();
                taggedPayload.put("value", payload);
            }
            taggedPayload.putAll(sourceMetadata);
            return new OutputSchema(outputSchema.getType(), outputSchema.getIndex(), taggedPayload);
        }
        return data;
    }

    private static OutputSchema normalizeOutput(Object data) {
        if (data instanceof OutputSchema outputSchema) {
            return outputSchema;
        }
        if (data instanceof Map<?, ?> map) {
            if (map.keySet().containsAll(java.util.Set.of("type", "index", "payload"))) {
                return new OutputSchema(
                        dataToString(map.get("type")),
                        dataToInt(map.get("index")),
                        map.get("payload")
                );
            }
            return new OutputSchema("message", 0, mapToStringKeyMap(map));
        }
        return new OutputSchema("message", 0, data);
    }

    private void triggerWriteStream(Object streamData) {
        LinkedHashMap<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("data", streamData);
        CallbackUtils.trigger(sessionId + "write_stream", kwargs);
    }

    private static LinkedHashMap<String, Object> mapToStringKeyMap(Map<?, ?> map) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static String dataToString(Object data) {
        return data == null ? null : String.valueOf(data);
    }

    private static int dataToInt(Object data) {
        if (data instanceof Number number) {
            return number.intValue();
        }
        if (data != null) {
            return Integer.parseInt(String.valueOf(data));
        }
        return 0;
    }

    private Object readCardProperty(String getterName, Object defaultValue) {
        if (card == null) {
            return defaultValue;
        }
        try {
            Object value = card.getClass().getMethod(getterName).invoke(card);
            return value == null ? defaultValue : value;
        } catch (ReflectiveOperationException ignored) {
            return defaultValue;
        }
    }
}
