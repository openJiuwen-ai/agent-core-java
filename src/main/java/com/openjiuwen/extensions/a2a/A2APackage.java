/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import com.openjiuwen.core.runner.drunner.remote_client.RemoteClientConfig;
import com.openjiuwen.core.runner.drunner.remote_client.RemoteClientFactory;
import com.openjiuwen.core.runner.drunner.server_adapter.ServerAdapterRegistry;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import com.openjiuwen.extensions.a2a.A2AAgentExecutor.A2AInvokeHandler;
import com.openjiuwen.extensions.a2a.A2AAgentExecutor.A2AStreamHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package bootstrap for the A2A extension.
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.a2a} module in
 * {@code openjiuwen/extensions/a2a/__init__.py}.</p>
 */
public final class A2APackage {
    public static final String PYTHON_MODULE = "openjiuwen/extensions/a2a/__init__.py";
    public static final String PROTOCOL = "A2A";
    public static final List<String> REGISTERED_FACTORIES = List.of(
            "create_a2a_remote_client",
            "create_a2a_server_adapter"
    );

    static {
        registerAll();
    }

    private A2APackage() {
    }

    public static void registerAll() {
        RemoteClientFactory.registerRemoteClient(PROTOCOL, A2APackage::createA2aRemoteClient);
        ServerAdapterRegistry.registerServerAdapter(PROTOCOL, A2APackage::createA2aServerAdapter);
    }

    public static A2ARemoteClient createA2aRemoteClient(RemoteClientConfig config) {
        return new A2ARemoteClient(config);
    }

    public static A2ARemoteClient create_a2a_remote_client(RemoteClientConfig config) {
        return createA2aRemoteClient(config);
    }

    public static A2ARemoteClient createA2aRemoteClient(Map<String, Object> kwargs) {
        Map<String, Object> safeKwargs = kwargs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(kwargs);
        Object config = safeKwargs.get("config");
        if (config instanceof RemoteClientConfig remoteClientConfig) {
            return createA2aRemoteClient(remoteClientConfig);
        }
        return createA2aRemoteClient(RemoteClientConfig.builder()
                .id(stringValue(safeKwargs.get("id")))
                .version(stringValue(safeKwargs.get("version")))
                .name(stringValue(safeKwargs.get("name")))
                .description(stringValue(safeKwargs.get("description")))
                .type(stringValue(safeKwargs.get("type")))
                .topic(stringValue(safeKwargs.get("topic")))
                .url(stringValue(safeKwargs.get("url")))
                .kwargs(nestedKwargs(safeKwargs))
                .build());
    }

    public static A2AServerAdapter createA2aServerAdapter(Map<String, Object> kwargs) {
        Map<String, Object> safeKwargs = kwargs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(kwargs);
        Object card = safeKwargs.get("agent_card");
        AgentCard agentCard = card instanceof AgentCard typedCard ? typedCard : null;
        return new A2AServerAdapter(
                stringValue(safeKwargs.get("adapter_id")),
                stringOrDefault(safeKwargs.get("version"), ""),
                agentCard,
                handler(safeKwargs.get("invoke_handler"), A2AInvokeHandler.class),
                handler(safeKwargs.get("stream_handler"), A2AStreamHandler.class),
                stringValue(safeKwargs.get("interface_url")),
                stringOrDefault(safeKwargs.get("rpc_url"), A2AServerAdapter.DEFAULT_RPC_URL),
                stringOrDefault(safeKwargs.get("rest_url"), A2AServerAdapter.DEFAULT_REST_URL));
    }

    public static A2AServerAdapter create_a2a_server_adapter(Map<String, Object> kwargs) {
        return createA2aServerAdapter(kwargs);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedKwargs(Map<String, Object> values) {
        Object nested = values.get("kwargs");
        if (nested instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        Map<String, Object> result = new LinkedHashMap<>(values);
        result.remove("config");
        result.remove("id");
        result.remove("version");
        result.remove("name");
        result.remove("description");
        result.remove("type");
        result.remove("topic");
        result.remove("url");
        return result;
    }

    private static <T> T handler(Object value, Class<T> type) {
        return type.isInstance(value) ? type.cast(value) : null;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String stringOrDefault(Object value, String defaultValue) {
        String string = stringValue(value);
        return string == null ? defaultValue : string;
    }
}
