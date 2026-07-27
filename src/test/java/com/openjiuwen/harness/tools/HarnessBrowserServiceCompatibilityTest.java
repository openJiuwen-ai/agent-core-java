package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.harness.tools.browser.BrowserRunGuardrails;
import com.openjiuwen.harness.tools.browser.BrowserService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessBrowserServiceCompatibilityTest {

    @Test
    void failureSummaryShouldBeReusedThenCleared() {
        AtomicInteger counter = new AtomicInteger();
        java.util.List<String> observedTasks = new java.util.ArrayList<>();
        BrowserService service = new BrowserService("openai", "key", "base", "model",
                McpServerConfig.builder().serverId("s").serverName("s").serverPath("stdio://playwright").clientType("stdio").build(),
                BrowserRunGuardrails.builder().build()) {
            @Override
            protected Map<String, Object> runTaskOnce(String task, String sessionId, String requestId, Integer timeoutS) {
                observedTasks.add(task);
                int attempt = counter.incrementAndGet();
                Map<String, Object> payload = new LinkedHashMap<>();
                if (attempt == 1) {
                    payload.put("ok", false);
                    payload.put("final", "Opened dashboard and attempted submit.");
                    payload.put("page", Map.of("url", "https://example.com/form"));
                    payload.put("screenshot", null);
                    payload.put("error", "submit button not found");
                    return payload;
                }
                payload.put("ok", true);
                payload.put("final", "Submitted successfully.");
                payload.put("page", Map.of("url", "https://example.com/done"));
                payload.put("screenshot", null);
                payload.put("error", null);
                return payload;
            }
        };

        Map<String, Object> first = service.runTask("Submit onboarding form", "session-1", "req-1", null);
        Map<String, Object> second = service.runTask("Submit onboarding form", "session-1", "req-2", null);
        Map<String, Object> third = service.runTask("Submit onboarding form", "session-1", "req-3", null);

        assertThat(first.get("ok")).isEqualTo(false);
        assertThat(String.valueOf(first.get("failure_summary"))).contains("submit button not found");
        assertThat(observedTasks.get(0)).doesNotContain("Previous failed attempt context:");
        assertThat(second.get("ok")).isEqualTo(true);
        assertThat(second.get("failure_summary")).isNull();
        assertThat(observedTasks.get(1)).contains("Previous failed attempt context:");
        assertThat(observedTasks.get(2)).doesNotContain("Previous failed attempt context:");
        assertThat(third.get("ok")).isEqualTo(true);
    }

    @Test
    void retryAndResumeGuardrailsShouldWork() {
        AtomicInteger retryCounter = new AtomicInteger();
        BrowserService retryService = new BrowserService("openai", "key", "base", "model",
                McpServerConfig.builder().serverId("s").serverName("s").serverPath("stdio://playwright").clientType("stdio").build(),
                BrowserRunGuardrails.builder().retryOnce(true).build()) {
            @Override
            protected Map<String, Object> runTaskOnce(String task, String sessionId, String requestId, Integer timeoutS) {
                int attempt = retryCounter.incrementAndGet();
                Map<String, Object> payload = new LinkedHashMap<>();
                if (attempt == 1) {
                    payload.put("ok", false);
                    payload.put("final", "### Error\nError: page.goto: Frame has been detached.");
                    payload.put("page", Map.of("url", "https://www.lazada.sg"));
                    payload.put("screenshot", null);
                    payload.put("error", "tool execution failed");
                    return payload;
                }
                payload.put("ok", true);
                payload.put("final", "Navigation recovered and task completed.");
                payload.put("page", Map.of("url", "https://www.lazada.sg"));
                payload.put("screenshot", null);
                payload.put("error", null);
                return payload;
            }
        };
        Map<String, Object> retried = retryService.runTask("Open Lazada homepage", "session-retry", "req-retry", null);
        assertThat(retried.get("ok")).isEqualTo(true);
        assertThat(retried.get("attempt")).isEqualTo(2);

        AtomicInteger resumeCounter = new AtomicInteger();
        BrowserService resumeService = new BrowserService("openai", "key", "base", "model",
                McpServerConfig.builder().serverId("s").serverName("s").serverPath("stdio://playwright").clientType("stdio").build(),
                BrowserRunGuardrails.builder().resumeOnMaxIterations(true).build()) {
            @Override
            protected Map<String, Object> runTaskOnce(String task, String sessionId, String requestId, Integer timeoutS) {
                int attempt = resumeCounter.incrementAndGet();
                Map<String, Object> payload = new LinkedHashMap<>();
                if (attempt == 1) {
                    payload.put("ok", false);
                    payload.put("final", "Max iterations reached without completion");
                    payload.put("page", Map.of("url", "https://www.baidu.com"));
                    payload.put("screenshot", null);
                    payload.put("error", "max_iterations_reached");
                    return payload;
                }
                payload.put("ok", true);
                payload.put("final", "Completed after continuation.");
                payload.put("page", Map.of("url", "https://www.baidu.com"));
                payload.put("screenshot", null);
                payload.put("error", null);
                return payload;
            }
        };
        Map<String, Object> resumed = resumeService.runTask("Open Baidu homepage", "session-max-iter", "req-1", null);
        assertThat(resumed.get("ok")).isEqualTo(true);
        assertThat(resumed.get("attempt")).isEqualTo(2);
    }
}
