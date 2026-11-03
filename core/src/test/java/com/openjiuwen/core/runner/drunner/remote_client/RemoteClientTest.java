/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.remote_client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class RemoteClientTest {

    @Test
    void isStoppedDefaultsToNegationOfIsStarted() {
        RemoteClient started = client(true);
        RemoteClient stopped = client(false);

        assertThat(started.isStopped()).isFalse();
        assertThat(stopped.isStopped()).isTrue();
    }

    @Test
    void asyncMethodsRemainComposable() {
        RemoteClient client = client(true);

        assertThat(client.start().toCompletableFuture().join()).isNull();
        assertThat(client.stop().toCompletableFuture().join()).isNull();
        assertThat(client.invoke(Map.of("x", 1), 1.5).toCompletableFuture().join())
                .containsEntry("ok", true);
        List<Object> chunks = new ArrayList<>();
        client.stream(Map.of(), null).forEachRemaining(chunks::add);
        assertThat(chunks).containsExactly("chunk");
    }

    private static RemoteClient client(boolean started) {
        return new RemoteClient() {
            @Override
            public CompletionStage<Void> start() {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<Void> stop() {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public boolean isStarted() {
                return started;
            }

            @Override
            public CompletionStage<Map<String, Object>> invoke(Map<String, Object> inputs, Double timeoutSeconds) {
                return CompletableFuture.completedFuture(Map.of("ok", true, "inputs", inputs));
            }

            @Override
            public Iterator<Object> stream(Map<String, Object> inputs, Double timeoutSeconds) {
                return List.<Object>of("chunk").iterator();
            }
        };
    }
}
