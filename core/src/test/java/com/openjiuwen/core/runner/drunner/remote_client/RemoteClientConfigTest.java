/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.remote_client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RemoteClientConfigTest {

    @Test
    void builderDefaultsMatchPythonDataclassDefaults() {
        RemoteClientConfig config = RemoteClientConfig.builder().build();

        assertThat(config.getProtocol()).isEqualTo(ProtocolEnum.MQ);
        assertThat(config.getKwargs()).isEmpty();
    }

    @Test
    void builderPreservesExplicitValues() {
        RemoteClientConfig config = RemoteClientConfig.builder()
                .id("client-1")
                .protocol(ProtocolEnum.A2A)
                .url("https://example")
                .kwargs(Map.of("token", "abc"))
                .build();

        assertThat(config.getId()).isEqualTo("client-1");
        assertThat(config.getProtocol()).isEqualTo(ProtocolEnum.A2A);
        assertThat(config.getUrl()).isEqualTo("https://example");
        assertThat(config.getKwargs()).containsEntry("token", "abc");
    }
}
