/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.retrieval.vector_store;

import io.milvus.exception.MilvusException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MilvusConnectionFailureTest {
    @Test
    void unreachableServerReportsMilvusFailureWithTransportCauseAndNoCredentials() throws Exception {
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
