/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.app;

import com.openjiuwen.agent_evolving.agent_rl.online.gateway.GatewayConfig;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.GatewayHttpResponse;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.UpstreamGatewayClient;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GatewayBootstrapTest {

    @Test
    void buildAppFromConfigRequiresRedisWhenNoInjectedClientExists() throws Exception {
        GatewayConfig config = new GatewayConfig();
        config.setRecordDir(Files.createTempDirectory("gateway-bootstrap-no-redis").toString());
        config.setJudgeUrl("");

        assertThrows(IllegalArgumentException.class, () -> GatewayBootstrap.buildAppFromConfig(config, new FakeClient(), null));
    }

    @Test
    void buildAppFromConfigAcceptsInjectedRedisAndJudgeScorerSeam() throws Exception {
        GatewayConfig config = new GatewayConfig();
        config.setRecordDir(Files.createTempDirectory("gateway-bootstrap").toString());
        config.setJudgeUrl("http://judge.local");
        config.setJudgeModel("judge-model");
        config.setModelId("worker-model");

        try (GatewayServer server = GatewayBootstrap.buildAppFromConfig(config, new FakeClient(), new FakeRedis())) {
            assertEquals(Map.of("status", "ok"), server.health());
        }
    }

    private static final class FakeClient implements UpstreamGatewayClient {
        @Override
        public GatewayHttpResponse postChatCompletions(Map<String, Object> jsonBody, Map<String, String> headers) {
            throw new UnsupportedOperationException("not used in bootstrap test");
        }

        @Override
        public GatewayHttpResponse request(String method,
                                           String url,
                                           Map<String, Object> params,
                                           Map<String, String> headers,
                                           byte[] content) {
            throw new UnsupportedOperationException("not used in bootstrap test");
        }
    }

    public static final class FakeRedis {
        public Script registerFetchAndMarkScript(String luaSource) {
            return new Script();
        }
    }

    public static final class Script {
        public Object execute(Object keys, Object args) {
            return java.util.List.of();
        }
    }
}
