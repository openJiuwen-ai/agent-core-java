package com.openjiuwen.deepagents;

import com.openjiuwen.deepagents.middlewares.ContextEngineeringMiddleware;
import com.openjiuwen.deepagents.middlewares.TaskPlanningMiddleware;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeepAgentsMiddlewareCompatibilityTest {

    @Test
    void contextEngineeringMiddlewareShouldNormalizeConfigSections() {
        ContextEngineeringMiddleware middleware = new ContextEngineeringMiddleware();
        DeepAgentConfig config = DeepAgentConfig.builder()
                .systemPrompt("You are a coding agent")
                .workspacePath("/tmp/repo")
                .language("en")
                .skillDirectories(List.of("/tmp/repo/skills"))
                .skillMode("auto_list")
                .extraPromptSections(List.of(
                        Map.of("name", "repo", "priority", 40, "content", Map.of("en", "Use rg")),
                        Map.of("name", "identity", "priority", 10, "content", Map.of("en", "Be precise"))
                ))
                .build();

        Map<String, Object> result = middleware.process(config);

        assertThat(result).containsEntry("workspace_path", "/tmp/repo");
        assertThat(result).containsEntry("language", "en");
        assertThat(result).containsEntry("skill_mode", "auto_list");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sections = (List<Map<String, Object>>) result.get("sections");
        assertThat(sections).hasSize(2);
        assertThat(sections.get(0)).containsEntry("name", "identity");
        assertThat(sections.get(1)).containsEntry("name", "repo");
    }

    @Test
    void contextEngineeringMiddlewareShouldAcceptMapInput() {
        ContextEngineeringMiddleware middleware = new ContextEngineeringMiddleware();

        Map<String, Object> result = middleware.process(Map.of(
                "system_prompt", "hello",
                "workspace_path", "/repo",
                "language", "cn",
                "extra_prompt_sections", List.of(Map.of("name", "repo", "content", "notes"))
        ));

        assertThat(result).containsEntry("system_prompt", "hello");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sections = (List<Map<String, Object>>) result.get("sections");
        assertThat(sections).singleElement().satisfies(section -> assertThat(section).containsEntry("name", "repo"));
    }

    @Test
    void taskPlanningMiddlewareShouldBuildPlanFromStringAndMap() {
        TaskPlanningMiddleware middleware = new TaskPlanningMiddleware();

        Map<String, Object> fromString = middleware.plan("Implement harness config");
        Map<String, Object> fromMap = middleware.plan(Map.of(
                "query", "Migrate module",
                "steps", List.of("compare", "implement", "test"),
                "mode", "plan"
        ));

        assertThat(fromString).containsEntry("count", 1);
        assertThat(fromString).containsEntry("mode", "plan");
        assertThat(fromMap).containsEntry("count", 3);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) fromMap.get("items");
        assertThat(items.get(0)).containsEntry("status", "in_progress");
        assertThat(items.get(1)).containsEntry("status", "pending");
    }
}
