/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code tests.unit_tests.harness.tools.test_filesystem_tools} in
 * {@code tests/unit_tests/harness/tools/test_filesystem_tools.py}.
 */
class FilesystemToolsTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, List<LinkedHashMap<String, Object>>>> HISTORY_TYPE =
            new TypeReference<>() {
            };

    @TempDir
    private Path tempDir;

    @Test
    void fileReadWrite() throws Exception {
        FilesystemTools.WriteFileTool writeTool = new FilesystemTools.WriteFileTool(tempDir.toString());
        FilesystemTools.ReadFileTool readTool = new FilesystemTools.ReadFileTool(tempDir.toString());

        ToolOutput write = output(writeTool.invoke(Map.of("path", "test.txt", "content", "first\nsecond"), Map.of()));
        ToolOutput read = output(readTool.invoke(Map.of("path", "test.txt"), Map.of()));

        assertThat(write.isSuccess()).isTrue();
        assertThat(data(write).get("bytes")).isEqualTo(12);
        assertThat(read.isSuccess()).isTrue();
        assertThat(data(read).get("content")).isEqualTo("first\nsecond");
    }

    @Test
    void readFileRejectsEscapingWorkspace() {
        FilesystemTools.ReadFileTool readTool = new FilesystemTools.ReadFileTool(tempDir.toString());

        assertThatThrownBy(() -> readTool.invoke(Map.of("path", "../outside.txt"), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes workspace root");
    }

    @Test
    void readFileTextContent() throws Exception {
        Files.writeString(tempDir.resolve("max.txt"), "alpha\nbeta\ngamma", StandardCharsets.UTF_8);

        ToolOutput read = output(new FilesystemTools.ReadFileTool(tempDir.toString())
                .invoke(Map.of("path", "max.txt"), Map.of()));

        assertThat(data(read).get("content")).isEqualTo("alpha\nbeta\ngamma");
    }

    @Test
    void writeCreatesParentDirectories() throws Exception {
        ToolOutput write = output(new FilesystemTools.WriteFileTool(tempDir.toString())
                .invoke(Map.of("path", "sub/dir/new.txt", "content", "created"), Map.of()));

        assertThat(write.isSuccess()).isTrue();
        assertThat(Files.readString(tempDir.resolve("sub/dir/new.txt"))).isEqualTo("created");
    }

    @Test
    void writeOverwritesExistingFile() throws Exception {
        Files.writeString(tempDir.resolve("rewrite.txt"), "old", StandardCharsets.UTF_8);

        output(new FilesystemTools.WriteFileTool(tempDir.toString())
                .invoke(Map.of("path", "rewrite.txt", "content", "new"), Map.of()));

        assertThat(Files.readString(tempDir.resolve("rewrite.txt"))).isEqualTo("new");
    }

    @Test
    void writeReturnsByteCount() throws Exception {
        ToolOutput write = output(new FilesystemTools.WriteFileTool(tempDir.toString())
                .invoke(Map.of("path", "unicode.txt", "content", "第一行"), Map.of()));

        assertThat((Integer) data(write).get("bytes")).isGreaterThan(0);
    }

    @Test
    void editFileReplacesText() throws Exception {
        Files.writeString(tempDir.resolve("edit.txt"), "Hello Google DeepMind", StandardCharsets.UTF_8);

        ToolOutput edit = output(new FilesystemTools.EditFileTool(tempDir.toString())
                .invoke(Map.of("path", "edit.txt", "old_text", "Hello", "new_text", "Hell0"), Map.of()));

        assertThat(edit.isSuccess()).isTrue();
        assertThat(data(edit).get("replacements")).isEqualTo(1);
        assertThat(Files.readString(tempDir.resolve("edit.txt"))).contains("Hell0 Google");
    }

    @Test
    void editFileMissingOldTextFails() throws Exception {
        Files.writeString(tempDir.resolve("edit.txt"), "content", StandardCharsets.UTF_8);

        ToolOutput edit = output(new FilesystemTools.EditFileTool(tempDir.toString())
                .invoke(Map.of("path", "edit.txt", "old_text", "missing", "new_text", "x"), Map.of()));

        assertThat(edit.isSuccess()).isFalse();
        assertThat(edit.getError()).contains("old_text not found");
    }

    @Test
    void editFileRejectsPathEscape() {
        FilesystemTools.EditFileTool editTool = new FilesystemTools.EditFileTool(tempDir.toString());

        assertThatThrownBy(() -> editTool.invoke(Map.of("path", "../edit.txt", "old_text", "x", "new_text", "y"),
                Map.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void globAndListDir() throws Exception {
        seedWorkspaceFiles();

        ToolOutput glob = output(new FilesystemTools.GlobTool(tempDir.toString())
                .invoke(Map.of("pattern", "**/*.py"), Map.of()));
        ToolOutput list = output(new FilesystemTools.ListDirTool(tempDir.toString())
                .invoke(Map.of("path", ""), Map.of()));

        assertThat((List<String>) data(glob).get("matches")).containsExactly("a.py", "subdir/b.py");
        assertThat((List<Map<String, Object>>) data(list).get("entries"))
                .extracting(entry -> entry.get("name"))
                .contains("subdir", "a.py", "c.txt");
    }

    @Test
    void globReturnsSortedMatches() throws Exception {
        seedWorkspaceFiles();

        ToolOutput glob = output(new FilesystemTools.GlobTool(tempDir.toString())
                .invoke(Map.of("pattern", "**/*.py"), Map.of()));

        assertThat((List<String>) data(glob).get("matches")).containsExactly("a.py", "subdir/b.py");
    }

    @Test
    void globMatchesNestedPythonFiles() throws Exception {
        seedWorkspaceFiles();

        ToolOutput glob = output(new FilesystemTools.GlobTool(tempDir.toString())
                .invoke(Map.of("pattern", "**/*.py"), Map.of()));

        assertThat((List<String>) data(glob).get("matches")).contains("subdir/b.py");
    }

    @Test
    void globNoMatchesReturnsEmpty() throws Exception {
        seedWorkspaceFiles();

        ToolOutput glob = output(new FilesystemTools.GlobTool(tempDir.toString())
                .invoke(Map.of("pattern", "**/*.md"), Map.of()));

        assertThat((List<String>) data(glob).get("matches")).isEmpty();
    }

    @Test
    void grepToolFindsMatchingFiles() throws Exception {
        Files.writeString(tempDir.resolve("grep.txt"), "Target\nOther\nTarget\n", StandardCharsets.UTF_8);

        ToolOutput grep = output(new FilesystemTools.GrepTool(tempDir.toString())
                .invoke(Map.of("pattern", "Target", "path", ""), Map.of()));

        assertThat((List<String>) data(grep).get("matches")).hasSize(1);
    }

    @Test
    void grepToolRespectsPathSubdir() throws Exception {
        Files.createDirectories(tempDir.resolve("sub"));
        Files.writeString(tempDir.resolve("sub/main.py"), "needle", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("other.py"), "needle", StandardCharsets.UTF_8);

        ToolOutput grep = output(new FilesystemTools.GrepTool(tempDir.toString())
                .invoke(Map.of("pattern", "needle", "path", "sub"), Map.of()));

        assertThat((List<String>) data(grep).get("matches")).containsExactly(tempDir.resolve("sub/main.py").toString());
    }

    @Test
    void grepToolNoMatchesReturnsEmpty() throws Exception {
        Files.writeString(tempDir.resolve("grep.txt"), "haystack", StandardCharsets.UTF_8);

        ToolOutput grep = output(new FilesystemTools.GrepTool(tempDir.toString())
                .invoke(Map.of("pattern", "needle", "path", ""), Map.of()));

        assertThat((List<String>) data(grep).get("matches")).isEmpty();
    }

    @Test
    void listDirIdentifiesDirectories() throws Exception {
        seedWorkspaceFiles();

        ToolOutput list = output(new FilesystemTools.ListDirTool(tempDir.toString())
                .invoke(Map.of("path", ""), Map.of()));

        assertThat((List<Map<String, Object>>) data(list).get("entries"))
                .anySatisfy(entry -> {
                    assertThat(entry.get("name")).isEqualTo("subdir");
                    assertThat(entry.get("is_dir")).isEqualTo(true);
                });
    }

    @Test
    void listDirEmptyDirectory() throws Exception {
        Files.createDirectories(tempDir.resolve("empty"));

        ToolOutput list = output(new FilesystemTools.ListDirTool(tempDir.toString())
                .invoke(Map.of("path", "empty"), Map.of()));

        assertThat((List<Map<String, Object>>) data(list).get("entries")).isEmpty();
    }

    @Test
    void resolveWorkspacePathPreventsEscape() {
        assertThatThrownBy(() -> FilesystemTools.resolveWorkspacePath(tempDir.toString(), "../escape.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes workspace root");
    }

    @Test
    void appendHistoryWrite() throws Exception {
        Path historyPath = tempDir.resolve("history.json");

        FilesystemTools.appendOpHistory(historyPath, "/foo/bar.py", "write", null, "content");

        assertThat(loadHistory(historyPath).get("/foo/bar.py").get(0))
                .containsEntry("action", "write")
                .containsEntry("new_content", "content");
    }

    @Test
    void appendHistoryTrimsMaxEntries() throws Exception {
        Path historyPath = tempDir.resolve("history.json");

        for (int i = 0; i < FilesystemTools.MAX_HISTORY_PER_FILE + 10; i++) {
            FilesystemTools.appendOpHistory(historyPath, "/foo/bar.py", "edit", String.valueOf(i), String.valueOf(i + 1));
        }

        assertThat(loadHistory(historyPath).get("/foo/bar.py")).hasSize(FilesystemTools.MAX_HISTORY_PER_FILE);
    }

    @Test
    void detectDeletionRecordsMissingFile() throws Exception {
        Path historyPath = tempDir.resolve("history.json");
        Path target = tempDir.resolve("deleted.txt");
        Files.writeString(target, "old content", StandardCharsets.UTF_8);
        FilesystemTools.appendOpHistory(historyPath, target.toString(), "write", null, "old content");
        Files.delete(target);

        FilesystemTools.detectAndRecordDeletions(historyPath);

        List<LinkedHashMap<String, Object>> entries = loadHistory(historyPath).get(target.toString());
        assertThat(entries.get(entries.size() - 1)).containsEntry("action", "delete").containsEntry("old_content", "old content");
    }

    @Test
    void detectDeletionDoesNotDuplicateDeleteEntries() throws Exception {
        Path historyPath = tempDir.resolve("history.json");
        Path target = tempDir.resolve("deleted.txt");
        FilesystemTools.appendOpHistory(historyPath, target.toString(), "delete", "old", null);

        FilesystemTools.detectAndRecordDeletions(historyPath);

        assertThat(loadHistory(historyPath).get(target.toString())).hasSize(1);
    }

    @Test
    void recordRmTargetBeforeDeletion() throws Exception {
        Path historyPath = tempDir.resolve("history.json");
        Path target = tempDir.resolve("target.txt");
        Files.writeString(target, "before delete", StandardCharsets.UTF_8);

        FilesystemTools.recordRmTargetsBeforeDeletion(historyPath, List.of(target.toString()), Files::readString);

        assertThat(loadHistory(historyPath).get(target.toRealPath().toString()).get(0))
                .containsEntry("action", "delete")
                .containsEntry("old_content", "before delete");
    }

    @Test
    void recordRmTargetsSkipsMissingAndDirectories() throws Exception {
        Path historyPath = tempDir.resolve("history.json");

        FilesystemTools.recordRmTargetsBeforeDeletion(historyPath,
                List.of(tempDir.resolve("missing.txt").toString(), tempDir.toString()), Files::readString);

        assertThat(Files.exists(historyPath)).isFalse();
    }

    @Test
    void parseRmTargetsSimple() {
        assertThat(FilesystemTools.parseRmTargets("rm foo.py")).containsExactly("foo.py");
        assertThat(FilesystemTools.parseRmTargets("rm -f a.py b.py")).containsExactly("a.py", "b.py");
    }

    @Test
    void parseRmTargetsRejectsRecursive() {
        assertThat(FilesystemTools.parseRmTargets("rm -rf dir")).isEmpty();
        assertThat(FilesystemTools.parseRmTargets("rm -R dir")).isEmpty();
    }

    @Test
    void parseRmTargetsRejectsShellComposition() {
        assertThat(FilesystemTools.parseRmTargets("rm foo.py; echo done")).isEmpty();
        assertThat(FilesystemTools.parseRmTargets("rm foo.py | cat")).isEmpty();
    }

    @Test
    void parsePowerShellTargetsSimple() {
        assertThat(FilesystemTools.parsePsRemoveTargets("Remove-Item foo.py")).containsExactly("foo.py");
        assertThat(FilesystemTools.parsePsRemoveTargets("del foo.py")).containsExactly("foo.py");
    }

    @Test
    void parsePowerShellTargetsRejectsRecurse() {
        assertThat(FilesystemTools.parsePsRemoveTargets("Remove-Item -Recurse dir")).isEmpty();
    }

    @Test
    void parsePowerShellTargetsNamedPath() {
        assertThat(FilesystemTools.parsePsRemoveTargets("Remove-Item -Path foo.py")).containsExactly("foo.py");
        assertThat(FilesystemTools.parsePsRemoveTargets("Remove-Item -LiteralPath foo.py")).containsExactly("foo.py");
    }

    @Test
    void parsePowerShellTargetsRejectsWildcard() {
        assertThat(FilesystemTools.parsePsRemoveTargets("Remove-Item *.py")).isEmpty();
        assertThat(FilesystemTools.parsePsRemoveTargets("Remove-Item -Path *.py")).isEmpty();
    }

    @Test
    void appendHistoryIgnoresInvalidHistoryParent() throws Exception {
        Path notDirectory = tempDir.resolve("not-dir");
        Files.writeString(notDirectory, "file", StandardCharsets.UTF_8);

        FilesystemTools.appendOpHistory(notDirectory.resolve("history.json"), "/foo/bar.py", "write", null, "content");

        assertThat(Files.readString(notDirectory)).isEqualTo("file");
    }

    @Test
    void streamReadToolReturnsOneToolOutput() throws Exception {
        Files.writeString(tempDir.resolve("stream.txt"), "streamed", StandardCharsets.UTF_8);

        Iterator<Object> iterator = new FilesystemTools.ReadFileTool(tempDir.toString())
                .stream(Map.of("path", "stream.txt"), Map.of());
        List<Object> chunks = new ArrayList<>();
        iterator.forEachRemaining(chunks::add);

        assertThat(chunks).hasSize(1);
        assertThat(output(chunks.get(0)).isSuccess()).isTrue();
    }

    @Test
    void readToolRequiresPath() {
        FilesystemTools.ReadFileTool readTool = new FilesystemTools.ReadFileTool(tempDir.toString());

        assertThatThrownBy(() -> readTool.invoke(Map.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path is required");
    }

    @Test
    void writeToolRequiresPath() {
        FilesystemTools.WriteFileTool writeTool = new FilesystemTools.WriteFileTool(tempDir.toString());

        assertThatThrownBy(() -> writeTool.invoke(Map.of("content", "x"), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path is required");
    }

    @Test
    void editToolRequiresOldText() {
        FilesystemTools.EditFileTool editTool = new FilesystemTools.EditFileTool(tempDir.toString());

        assertThatThrownBy(() -> editTool.invoke(Map.of("path", "a.txt", "new_text", "x"), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("old_text is required");
    }

    private void seedWorkspaceFiles() throws Exception {
        Files.createDirectories(tempDir.resolve("subdir"));
        Files.writeString(tempDir.resolve("a.py"), "1", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("subdir/b.py"), "2", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("c.txt"), "3", StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(ToolOutput output) {
        return (Map<String, Object>) output.getData();
    }

    private static ToolOutput output(Object value) {
        return (ToolOutput) value;
    }

    private static LinkedHashMap<String, List<LinkedHashMap<String, Object>>> loadHistory(Path path) throws Exception {
        return MAPPER.readValue(path.toFile(), HISTORY_TYPE);
    }
}
