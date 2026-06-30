package com.openjiuwen.harness.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessUtilityToolsCompatibilityTest {

    @TempDir
    Path tempDir;

    @Test
    void filesystemToolShouldWriteReadListAndSearch() throws Exception {
        FilesystemTool tool = new FilesystemTool(tempDir.toString());

        ToolOutput written = tool.writeFile("notes/a.txt", "hello memory");
        ToolOutput read = tool.readFile("notes/a.txt");
        ToolOutput listed = tool.listFiles("notes");
        ToolOutput searched = tool.searchText("notes", "memory");

        assertThat(written.isSuccess()).isTrue();
        assertThat(read.getData()).isEqualTo("hello memory");
        assertThat(listed.getData()).isEqualTo(List.of("notes/a.txt"));
        assertThat(searched.getData()).isEqualTo(List.of("notes/a.txt"));
    }

    @Test
    void memoryToolsShouldWriteReadEditAndSearch() {
        MemoryTools tools = new MemoryTools(tempDir.resolve("memory").toString());

        ToolOutput written = tools.writeMemory("MEMORY.md", "line1\nline2 query\nline3", false);
        ToolOutput read = tools.readMemory("MEMORY.md", 1, 2);
        ToolOutput edited = tools.editMemory("MEMORY.md", "query", "updated");
        ToolOutput searched = tools.memorySearch("updated", 5);

        assertThat(written.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> readPayload = (Map<String, Object>) read.getData();
        assertThat(String.valueOf(readPayload.get("content"))).contains("line2 query");
        assertThat(edited.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = (List<Map<String, Object>>) searched.getData();
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0)).containsEntry("path", "MEMORY.md");
    }

    @Test
    void skillToolsShouldListAndReadSkillFiles() throws Exception {
        Path skillsRoot = tempDir.resolve("skills");
        Files.createDirectories(skillsRoot.resolve("demo"));
        Files.writeString(skillsRoot.resolve("demo/SKILL.md"), "# Demo Skill");

        ListSkillTool listSkillTool = new ListSkillTool(skillsRoot.toString());
        SkillTool skillTool = new SkillTool(skillsRoot.toString());

        ToolOutput listed = listSkillTool.listSkills();
        ToolOutput read = skillTool.readSkill("demo", "SKILL.md");

        assertThat(listed.isSuccess()).isTrue();
        assertThat(listed.getData()).isEqualTo(List.of("demo"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) read.getData();
        assertThat(String.valueOf(payload.get("skill_content"))).contains("Demo Skill");
    }
}
