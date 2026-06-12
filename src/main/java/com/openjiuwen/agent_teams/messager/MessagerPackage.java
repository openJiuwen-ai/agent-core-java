/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.messager;

import java.util.List;

/**
 * Package bridge for messager transport exports.
 *
 * <p>Mirrors Python's {@code openjiuwen/agent_teams/messager/__init__.py}.</p>
 */
public final class MessagerPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_teams/messager/__init__.py";
    public static final String DESCRIPTION = "Messager transport interfaces and implementations.";
    public static final Class<InProcessMessager> IN_PROCESS_MESSAGER = InProcessMessager.class;
    public static final Class<Messager> MESSAGER = Messager.class;
    public static final Class<MessagerHandler> MESSAGER_HANDLER = MessagerHandler.class;
    public static final Class<PyZmqMessager> PY_ZMQ_MESSAGER = PyZmqMessager.class;
    public static final Class<MessagerPeerConfig> MESSAGER_PEER_CONFIG = MessagerPeerConfig.class;
    public static final Class<MessagerTransportConfig> MESSAGER_TRANSPORT_CONFIG = MessagerTransportConfig.class;
    public static final Class<SubscriptionHandle> SUBSCRIPTION_HANDLE = SubscriptionHandle.class;
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "InProcessMessager",
            "Messager",
            "MessagerHandler",
            "PyZmqMessager",
            "MessagerPeerConfig",
            "MessagerTransportConfig",
            "SubscriptionHandle",
            "create_messager"
    );

    private MessagerPackage() {
    }

    public static Messager createMessager(MessagerTransportConfig config) {
        return Messagers.createMessager(config);
    }
}
