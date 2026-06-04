package com.openjiuwen.harness.tools;

import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.skills.Skill;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.harness.schema.task.TodoStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's ask-user / todo / skill tool behavior slices for P1-01.
 */
class HarnessModernToolsTest {

    @Test
    void askUserToolReturnsStructuredPayload() {
        AskUserTool tool = new AskUserTool();
        ToolOutput output = (ToolOutput) tool.invoke(Map.of(
                "question", "Choose one",
                "multiple", false,
                "options", List.of(Map.of("label", "A"), Map.of("label", "B"))
        ), Map.of());

        assertTrue(output.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) output.getData();
        assertTrue(data.isEmpty());
    }

    @Test
    void skillToolReadsSkillFile(@TempDir Path tempDir) throws Exception {
        Path skillDir = tempDir.resolve("test_skill_1");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "# test_skill_1\nskill body 1");
        Skill skill = Skill.builder().name("test_skill_1").description("skill description 1").directory(skillDir.toString()).build();
        SkillTool tool = new SkillTool((SysOperation) null, () -> List.of(skill));

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("skill_name", "test_skill_1", "relative_file_path", ""), Map.of());

        assertTrue(output.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) output.getData();
        assertTrue(String.valueOf(data.get("skill_directory")).contains("test_skill_1"));
        assertTrue(String.valueOf(data.get("skill_content")).contains("skill body 1"));
    }

    @Test
    void skillToolReturnsErrorForUnknownSkill(@TempDir Path tempDir) {
        Path skillDir = tempDir.resolve("test_skill_1");
        Skill skill = Skill.builder().name("test_skill_1").description("skill description 1").directory(skillDir.toString()).build();
        SkillTool tool = new SkillTool((SysOperation) null, () -> List.of(skill));

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("skill_name", "test_skill_2", "relative_file_path", ""), Map.of());

        assertFalse(output.isSuccess());
        assertTrue(String.valueOf(output.getError()).contains("Skill not found: test_skill_2"));
    }

    @Test
    void skillToolReadsReferenceFile(@TempDir Path tempDir) throws Exception {
        Path skillDir = tempDir.resolve("test_skill_1");
        Path referenceDir = skillDir.resolve("reference");
        Files.createDirectories(referenceDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "# test_skill_1\nskill body 1");
        Files.writeString(referenceDir.resolve("temp_file.md"), "test_skill_1 temp file content");
        Skill skill = Skill.builder().name("test_skill_1").description("skill description 1").directory(skillDir.toString()).build();
        SkillTool tool = new SkillTool((SysOperation) null, () -> List.of(skill));

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("skill_name", "test_skill_1", "relative_file_path", "reference/temp_file.md"), Map.of());

        assertTrue(output.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) output.getData();
        assertTrue(String.valueOf(data.get("skill_content")).contains("test_skill_1 temp file content"));
    }

    @Test
    void skillToolReturnsErrorForUnknownReferenceFile(@TempDir Path tempDir) throws Exception {
        Path skillDir = tempDir.resolve("test_skill_1");
        Files.createDirectories(skillDir.resolve("reference"));
        Files.writeString(skillDir.resolve("SKILL.md"), "# test_skill_1\nskill body 1");
        Skill skill = Skill.builder().name("test_skill_1").description("skill description 1").directory(skillDir.toString()).build();
        SkillTool tool = new SkillTool((SysOperation) null, () -> List.of(skill));

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("skill_name", "test_skill_1", "relative_file_path", "reference/unknown_file.md"), Map.of());

        assertFalse(output.isSuccess());
        assertNotNull(output.getError());
    }

    @Test
    void listSkillToolReturnsAllSkillsAndFallbackMessage() {
        Skill a = Skill.builder().name("alpha").description("first").directory("/skills/alpha").build();
        Skill b = Skill.builder().name("beta").description("second").directory("/skills/beta").build();
        ListSkillTool tool = new ListSkillTool(() -> List.of(a, b));

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("query", "search task"), Map.of());

        assertTrue(output.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) output.getData();
        assertEquals("all", data.get("mode"));
        assertTrue(String.valueOf(data.get("message")).contains("fallback"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> skills = (List<Map<String, Object>>) data.get("skills");
        assertEquals(2, skills.size());
        assertEquals("/skills/alpha/SKILL.md", skills.get(0).get("skill_md_path"));
        assertEquals("/skills/beta/SKILL.md", skills.get(1).get("skill_md_path"));
    }

    @Test
    void todoCreateListModifyAndGetUseSessionState() {
        InMemorySession session = new InMemorySession("session-1");
        TodoCreateTool createTool = new TodoCreateTool(null);
        TodoListTool listTool = new TodoListTool(null);
        TodoModifyTool modifyTool = new TodoModifyTool(null);
        TodoGetTool getTool = new TodoGetTool(null);

        @SuppressWarnings("unchecked")
        Map<String, Object> createResult = (Map<String, Object>) createTool.invoke(Map.of(
                "tasks", List.of(
                        Map.of("content", "Task 1", "activeForm", "Doing Task 1", "description", "Desc 1"),
                        Map.of("content", "Task 2", "activeForm", "Doing Task 2", "description", "Desc 2")
                )
        ), Map.of("session", session));
        assertTrue(String.valueOf(createResult.get("message")).contains("Successfully created 2 task(s)"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> activeTasks = (List<Map<String, Object>>) ((Map<String, Object>) listTool.invoke(Map.of(), Map.of("session", session))).get("tasks");
        assertEquals(2, activeTasks.size());
        assertEquals(TodoStatus.IN_PROGRESS.getValue(), activeTasks.get(0).get("status"));
        assertEquals(TodoStatus.PENDING.getValue(), activeTasks.get(1).get("status"));

        String firstId = String.valueOf(activeTasks.get(0).get("id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> modifyResult = (Map<String, Object>) modifyTool.invoke(Map.of(
                "action", "update",
                "todos", List.of(Map.of("id", firstId, "status", "completed"))
        ), Map.of("session", session));
        assertTrue(String.valueOf(modifyResult.get("message")).contains("Successfully updated 1 task(s)"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> afterModify = (List<Map<String, Object>>) ((Map<String, Object>) listTool.invoke(Map.of(), Map.of("session", session))).get("tasks");
        assertEquals(1, afterModify.size());
        assertEquals("Task 2", afterModify.get(0).get("content"));

        @SuppressWarnings("unchecked")
        Map<String, Object> fetchedTodo = (Map<String, Object>) ((Map<String, Object>) getTool.invoke(
                Map.of("id", firstId),
                Map.of("session", session)
        )).get("todo");
        assertNotNull(fetchedTodo.get("id"));
        assertFalse(String.valueOf(fetchedTodo.get("id")).isBlank());
        assertEquals(TodoStatus.COMPLETED.getValue(), fetchedTodo.get("status"));
    }

    private static final class InMemorySession implements Session {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();

        private InMemorySession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> patch) {
            state.putAll(patch);
        }
    }
}
