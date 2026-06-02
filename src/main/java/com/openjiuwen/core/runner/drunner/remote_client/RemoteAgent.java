/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.remote_client;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.runner.drunner.DistributedRunner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

/**
 * Remote-agent facade.
 *
 * <p>Mirrors Python's {@code RemoteAgent} in
 * {@code openjiuwen.core.runner.drunner.remote_client.remote_agent}.
 */
public class RemoteAgent {

    private final String agentId;
    private final String version;
    private final String description;
    private final String topic;
    private final ProtocolEnum protocol;
    private final RemoteClient client;

    public RemoteAgent(String agentId, String version, String description, String topic,
                       ProtocolEnum protocol, Map<String, Object> config) {
        this.agentId = agentId;
        this.version = version != null ? version : "";
        this.description = description;
        this.topic = topic != null ? topic : DistributedRunner.agentTopic(agentId, this.version);
        this.protocol = protocol != null ? protocol : ProtocolEnum.MQ;
        Map<String, Object> rawConfig = config != null ? new LinkedHashMap<>(config) : new LinkedHashMap<>();
        Map<String, Object> kwargs = rawConfig.get("kwargs") instanceof Map<?, ?> nestedKwargs
                ? stringifyKeys(nestedKwargs)
                : rawConfig;
        String url = rawConfig.get("url") != null ? String.valueOf(rawConfig.get("url")) : null;
        RemoteClientConfig clientConfig = RemoteClientConfig.builder()
                .id(agentId)
                .version(this.version)
                .description(description)
                .topic(this.topic)
                .protocol(this.protocol)
                .url(url)
                .kwargs(kwargs)
                .build();
        if (this.protocol == ProtocolEnum.A2A) {
            Object card = kwargs.get("card");
            this.client = RemoteClientFactory.createA2a(clientConfig, card instanceof AgentCard agentCard
                    ? agentCard
                    : AgentCard.builder().id(agentId).name(agentId).description(description).build());
        } else {
            this.client = new MqRemoteClient(clientConfig);
        }
    }

    public RemoteAgent(String agentId) {
        this(agentId, "", null, null, ProtocolEnum.MQ, null);
    }

    public Object invoke(Map<String, Object> inputs) throws Exception {
        return invoke(inputs, (Double) null);
    }

    public Object invoke(Map<String, Object> inputs, AgentSessionApi session) throws Exception {
        return invoke(inputs, (Double) null);
    }

    public Object invoke(Map<String, Object> inputs, AgentSessionApi session, ModelContext context) throws Exception {
        return invoke(inputs, (Double) null);
    }

    public Object invoke(Map<String, Object> inputs, Double timeoutSeconds) throws Exception {
        try {
            client.start();
            return client.invoke(inputs, timeoutSeconds);
        } catch (BaseError e) {
            throw e;
        } catch (TimeoutException e) {
            throw timeoutError(timeoutSeconds, e);
        } catch (CancellationException e) {
            throw cancelledError(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw cancelledError(e);
        }
    }

    public Iterator<Object> stream(Map<String, Object> inputs) throws Exception {
        return stream(inputs, (Double) null);
    }

    public Iterator<Object> stream(Map<String, Object> inputs, AgentSessionApi session) throws Exception {
        return stream(inputs, (Double) null);
    }

    public Iterator<Object> stream(Map<String, Object> inputs, AgentSessionApi session, ModelContext context)
            throws Exception {
        return stream(inputs, (Double) null);
    }

    public Iterator<Object> stream(Map<String, Object> inputs, AgentSessionApi session,
                                   List<StreamMode> streamModes) throws Exception {
        return stream(inputs, (Double) null);
    }

    public Iterator<Object> stream(Map<String, Object> inputs, Double timeoutSeconds) throws Exception {
        try {
            client.start();
            return wrapStream(client.stream(inputs, timeoutSeconds), timeoutSeconds);
        } catch (BaseError e) {
            throw e;
        } catch (TimeoutException e) {
            throw timeoutError(timeoutSeconds, e);
        } catch (CancellationException e) {
            throw cancelledError(e);
        }
    }

    private Iterator<Object> wrapStream(Iterator<Object> delegate, Double timeoutSeconds) {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                try {
                    return delegate.hasNext();
                } catch (RuntimeException e) {
                    throw translateRuntime(e, timeoutSeconds);
                }
            }

            @Override
            public Object next() {
                try {
                    return delegate.next();
                } catch (NoSuchElementException e) {
                    throw e;
                } catch (RuntimeException e) {
                    throw translateRuntime(e, timeoutSeconds);
                }
            }
        };
    }

    private RuntimeException translateRuntime(RuntimeException error, Double timeoutSeconds) {
        if (error instanceof BaseError baseError) {
            return baseError;
        }
        Throwable cause = error.getCause();
        if (cause instanceof BaseError baseError) {
            return baseError;
        }
        if (cause instanceof TimeoutException timeoutException) {
            return timeoutError(timeoutSeconds, timeoutException);
        }
        if (error instanceof CancellationException) {
            return cancelledError(error);
        }
        if (cause instanceof CancellationException cancellationException) {
            return cancelledError(cancellationException);
        }
        return error;
    }

    private BaseError timeoutError(Double timeoutSeconds, Throwable cause) {
        return ErrorHelper.buildError(
                StatusCode.REMOTE_AGENT_EXECUTION_TIMEOUT,
                null,
                null,
                cause,
                Map.of("agent_id", agentId, "timeout", String.valueOf(timeoutSeconds)));
    }

    private BaseError cancelledError(Throwable cause) {
        return ErrorHelper.buildError(
                StatusCode.REMOTE_AGENT_EXECUTION_ERROR,
                null,
                null,
                cause,
                Map.of("agent_id", agentId, "reason", "cancelled"));
    }

    private static Map<String, Object> stringifyKeys(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }
}
