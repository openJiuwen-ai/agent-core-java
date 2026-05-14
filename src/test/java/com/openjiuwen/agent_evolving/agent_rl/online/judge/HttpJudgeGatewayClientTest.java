/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.judge;

import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.GatewayHttpResponse;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.UpstreamGatewayClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpJudgeGatewayClientTest {

    @Test
    void scorePostsRequestAndParsesResponse() {
        FakeClient client = new FakeClient(new GatewayHttpResponse(200,
                "{\"score\":0.6,\"overall_raw\":8,\"votes\":[8],\"details\":{\"overall\":8},\"model\":\"judge-model\",\"session_id\":\"s1\",\"turn_num\":1}"));
        HttpJudgeGatewayClient judgeClient = new HttpJudgeGatewayClient(client, "http://judge.local", "token");

        ScoreResponse response = judgeClient.score(new ScoreRequest("resp", "inst", "next", "s1", 1));

        assertEquals(0.6, response.score());
        assertEquals(1, client.calls.size());
        assertEquals("Bearer token", client.calls.getFirst().headers().get("Authorization"));
        assertEquals("http://judge.local/score", client.calls.getFirst().url());
    }

    @Test
    void scoreThrowsOnHttpError() {
        FakeClient client = new FakeClient(new GatewayHttpResponse(503, "busy"));
        HttpJudgeGatewayClient judgeClient = new HttpJudgeGatewayClient(client, "http://judge.local", "");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> judgeClient.score(new ScoreRequest("resp", "inst", "", "", 0)));

        assertEquals("Judge request failed: HTTP 503: busy", error.getMessage());
    }

    record Call(String method, String url, Map<String, String> headers) {
    }

    static final class FakeClient implements UpstreamGatewayClient {
        private final GatewayHttpResponse response;
        final List<Call> calls = new ArrayList<>();

        FakeClient(GatewayHttpResponse response) {
            this.response = response;
        }

        @Override
        public GatewayHttpResponse postChatCompletions(Map<String, Object> jsonBody, Map<String, String> headers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GatewayHttpResponse request(String method, String url, Map<String, Object> params, Map<String, String> headers, byte[] content) {
            calls.add(new Call(method, url, headers));
            return response;
        }
    }
}
