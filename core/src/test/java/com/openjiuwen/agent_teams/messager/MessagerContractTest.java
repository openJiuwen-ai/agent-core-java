/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.messager;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.schema.events.EventMessage;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class MessagerContractTest {

    @Test
    void handlerRemainsFunctionalAndAsync() {
        MessagerHandler handler = message -> CompletableFuture.completedFuture(null);

        CompletionStage<Void> result = handler.handle(new EventMessage("team_created", Map.of(), "sender"));

        assertThat(handler.getClass().getInterfaces()).contains(MessagerHandler.class);
        assertThat(result.toCompletableFuture()).isCompleted();
    }

    @Test
    void messagerMethodsExposeAsyncPythonSurface() {
        assertThat(Messager.class.getDeclaredMethods())
                .extracting(Method::getName)
                .containsExactlyInAnyOrder(
                        "start",
                        "stop",
                        "publish",
                        "subscribe",
                        "unsubscribe",
                        "send",
                        "registerDirectMessageHandler",
                        "unregisterDirectMessageHandler"
                );

        assertAsyncReturn("start");
        assertAsyncReturn("stop");
        assertAsyncReturn("unsubscribe", String.class);
        assertAsyncReturn("unregisterDirectMessageHandler");
        assertAsyncReturn("publish", String.class, EventMessage.class);
        assertAsyncReturn("subscribe", String.class, MessagerHandler.class);
        assertAsyncReturn("send", String.class, EventMessage.class);
        assertAsyncReturn("registerDirectMessageHandler", MessagerHandler.class);
    }

    private static void assertAsyncReturn(String methodName, Class<?>... parameterTypes) {
        Method method = Arrays.stream(Messager.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName)
                        && Arrays.equals(candidate.getParameterTypes(), parameterTypes))
                .findFirst()
                .orElseThrow();
        assertThat(CompletionStage.class.isAssignableFrom(method.getReturnType())).isTrue();
    }
}
