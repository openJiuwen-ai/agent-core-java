/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Supplemental tests for filesystem operation history helpers.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/harness/tools/test_file_op_history.py}.</p>
 */
class FileOpHistoryMissingTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, List<Map<String, Object>>>> HISTORY_TYPE =
            new TypeReference<>() {
            };

    @TempDir
    Path tempDir;

    @Test
    void testCreatesHistoryFile() {
        Path historyPath = historyPath("file_ops_test.json");

        FilesystemTools.appendOpHistory(historyPath, "/foo/bar.py", "write", null, "content");

        assertThat(historyPath).exists();
    }

    @Test
    void testEntryFields() throws Exception {
        Path historyPath = historyPath("file_ops_test.json");

        FilesystemTools.appendOpHistory(historyPath, "/foo/bar.py", "write", null, "hello");
        Map<String, Object> entry = load(historyPath).get("/foo/bar.py").get(0);

        assertThat(entry).containsEntry("action", "write")
                .containsEntry("old_content", null)
                .containsEntry("new_content", "hello")
                .containsKey("timestamp");
    }

    @Test
    void testOldContentNoneForCreate() throws Exception {
        Path historyPath = historyPath("file_ops_test.json");

        FilesystemTools.appendOpHistory(historyPath, "/foo/new.py", "write", null, "body");

        assertThat(load(historyPath).get("/foo/new.py").get(0)).containsEntry("old_content", null);
    }

    @Test
    void testEditPreservesOldAndNew() throws Exception {
        Path historyPath = historyPath("file_ops_test.json");

        FilesystemTools.appendOpHistory(historyPath, "/foo/bar.py", "edit", "old", "new");
        Map<String, Object> entry = load(historyPath).get("/foo/bar.py").get(0);

        assertThat(entry).containsEntry("action", "edit")
                .containsEntry("old_content", "old")
                .containsEntry("new_content", "new");
    }

    @Test
    void testMultipleEntriesAppendedInOrder() throws Exception {
        Path historyPath = historyPath("file_ops_test.json");

        FilesystemTools.appendOpHistory(historyPath, "/foo/bar.py", "write", null, "v1");
        FilesystemTools.appendOpHistory(historyPath, "/foo/bar.py", "edit", "v1", "v2");
        FilesystemTools.appendOpHistory(historyPath, "/foo/bar.py", "edit", "v2", "v3");

        List<Map<String, Object>> entries = load(historyPath).get("/foo/bar.py");
        assertThat(entries).hasSize(3);
        assertThat(entries).extracting(entry -> entry.get("new_content"))
                .containsExactly("v1", "v2", "v3");
    }

    @Test
    void testMultipleFilesTrackedSeparately() throws Exception {
        Path historyPath = historyPath("file_ops_test.json");

        FilesystemTools.appendOpHistory(historyPath, "/foo/a.py", "write", null, "a");
        FilesystemTools.appendOpHistory(historyPath, "/foo/b.py", "write", null, "b");
        Map<String, List<Map<String, Object>>> data = load(historyPath);

        assertThat(data.get("/foo/a.py").get(0)).containsEntry("new_content", "a");
        assertThat(data.get("/foo/b.py").get(0)).containsEntry("new_content", "b");
    }

    @Test
    void testAppendsToExistingHistory() throws Exception {
        Path historyPath = historyPath("file_ops_test.json");

        FilesystemTools.appendOpHistory(historyPath, "/foo/bar.py", "write", null, "v1");
        FilesystemTools.appendOpHistory(historyPath, "/foo/bar.py", "edit", "v1", "v2");

        assertThat(load(historyPath).get("/foo/bar.py")).hasSize(2);
    }

    @Test
    void testExistingOtherFileEntriesPreserved() throws Exception {
        Path historyPath = historyPath("file_ops_test.json");

        FilesystemTools.appendOpHistory(historyPath, "/foo/a.py", "write", null, "a");
        FilesystemTools.appendOpHistory(historyPath, "/foo/b.py", "write", null, "b");
        FilesystemTools.appendOpHistory(historyPath, "/foo/a.py", "edit", "a", "a2");
        Map<String, List<Map<String, Object>>> data = load(historyPath);

        assertThat(data.get("/foo/a.py")).hasSize(2);
        assertThat(data.get("/foo/b.py")).hasSize(1);
    }

    @Test
    void testEntriesCappedAtMax() throws Exception {
        Path historyPath = historyPath("file_ops_test.json");

        for (int i = 0; i < FilesystemTools.MAX_HISTORY_PER_FILE + 10; i++) {
            FilesystemTools.appendOpHistory(historyPath, "/foo/bar.py", "edit",
                    String.valueOf(i), String.valueOf(i + 1));
        }

        assertThat(load(historyPath).get("/foo/bar.py")).hasSize(FilesystemTools.MAX_HISTORY_PER_FILE);
    }

    @Test
    void testOldestEntriesDroppedWhenCapped() throws Exception {
        Path historyPath = historyPath("file_ops_test.json");

        for (int i = 0; i < FilesystemTools.MAX_HISTORY_PER_FILE + 5; i++) {
            FilesystemTools.appendOpHistory(historyPath, "/foo/bar.py", "edit",
                    String.valueOf(i), String.valueOf(i + 1));
        }
        List<Map<String, Object>> entries = load(historyPath).get("/foo/bar.py");

        assertThat(entries.get(0)).containsEntry("old_content", "5");
        assertThat(entries.get(entries.size() - 1))
                .containsEntry("old_content", String.valueOf(FilesystemTools.MAX_HISTORY_PER_FILE + 4));
    }

    @Test
    void testInvalidHistoryPathDoesNotRaise() throws Exception {
        Path notDirectory = tempDir.resolve("not-directory");
        Files.writeString(notDirectory, "file");

        FilesystemTools.appendOpHistory(notDirectory.resolve("file_ops.json"), "/foo/bar.py",
                "write", null, "content");

        assertThat(notDirectory).isRegularFile();
    }

    @Test
    void testCorruptedJsonDoesNotRaise() throws Exception {
        Path historyPath = historyPath("file_ops_test.json");
        Files.createDirectories(historyPath.getParent());
        Files.writeString(historyPath, "not valid json{{{");

        FilesystemTools.appendOpHistory(historyPath, "/foo/bar.py", "write", null, "content");

        assertThat(Files.readString(historyPath)).isEqualTo("not valid json{{{");
    }

    @Test
    void testConcurrentCoroutinesDoNotCorrupt() throws Exception {
        Path historyPath = historyPath("file_ops_test.json");
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            int value = i;
            futures.add(CompletableFuture.runAsync(() -> FilesystemTools.appendOpHistory(historyPath,
                    "/foo/bar.py", "edit", String.valueOf(value), String.valueOf(value + 1))));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        Map<String, List<Map<String, Object>>> data = load(historyPath);

        assertThat(data).containsKey("/foo/bar.py");
        assertThat(data.get("/foo/bar.py")).hasSizeLessThanOrEqualTo(FilesystemTools.MAX_HISTORY_PER_FILE);
    }

    @Test
    void testParseRmSimpleFile() {
        assertThat(FilesystemTools.parseRmTargets("rm foo.py")).containsExactly("foo.py");
    }

    @Test
    void testParseRmForceFlagSingleFile() {
        assertThat(FilesystemTools.parseRmTargets("rm -f foo.py")).containsExactly("foo.py");
    }

    @Test
    void testParseRmMultipleExplicitFiles() {
        assertThat(FilesystemTools.parseRmTargets("rm -f a.py b.py")).containsExactly("a.py", "b.py");
    }

    @Test
    void testParseRmRecursiveFlagReturnsEmpty() {
        assertThat(FilesystemTools.parseRmTargets("rm -rf dir/")).isEmpty();
    }

    @Test
    void testParseRmUppercaseRecursiveFlagReturnsEmpty() {
        assertThat(FilesystemTools.parseRmTargets("rm -R dir/")).isEmpty();
    }

    @Test
    void testParseRmGlobPatternSkipped() {
        assertThat(FilesystemTools.parseRmTargets("rm *.py")).isEmpty();
    }

    @Test
    void testParseRmCompoundCommandWithSemicolonReturnsEmpty() {
        assertThat(FilesystemTools.parseRmTargets("rm foo.py; echo done")).isEmpty();
    }

    @Test
    void testParseRmCompoundCommandWithPipeReturnsEmpty() {
        assertThat(FilesystemTools.parseRmTargets("rm foo.py | cat")).isEmpty();
    }

    @Test
    void testParseRmSubcommandReturnsEmpty() {
        assertThat(FilesystemTools.parseRmTargets("rm $(find . -name '*.py')")).isEmpty();
    }

    @Test
    void testParseRmNonRmCommandReturnsEmpty() {
        assertThat(FilesystemTools.parseRmTargets("ls -la")).isEmpty();
    }

    @Test
    void testParseRmEmptyCommandReturnsEmpty() {
        assertThat(FilesystemTools.parseRmTargets("")).isEmpty();
    }

    @Test
    void testParseRmMixedGlobAndExplicit() {
        assertThat(FilesystemTools.parseRmTargets("rm *.py explicit.py")).containsExactly("explicit.py");
    }

    @Test
    void testDetectAndRecordDeletionsRecordsDeleteForMissingFile() throws Exception {
        Path historyPath = historyPath("ops.json");
        Path target = tempDir.resolve("foo.py");
        Files.writeString(target, "old content");
        FilesystemTools.appendOpHistory(historyPath, target.toString(), "write", null, "old content");
        Files.delete(target);

        FilesystemTools.detectAndRecordDeletions(historyPath);

        List<Map<String, Object>> entries = load(historyPath).get(target.toString());
        Map<String, Object> last = entries.get(entries.size() - 1);
        assertThat(last).containsEntry("action", "delete")
                .containsEntry("old_content", "old content")
                .containsEntry("new_content", null);
    }

    @Test
    void testDetectAndRecordDeletionsDoesNotDoubleRecordDelete() throws Exception {
        Path historyPath = historyPath("ops.json");
        Path target = tempDir.resolve("foo.py");
        Files.writeString(target, "content");
        FilesystemTools.appendOpHistory(historyPath, target.toString(), "write", null, "content");
        Files.delete(target);

        FilesystemTools.detectAndRecordDeletions(historyPath);
        FilesystemTools.detectAndRecordDeletions(historyPath);

        long deleteCount = load(historyPath).get(target.toString()).stream()
                .filter(entry -> "delete".equals(entry.get("action")))
                .count();
        assertThat(deleteCount).isEqualTo(1);
    }

    @Test
    void testDetectAndRecordDeletionsStillExistingFileNotMarkedDeleted() throws Exception {
        Path historyPath = historyPath("ops.json");
        Path target = tempDir.resolve("foo.py");
        Files.writeString(target, "content");
        FilesystemTools.appendOpHistory(historyPath, target.toString(), "write", null, "content");

        FilesystemTools.detectAndRecordDeletions(historyPath);

        assertThat(load(historyPath).get(target.toString()))
                .allSatisfy(entry -> assertThat(entry).doesNotContainEntry("action", "delete"));
    }

    @Test
    void testDetectAndRecordDeletionsNoHistoryFileIsNoop() {
        Path historyPath = historyPath("ops.json");

        FilesystemTools.detectAndRecordDeletions(historyPath);

        assertThat(historyPath).doesNotExist();
    }

    @Test
    void testDetectAndRecordDeletionsDeleteOldContentTakenFromLastEntryNewContent() throws Exception {
        Path historyPath = historyPath("ops.json");
        Path target = tempDir.resolve("foo.py");
        Files.writeString(target, "v2");
        FilesystemTools.appendOpHistory(historyPath, target.toString(), "write", null, "v1");
        FilesystemTools.appendOpHistory(historyPath, target.toString(), "edit", "v1", "v2");
        Files.delete(target);

        FilesystemTools.detectAndRecordDeletions(historyPath);

        assertThat(lastEntry(historyPath, target.toString()))
                .containsEntry("action", "delete")
                .containsEntry("old_content", "v2");
    }

    @Test
    void testRecordRmTargetsBeforeDeletionRecordsContentForExistingFile() throws Exception {
        Path historyPath = historyPath("ops.json");
        Path target = tempDir.resolve("foo.py");
        Files.writeString(target, "file content");

        FilesystemTools.recordRmTargetsBeforeDeletion(historyPath, List.of(target.toString()), Files::readString);

        String resolvedTarget = target.toRealPath().toString();
        assertThat(load(historyPath)).containsKey(resolvedTarget);
        assertThat(load(historyPath).get(resolvedTarget).get(0))
                .containsEntry("action", "delete")
                .containsEntry("old_content", "file content")
                .containsEntry("new_content", null);
    }

    @Test
    void testRecordRmTargetsBeforeDeletionSkipsNonexistentFile() {
        Path historyPath = historyPath("ops.json");

        FilesystemTools.recordRmTargetsBeforeDeletion(historyPath, List.of(tempDir.resolve("missing.py").toString()),
                Files::readString);

        assertThat(historyPath).doesNotExist();
    }

    @Test
    void testRecordRmTargetsBeforeDeletionSkipsDirectory() {
        Path historyPath = historyPath("ops.json");

        FilesystemTools.recordRmTargetsBeforeDeletion(historyPath, List.of(tempDir.toString()), Files::readString);

        assertThat(historyPath).doesNotExist();
    }

    @Test
    void testRecordRmTargetsBeforeDeletionMultipleTargetsAllRecorded() throws Exception {
        Path historyPath = historyPath("ops.json");
        Path first = tempDir.resolve("a.py");
        Path second = tempDir.resolve("b.py");
        Files.writeString(first, "content of a.py");
        Files.writeString(second, "content of b.py");

        FilesystemTools.recordRmTargetsBeforeDeletion(historyPath, List.of(first.toString(), second.toString()),
                Files::readString);

        assertThat(load(historyPath)).containsKeys(first.toRealPath().toString(), second.toRealPath().toString());
    }

    @Test
    void testParsePsSimpleRemoveItem() {
        assertThat(FilesystemTools.parsePsRemoveTargets("Remove-Item foo.py")).containsExactly("foo.py");
    }

    @Test
    void testParsePsAliasRm() {
        assertThat(FilesystemTools.parsePsRemoveTargets("rm foo.py")).containsExactly("foo.py");
    }

    @Test
    void testParsePsAliasDel() {
        assertThat(FilesystemTools.parsePsRemoveTargets("del foo.py")).containsExactly("foo.py");
    }

    @Test
    void testParsePsAliasRi() {
        assertThat(FilesystemTools.parsePsRemoveTargets("ri foo.py")).containsExactly("foo.py");
    }

    @Test
    void testParsePsAliasErase() {
        assertThat(FilesystemTools.parsePsRemoveTargets("erase foo.py")).containsExactly("foo.py");
    }

    @Test
    void testParsePsForceFlag() {
        assertThat(FilesystemTools.parsePsRemoveTargets("Remove-Item -Force foo.py")).containsExactly("foo.py");
    }

    @Test
    void testParsePsPathFlag() {
        assertThat(FilesystemTools.parsePsRemoveTargets("Remove-Item -Path foo.py")).containsExactly("foo.py");
    }

    @Test
    void testParsePsLiteralPathFlag() {
        assertThat(FilesystemTools.parsePsRemoveTargets("Remove-Item -LiteralPath foo.py")).containsExactly("foo.py");
    }

    @Test
    void testParsePsMultipleExplicitFiles() {
        assertThat(FilesystemTools.parsePsRemoveTargets("Remove-Item a.py b.py")).containsExactly("a.py", "b.py");
    }

    @Test
    void testParsePsWindowsAbsolutePath() {
        assertThat(FilesystemTools.parsePsRemoveTargets("Remove-Item C:/work/foo.py"))
                .containsExactly("C:/work/foo.py");
    }

    @Test
    void testParsePsRecurseReturnsEmpty() {
        assertThat(FilesystemTools.parsePsRemoveTargets("Remove-Item -Recurse dir")).isEmpty();
    }

    @Test
    void testParsePsWildcardReturnsEmpty() {
        assertThat(FilesystemTools.parsePsRemoveTargets("Remove-Item *.py")).isEmpty();
    }

    @Test
    void testParsePsCompoundWithSemicolonReturnsEmpty() {
        assertThat(FilesystemTools.parsePsRemoveTargets("Remove-Item foo.py; echo done")).isEmpty();
    }

    @Test
    void testParsePsCompoundWithPipeReturnsEmpty() {
        assertThat(FilesystemTools.parsePsRemoveTargets("Remove-Item foo.py | Out-Null")).isEmpty();
    }

    @Test
    void testParsePsNonRemoveCommandReturnsEmpty() {
        assertThat(FilesystemTools.parsePsRemoveTargets("Get-Item foo.py")).isEmpty();
    }

    @Test
    void testParsePsEmptyCommandReturnsEmpty() {
        assertThat(FilesystemTools.parsePsRemoveTargets("")).isEmpty();
    }

    @Test
    void testParsePsErrorActionFlagStripped() {
        assertThat(FilesystemTools.parsePsRemoveTargets("Remove-Item -ErrorAction SilentlyContinue foo.py"))
                .containsExactly("foo.py");
    }

    @Test
    void testParsePsCaseInsensitiveCommand() {
        assertThat(FilesystemTools.parsePsRemoveTargets("remove-item foo.py")).containsExactly("foo.py");
    }

    @Test
    void testParsePsGlobInPathFlagReturnsEmpty() {
        assertThat(FilesystemTools.parsePsRemoveTargets("Remove-Item -Path *.py")).isEmpty();
    }

    private Path historyPath(String fileName) {
        return tempDir.resolve(".agent_history").resolve(fileName);
    }

    private Map<String, List<Map<String, Object>>> load(Path path) throws Exception {
        return MAPPER.readValue(path.toFile(), HISTORY_TYPE);
    }

    private Map<String, Object> lastEntry(Path historyPath, String target) throws Exception {
        List<Map<String, Object>> entries = load(historyPath).get(target);
        return entries.get(entries.size() - 1);
    }
}
