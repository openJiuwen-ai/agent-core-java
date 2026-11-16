/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.messager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedConstruction;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Preserves positive millisecond startup budgets and saturation when decimal seconds are converted.
 *
 * @since 2026-11-09
 */
class TeamTransportTimeoutTest {
    @ParameterizedTest
    @CsvSource({
        "0.0001, 1",
        "0.0014, 1",
        "0.0015, 2",
        "0.1285, 129",
        "3.0, 3000",
        "1.7976931348623157E308, 9223372036854775807"
    })
    void transportStartupUsesRoundedBoundedMilliseconds(double timeoutSeconds, long expectedMillis) {
        CompletableFuture<Void> started = spy(CompletableFuture.<Void>completedFuture(null));
        MessagerTransportConfig config = MessagerTransportConfig.builder().backend("pyzmq")
                .pubsubPublishAddr("tcp://127.0.0.1:23456").pubsubSubscribeAddr("tcp://127.0.0.1:23457")
                .metadata(Map.of("pubsub_bind", false)).requestTimeout(timeoutSeconds).build();
        try (MockedConstruction<PyZmqMessager> construction = mockConstruction(PyZmqMessager.class, (mock, context) -> {
            when(mock.start()).thenReturn(started);
            when(mock.stop()).thenReturn(CompletableFuture.completedFuture(null));
            when(mock.getLocalPeer()).thenReturn(MessagerPeerConfig.builder().agentId("leader")
                    .addrs(List.of("tcp://127.0.0.1:23458")).build());
        }); TeamTransportRuntime runtime = TeamTransportRuntime.open("timeout-fixture", "leader", config)) {
            verify(started).orTimeout(expectedMillis, TimeUnit.MILLISECONDS);
            assertThat(runtime.messager()).isSameAs(construction.constructed().get(0));
        }
    }
}
