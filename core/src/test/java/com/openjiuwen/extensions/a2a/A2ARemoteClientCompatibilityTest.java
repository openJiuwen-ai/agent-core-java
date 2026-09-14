package com.openjiuwen.extensions.a2a;

import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.runner.drunner.remote_client.ProtocolEnum;
import com.openjiuwen.core.runner.drunner.remote_client.RemoteAgent;
import com.openjiuwen.core.runner.drunner.remote_client.RemoteClientConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.singleagent.schema.AgentResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("system-test")
class A2ARemoteClientCompatibilityTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void invokeShouldReturnAgentResultFromJsonRpcTaskResponse() throws Exception {
        List<String> requestBodies = new ArrayList<>();
        server = startServer("/a2a/jsonrpc", exchange -> {
            requestBodies.add(readBody(exchange));
            writeJson(exchange, """
                    {"jsonrpc":"2.0","id":"1","result":{"task":{"id":"task-1","contextId":"conv-1","status":{"state":"TASK_STATE_COMPLETED"},"artifacts":[{"artifactId":"artifact-1","parts":[{"text":"invoke ok"}]}]}}}
                    """);
        });

        A2ARemoteClient client = new A2ARemoteClient(RemoteClientConfig.builder()
                .id("remote-a2a-agent")
                .protocol(ProtocolEnum.A2A)
                .url("http://127.0.0.1:" + server.getAddress().getPort())
                .kwargs(Map.of("card", new AgentCard("remote-a2a-agent", "A2A test agent", "1.0")))
                .build());

        AgentResult result = (AgentResult) client.invoke(Map.of("query", "hello", "conversation_id", "conv-1"), 5.0);

        assertThat(requestBodies).hasSize(1);
        assertThat(requestBodies.get(0)).contains("SendMessage");
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.getArtifacts().get(0).getParts().get(0).getText()).isEqualTo("invoke ok");
    }

    @Test
    void streamShouldReturnStatusAndArtifactEventsFromSseResponse() throws Exception {
        server = startServer("/a2a/jsonrpc", exchange -> {
            readBody(exchange);
            String body = """
                    data: {"jsonrpc":"2.0","id":"1","result":{"statusUpdate":{"taskId":"task-stream-1","contextId":"conv-stream-1","status":{"state":"TASK_STATE_WORKING"}}}}

                    data: {"jsonrpc":"2.0","id":"1","result":{"artifactUpdate":{"taskId":"task-stream-1","contextId":"conv-stream-1","artifact":{"artifactId":"artifact-1","parts":[{"text":"chunk-1"}]}}}}

                    data: {"jsonrpc":"2.0","id":"1","result":{"task":{"id":"task-stream-1","contextId":"conv-stream-1","status":{"state":"TASK_STATE_COMPLETED"},"artifacts":[{"artifactId":"artifact-1","parts":[{"text":"done"}]}]}}}

                    """;
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        A2ARemoteClient client = new A2ARemoteClient(RemoteClientConfig.builder()
                .id("remote-a2a-agent")
                .protocol(ProtocolEnum.A2A)
                .url("http://127.0.0.1:" + server.getAddress().getPort() + "/a2a/jsonrpc")
                .kwargs(Map.of("card", new AgentCard("remote-a2a-agent", "A2A test agent", "1.0")))
                .build());

        Iterator<Object> iterator = client.stream(Map.of("query", "stream please"), 5.0);
        List<AgentResult> results = new ArrayList<>();
        while (iterator.hasNext()) {
            results.add((AgentResult) iterator.next());
        }

        assertThat(results).hasSize(3);
        assertThat(results.get(0).getStatus()).isEqualTo(TaskStatus.WORKING);
        assertThat(results.get(1).getArtifacts().get(0).getParts().get(0).getText()).isEqualTo("chunk-1");
        assertThat(results.get(2).getStatus()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    void remoteAgentShouldInstantiateA2AProtocolClient() throws Exception {
        server = startServer("/a2a/jsonrpc", exchange -> writeJson(exchange, """
                {"jsonrpc":"2.0","id":"1","result":{"task":{"id":"task-agent-1","contextId":"conv-agent-1","status":{"state":"TASK_STATE_COMPLETED"}}}}
                """));

        RemoteAgent agent = new RemoteAgent(
                "remote-a2a-agent",
                "",
                null,
                null,
                ProtocolEnum.A2A,
                Map.of("url", "http://127.0.0.1:" + server.getAddress().getPort(),
                       "card", new AgentCard("remote-a2a-agent", "A2A test agent", "1.0"))
        );

        AgentResult result = (AgentResult) agent.invoke(Map.of("query", "hello a2a", "conversation_id", "conv-agent-1"), 5.0);
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.getTaskId()).isEqualTo("task-agent-1");
    }

    private HttpServer startServer(String path, ExchangeHandler handler) throws IOException {
        HttpServer created = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        created.createContext(path, exchange -> handler.handle(exchange));
        created.start();
        return created;
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream input = exchange.getRequestBody()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void writeJson(HttpExchange exchange, String body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
