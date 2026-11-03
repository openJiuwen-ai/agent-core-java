package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.tools.tool_discovery.LoadToolsTool;
import com.openjiuwen.harness.tools.tool_discovery.SearchToolsTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessMetaToolsCompatibilityTest {

    @Test
    void askUserToolShouldReturnStructuredPayload() throws Exception {
        AskUserTool tool = new AskUserTool();
        Object output = tool.invoke(Map.of("questions", List.of(Map.of("header", "File"))));

        assertThat(output).isNotNull();
    }

    @Test
    void searchAndLoadToolsShouldDelegateToHandlers() throws Exception {
        SearchToolsTool ToolSearcher = new SearchToolsTool(
                (query, limit, detailLevel) -> List.of(
                        Map.of("name", "read_file", "description", "Read file", "limit", limit, "detail", detailLevel)
                ),
                (session, trace) -> {}
        );
        LoadToolsTool load = new LoadToolsTool((session, toolNames, replace) -> Map.of("tool_names", toolNames, "replace", replace));

        ToolOutput searched = (ToolOutput) ToolSearcher.invoke(Map.of("query", "file", "limit", 5, "detail_level", 2));
        ToolOutput loaded = (ToolOutput) load.invoke(Map.of("tool_names", List.of("read_file"), "replace", true));

        assertThat(searched.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> searchedPayload = (Map<String, Object>) searched.getData();
        assertThat(searchedPayload).containsEntry("query", "file");
        assertThat(searchedPayload).containsEntry("count", 1);
        assertThat(loaded.isSuccess()).isTrue();
        assertThat(loaded.getData()).isEqualTo(Map.of("tool_names", List.of("read_file"), "replace", true));
    }

    @Test
    void cronToolShouldDispatchActionsToBackend() throws Exception {
    CronTool.CronToolBackend backend = new CronTool.CronToolBackend() {
            @Override
            public List<Map<String, Object>> listJobs(boolean includeDisabled) {
                return List.of(Map.of("id", "job-1"));
            }

            @Override
            public Map<String, Object> getJob(String jobId) {
                return Map.of("id", jobId);
            }

            @Override
            public Map<String, Object> createJob(Map<String, Object> params, CronTool.CronToolContext context) {
                return Map.of("created", params.get("name"), "scope", context.toolScope());
            }

            @Override
            public Map<String, Object> updateJob(String jobId, Map<String, Object> patch, CronTool.CronToolContext context) {
                return Map.of("jobId", jobId, "patch", patch);
            }

            @Override
            public boolean deleteJob(String jobId) {
                return true;
            }

            @Override
            public Map<String, Object> toggleJob(String jobId, boolean enabled) {
                return Map.of("jobId", jobId, "enabled", enabled);
            }

            @Override
            public List<Map<String, Object>> previewJob(String jobId, int count) {
                return List.of();
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
            public Map<String, Object> wake(String text, CronTool.CronToolContext context, String mode) {
                return Map.of("text", text, "mode", mode, "scope", context.toolScope());
            }
        };

        CronTool.CronToolContext ctx = new CronTool.CronToolContext("cron", "s1", Map.of(), null);

        Object statusResult = CronTool.dispatchCronAction(backend, ctx, "status", Map.of());
        assertThat(statusResult).isEqualTo(Map.of("healthy", true));

        Object listResult = CronTool.dispatchCronAction(backend, ctx, "list", Map.of());
        assertThat(listResult).isNotNull();

        Object addResult = CronTool.dispatchCronAction(backend, ctx, "add", Map.of("name", "job-a"));
        assertThat(addResult).isEqualTo(Map.of("created", "job-a", "scope", "cron:s1"));

        Object wakeResult = CronTool.dispatchCronAction(backend, ctx, "wake", Map.of("text", "hello", "mode", "plan"));
        assertThat(wakeResult).isEqualTo(Map.of("text", "hello", "mode", "plan", "scope", "cron:s1"));
    }
}
