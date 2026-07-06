package examples.reactive_agent;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.stream.StreamMode;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 示例：应用侧如何用框架的响应式 API（Runner.runAgentAsync / runAgentStreamingAsync）
 * 在 Spring WebFlux 中暴露 HTTP 端点。
 *
 * <p>框架不提供 starter，本类完全在示例目录内，是参考实现，并非框架的一部分。
 *
 * <p>错误通过 event:error + event:done（HTTP 200）返回，防止 EventSource 自动重连重放副作用工具。
 */
@RestController
@RequestMapping("/api/agent")
public class WebFluxControllerExample {

    @PostMapping(value = "/invoke",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> invoke(@RequestBody Map<String, Object> request) {
        Object agentId = request.get("agent_id");
        Object inputs = request.get("inputs");
        if (isBlank(agentId)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "agent_id is required"));
        }
        return Runner.runAgentAsync(agentId, inputs, null, null, null)
                .map(result -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("output", result);
                    body.put("result_type", "answer");
                    return ResponseEntity.ok(body);
                });
    }

    @PostMapping(value = "/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> stream(@RequestBody Map<String, Object> request) {
        Object agentId = request.get("agent_id");
        Object inputs = request.get("inputs");
        // SSE 已 committed HTTP 200，缺字段错误走 event:error 信封而非 400
        Flux<Object> source = isBlank(agentId)
                ? Flux.error(new IllegalArgumentException("agent_id is required"))
                : Runner.runAgentStreamingAsync(
                        agentId, inputs, null, null, List.of(StreamMode.OUTPUT), null);
        return source
                .map(chunk -> ServerSentEvent.<Object>builder()
                        .event("chunk")
                        .data(chunk)
                        .build())
                .onErrorResume(err -> Flux.just(ServerSentEvent.<Object>builder()
                        .event("error")
                        .data(Map.of(
                                "message", err.getMessage() != null ? err.getMessage() : "unknown",
                                "type", err.getClass().getSimpleName()))
                        .build()))
                .concatWith(Mono.just(ServerSentEvent.<Object>builder()
                        .event("done")
                        .data("[DONE]")
                        .build()));
    }

    private static boolean isBlank(Object value) {
        return value == null || (value instanceof String s && s.isBlank());
    }
}
