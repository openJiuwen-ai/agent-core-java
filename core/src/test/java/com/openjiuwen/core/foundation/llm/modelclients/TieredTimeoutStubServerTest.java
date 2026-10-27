/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.modelclients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

class TieredTimeoutStubServerTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void closeReleasesResponseWithoutWaitingForScriptDelay(boolean isSilentAfterEvents) throws IOException {
        String event = TieredTimeoutStubServer.sseContent("ready");
        TieredTimeoutStubServer.StubScript script = new TieredTimeoutStubServer.StubScript();
        if (isSilentAfterEvents) {
            script.events(List.of(event)).silentAfterEvents();
        } else {
            script.events(List.of(event, TieredTimeoutStubServer.sseDone())).gapMillis(30_000L);
        }
        try (TieredTimeoutStubServer stub = TieredTimeoutStubServer.start(script)) {
            URLConnection connection = URI.create(stub.baseUrl() + "/chat/completions")
                    .toURL().openConnection(Proxy.NO_PROXY);
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(5_000);
            try (InputStream body = connection.getInputStream()) {
                // Receiving a complete event proves the response is active before we close it.
                assertThat(new String(body.readNBytes(event.getBytes(StandardCharsets.UTF_8).length),
                        StandardCharsets.UTF_8)).isEqualTo(event);

                assertTimeout(Duration.ofSeconds(5), stub::close);
            }
        }
    }
}
