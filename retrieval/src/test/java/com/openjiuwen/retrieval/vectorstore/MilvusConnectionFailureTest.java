/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.retrieval.vectorstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openjiuwen.retrieval.vector_store.MilvusVectorStore;

import io.milvus.exception.MilvusException;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.database.response.ListDatabasesResp;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;

/**
 * Verifies sanitized Milvus connection failures retain their transport cause.
 *
 * @since 2026-10-28
 */
class MilvusConnectionFailureTest {
    @Test
    void databaseFailureClosesClientAndKeepsOriginalFailure() {
        IllegalArgumentException failure = new IllegalArgumentException("database rejected");
        try (MockedConstruction<MilvusClientV2> clients = mockConstruction(MilvusClientV2.class,
                (client, context) -> when(client.listDatabases()).thenThrow(failure))) {
            assertThatThrownBy(() -> MilvusVectorStore.createClient("custom", "http://127.0.0.1:19530", ""))
                    .isSameAs(failure);
            verify(clients.constructed().get(0)).close();
        }
    }

    @Test
    void cleanupFailureIsSuppressedWithoutReplacingDatabaseFailure() {
        IllegalArgumentException failure = new IllegalArgumentException("database rejected");
        IllegalStateException cleanupFailure = new IllegalStateException("cleanup failed");
        try (MockedConstruction<MilvusClientV2> clients = mockConstruction(MilvusClientV2.class,
                (client, context) -> {
                    when(client.listDatabases()).thenThrow(failure);
                    doThrow(cleanupFailure).when(client).close();
                })) {
            assertThatThrownBy(() -> MilvusVectorStore.createClient("custom", "http://127.0.0.1:19530", ""))
                    .isSameAs(failure);
            assertThat(failure.getSuppressed()).containsExactly(cleanupFailure);
            verify(clients.constructed().get(0)).close();
        }
    }

    @Test
    void successfulInitializationTransfersAnOpenClientToTheCaller() throws Exception {
        try (MockedConstruction<MilvusClientV2> clients = mockConstruction(MilvusClientV2.class,
                (client, context) -> when(client.listDatabases())
                        .thenReturn(ListDatabasesResp.builder().databaseNames(List.of("custom")).build()))) {
            MilvusClientV2 connected = MilvusVectorStore.createClient("custom", "http://127.0.0.1:19530", "");
            assertThat(connected).isSameAs(clients.constructed().get(0));
            verify(connected).useDatabase("custom");
            verify(connected, never()).close();
            connected.close();
        }
    }

    @Test
    void interruptedInitializationClosesTheClientBeforeRestoringTheSignal() {
        InterruptedException interruption = new InterruptedException("switch cancelled");
        try (MockedConstruction<MilvusClientV2> clients = mockConstruction(MilvusClientV2.class,
                (client, context) -> {
                    when(client.listDatabases())
                            .thenReturn(ListDatabasesResp.builder().databaseNames(List.of("custom")).build());
                    doThrow(interruption).when(client).useDatabase("custom");
                    doAnswer(invocation -> {
                        assertThat(Thread.currentThread().isInterrupted()).isFalse();
                        return null;
                    }).when(client).close();
                })) {
            assertThatThrownBy(() -> MilvusVectorStore.createClient("custom", "http://127.0.0.1:19530", ""))
                    .isInstanceOf(IllegalStateException.class).hasCause(interruption);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(clients.constructed().get(0)).close();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void unreachableServerReportsSanitizedFailureWithTransportCause() throws Exception {
        try (ServerSocket unavailable = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            String address = "127.0.0.1:" + unavailable.getLocalPort();
            assertThatThrownBy(() -> MilvusVectorStore.createClient("default", "http://" + address,
                    "fixture-secret", Map.of("timeout", 0.2)))
                    .isInstanceOf(MilvusException.class)
                    .hasMessage("Fail connecting to server on " + address)
                    .hasMessageNotContaining("fixture-secret")
                    .cause().hasMessageContaining("DEADLINE_EXCEEDED");
        }
    }
}
