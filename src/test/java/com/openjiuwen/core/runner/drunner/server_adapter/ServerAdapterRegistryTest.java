/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.server_adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ServerAdapterRegistryTest {

    @Test
    void customRegistrationWinsForMatchingProtocol() {
        ServerAdapterRegistry.registerServerAdapter("CUSTOM_TEST", kwargs -> Map.of("seen", kwargs.get("x")));

        Object adapter = ServerAdapterRegistry.createServerAdapter("CUSTOM_TEST", Map.of("x", 7));

        assertThat(adapter).isEqualTo(Map.of("seen", 7));
    }

    @Test
    void unknownProtocolReturnsNullWithoutThrowing() {
        assertThat(ServerAdapterRegistry.createServerAdapter("UNKNOWN_PROTOCOL", Map.of())).isNull();
        assertThat(ServerAdapterRegistry.createServerAdapter("A2A", Map.of("url", "noop"))).isNull();
    }
}
