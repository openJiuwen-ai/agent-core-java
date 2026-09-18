/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

class HttpClientRegistryTest {

    @Test
    void commonHttpClientIsRegisteredForFactoryConstruction() {
        assertThat(HttpClient.__client_name__).isEqualTo("http");
        ClientRegistry registry = ClientRegistry.getClientRegistry();

        assertThat(registry.isRegistered("http", "common")).isTrue();

        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("reuse_session", Boolean.FALSE);
        kwargs.put("config", new SessionConfig());

        Object client = registry.getClient("http", "common", kwargs);

        assertThat(client).isInstanceOf(HttpClient.class);
        assertThat(((HttpClient) client).getConfig().get("reuse_session")).isEqualTo(Boolean.FALSE);
    }
}
