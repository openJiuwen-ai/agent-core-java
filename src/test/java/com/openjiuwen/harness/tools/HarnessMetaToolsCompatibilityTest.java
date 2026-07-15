
package com.openjiuwen.harness.tools;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class HarnessMetaToolsCompatibilityTest {
    @Test
    void askUserToolShouldReturnStructuredPayload() {
        AskUserTool tool = new AskUserTool();
        ToolOutput output = tool.invoke(Map.of("questions", List.of(Map.of("header", "File"))));

        assertThat(output.isSuccess()).isTrue();
        assertThat(output.getData()).isEqualTo(Map.of("questions", List.of(Map.of("header", "File"))));
    }

    @Test
    void searchAndLoadToolsShouldDelegateToHandlers() {
        SearchToolsTool search = new SearchToolsTool((query, limit, detailLevel) -> List
                .of(Map.of("name", "read_file", "description", "Read file", "limit", limit, "detail", detailLevel)));
        LoadToolsTool load =
            new LoadToolsTool((toolNames, replace) -> Map.of("tool_names", toolNames, "replace", replace));

        ToolOutput searched = search.invoke("file", 5, 2);
        ToolOutput loaded = load.invoke(List.of("read_file"), true);

        assertThat(searched.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> searchedPayload = (Map<String, Object>) searched.getData();
        assertThat(searchedPayload).containsEntry("query", "file");
        assertThat(searchedPayload).containsEntry("count", 1);
        assertThat(loaded.isSuccess()).isTrue();
        assertThat(loaded.getData()).isEqualTo(Map.of("tool_names", List.of("read_file"), "replace", true));
    }

    @Test
    void cronToolShouldDispatchActionsToBackend() {
        CronToolBackend backend = new CronToolBackend() {
            @Override
            public List<Map<String, Object>> listJobs(boolean includeDisabled) {
                return List.of(Map.of("id", "job-1"));
            }

            @Override
            public Map<String, Object> createJob(Map<String, Object> params, CronToolContext context) {
                return Map.of("created", params.get("name"), "scope", context.toolScope());
            }

            @Override
            public Map<String, Object> updateJob(String jobId, Map<String, Object> patch, CronToolContext context) {
                return Map.of("jobId", jobId, "patch", patch);
            }

            @Override
            public boolean deleteJob(String jobId) {
                return true;
            }

            @Override
            public String runNow(String jobId) {
                return "run-1";
            }

            @Override
            public Map<String, Object> status() {
                return Map.of("healthy", true);
            }

            @Override
            public List<Map<String, Object>> getRuns(String jobId, int limit) {
                return List.of(Map.of("jobId", jobId, "runId", "run-1"));
            }

            @Override
            public Map<String, Object> wake(String text, CronToolContext context, String mode) {
                return Map.of("text", text, "mode", mode, "scope", context.toolScope());
            }
        };

        CronTool tool = new CronTool(backend, CronToolContext.builder().channelId("cron").sessionId("s1").build());

        assertThat(tool.invoke("status", Map.of()).isSuccess()).isTrue();
        assertThat(tool.invoke("list", Map.of()).isSuccess()).isTrue();
        assertThat(tool.invoke("add", Map.of("name", "job-a")).getData())
                .isEqualTo(Map.of("created", "job-a", "scope", "cron:s1"));
        assertThat(tool.invoke("wake", Map.of("text", "hello", "mode", "plan")).getData())
                .isEqualTo(Map.of("text", "hello", "mode", "plan", "scope", "cron:s1"));
    }
}
