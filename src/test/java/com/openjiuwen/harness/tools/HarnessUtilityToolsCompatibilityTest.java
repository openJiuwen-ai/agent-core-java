package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.memory.lite.MemoryToolContext;
import com.openjiuwen.harness.tools.skills.ListSkillTool;
import com.openjiuwen.harness.tools.skills.SkillDescriptor;
import com.openjiuwen.harness.tools.skills.SkillTool;
import com.openjiuwen.harness.workspace.Workspace;
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
    void memoryToolsShouldWriteReadEditAndSearch() throws Exception {
        Workspace workspace = new Workspace(tempDir.resolve("memory"));
        MemoryToolContext ctx = new MemoryToolContext();
        ctx.setWorkspace(workspace);
        ctx.setNodeName("memory");
        List<Tool> tools = MemoryTools.createMemoryTools(ctx);

        assertThat(tools).isNotEmpty();

        Tool writeTool = tools.stream().filter(t -> "write_memory".equals(t.getCard().getName())).findFirst().orElseThrow();
        Object writeResult = writeTool.invoke(Map.of("path", "MEMORY.md", "content", "line1\nline2 query\nline3", "append", false), Map.of());
        assertThat(writeResult).isNotNull();
        Tool searchTool = tools.stream().filter(t -> "memory_search".equals(t.getCard().getName())).findFirst().orElseThrow();
        Object result = searchTool.invoke(Map.of("query", "query"), Map.of());

        assertThat(String.valueOf(result)).contains("line2 query");
    }

    @Test
    void skillToolsShouldListAndReadSkillFiles() throws Exception {
        Path skillsRoot = tempDir.resolve("skills");
        Files.createDirectories(skillsRoot.resolve("demo"));
        Files.writeString(skillsRoot.resolve("demo/SKILL.md"), "# Demo Skill");

        ListSkillTool listSkillTool = new ListSkillTool(() -> List.of(
                new SkillDescriptor("demo", "Demo Skill", skillsRoot.resolve("demo").toString(), null)));
        SkillTool skillTool = new SkillTool(() -> List.of(
                new SkillDescriptor("demo", "Demo Skill", skillsRoot.resolve("demo").toString(), null)));

        Object listedResult = listSkillTool.invoke(Map.of(), Map.of());
        Object readResult = skillTool.invoke(Map.of("skill_name", "demo", "relative_file_path", "SKILL.md"), Map.of());

        assertThat(listedResult).isNotNull();
        assertThat(readResult).isInstanceOf(ToolOutput.class);
        ToolOutput readOutput = (ToolOutput) readResult;
        assertThat(readOutput.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) readOutput.getData();
        assertThat(String.valueOf(payload.get("skill_content"))).contains("Demo Skill");
    }
}
