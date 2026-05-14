/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.remote_client;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.ServiceLoader;

/**
 * Factory for creating remote client instances using a plugin/entry-point mechanism.
 * <p>
 * Mirrors Python's {@code RemoteClientFactory} in
 * {@code runner/drunner/remote_client/remote_client_factory.py}.
 * <p>
 * Uses Java {@link ServiceLoader} as the equivalent of Python's {@code entry_points}
 * mechanism for discovering remote client implementations.
 */
public class RemoteClientFactory {

    /** Logical name for the A2A protocol client. */
    public static final String REMOTE_CLIENT_A2A_NAME = "A2A";

    /**
     * Create an A2A remote client.
     * <p>
     * Attempts to load the A2A client via ServiceLoader first, then falls back
     * to direct class loading of the built-in implementation.
     *
     * @param config the remote client configuration
     * @param card   the agent card for the remote agent
     * @return the instantiated remote client
     * @throws BaseError if the A2A client plugin cannot be loaded or instantiated
     */
    public static RemoteClient createA2a(RemoteClientConfig config, AgentCard card) {
        Class<? extends RemoteClient> clientClass;
        try {
            clientClass = loadEntryPoint(REMOTE_CLIENT_A2A_NAME);
        } catch (Exception exc) {
            throw ErrorHelper.buildError(StatusCode.REMOTE_AGENT_EXECUTION_ERROR,
                    "agent_id", config.getId(),
                    "reason", "failed to load A2A remote client plugin");
        }

        try {
            return clientClass
                    .getConstructor(RemoteClientConfig.class, AgentCard.class)
                    .newInstance(config, card);
        } catch (Exception exc) {
            throw ErrorHelper.buildError(StatusCode.REMOTE_AGENT_EXECUTION_ERROR,
                    "agent_id", config.getId(),
                    "reason", "failed to instantiate A2A remote client plugin");
        }
    }

    /**
     * Load a remote client class by name via ServiceLoader.
     * <p>
     * Falls back to reflection-based loading of the built-in A2A implementation
     * if the ServiceLoader finds no match.
     *
     * @param name the logical name of the remote client plugin
     * @return the loaded class
     * @throws BaseError if no matching plugin is found
     */
    @SuppressWarnings("unchecked")
    private static Class<? extends RemoteClient> loadEntryPoint(String name) {
        ServiceLoader<RemoteClient> loader = ServiceLoader.load(RemoteClient.class);
        for (RemoteClient client : loader) {
            // Check if the loaded implementation matches the requested name
            if (name.equals(client.getClass().getSimpleName())) {
                return (Class<? extends RemoteClient>) client.getClass();
            }
        }

        // Fallback: try to load the built-in A2A implementation via reflection
        if (REMOTE_CLIENT_A2A_NAME.equals(name)) {
            try {
                Class<?> clazz = Class.forName(
                        "com.openjiuwen.extensions.a2a.A2ARemoteClient");
                return (Class<? extends RemoteClient>) clazz;
            } catch (ClassNotFoundException e) {
                // fall through to error
            }
        }

        throw ErrorHelper.buildError(StatusCode.REMOTE_AGENT_EXECUTION_ERROR,
                "reason", "remote client plugin '" + name + "' not found");
    }
}
