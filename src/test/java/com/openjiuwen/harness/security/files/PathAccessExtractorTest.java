/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security.files;

import com.openjiuwen.harness.security.fileguard.FileGuardAction;
import com.openjiuwen.harness.security.files.PathAccessExtractor.PathAccess;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PathAccessExtractorTest {

    private static String posix(Path p) {
        return p.toString().replace("\\", "/");
    }

    @Nested
    class ExtractNative {
        @Test
        void writeFile_filePath_extractsWrite() {
            List<PathAccess> out = PathAccessExtractor.extractNative("write_file",
                    Map.of("file_path", "/etc/hosts"), Path.of("/work"));
            assertThat(out).hasSize(1);
            assertThat(posix(out.get(0).getPath())).isEqualTo("/etc/hosts");
            assertThat(out.get(0).getAction()).isEqualTo(FileGuardAction.WRITE);
            assertThat(out.get(0).getSource()).isEqualTo("tool_arg");
        }

        @Test
        void readFile_filePath_extractsRead() {
            List<PathAccess> out = PathAccessExtractor.extractNative("read_file",
                    Map.of("file_path", "/etc/hosts"), Path.of("/work"));
            assertThat(out).hasSize(1);
            assertThat(posix(out.get(0).getPath())).isEqualTo("/etc/hosts");
            assertThat(out.get(0).getAction()).isEqualTo(FileGuardAction.READ);
        }

        @Test
        void readFile_pathKey_extractsRead() {
            List<PathAccess> out = PathAccessExtractor.extractNative("read_file",
                    Map.of("path", "/etc/hosts"), Path.of("/work"));
            assertThat(out).hasSize(1);
            assertThat(out.get(0).getAction()).isEqualTo(FileGuardAction.READ);
        }

        @Test
        void grep_path_extractsRead() {
            List<PathAccess> out = PathAccessExtractor.extractNative("grep",
                    Map.of("path", "/etc/hosts"), Path.of("/work"));
            assertThat(out).hasSize(1);
            assertThat(out.get(0).getAction()).isEqualTo(FileGuardAction.READ);
        }

        @Test
        void listDir_path_extractsRead() {
            List<PathAccess> out = PathAccessExtractor.extractNative("list_dir",
                    Map.of("path", "/work"), Path.of("/work"));
            assertThat(out).hasSize(1);
            assertThat(out.get(0).getAction()).isEqualTo(FileGuardAction.READ);
        }

        @Test
        void editFile_filePath_extractsWrite() {
            List<PathAccess> out = PathAccessExtractor.extractNative("edit_file",
                    Map.of("file_path", "/data/f.txt"), Path.of("/work"));
            assertThat(out).hasSize(1);
            assertThat(out.get(0).getAction()).isEqualTo(FileGuardAction.WRITE);
        }

        @Test
        void searchReplace_targetFile_extractsWrite() {
            List<PathAccess> out = PathAccessExtractor.extractNative("search_replace",
                    Map.of("target_file", "/data/f.txt"), Path.of("/work"));
            assertThat(out).hasSize(1);
            assertThat(out.get(0).getAction()).isEqualTo(FileGuardAction.WRITE);
        }

        @Test
        void unknownTool_extractsNothing() {
            List<PathAccess> out = PathAccessExtractor.extractNative("unknown_tool",
                    Map.of("file_path", "/etc/hosts"), Path.of("/work"));
            assertThat(out).isEmpty();
        }

        @Test
        void blankArg_extractsNothing() {
            List<PathAccess> out = PathAccessExtractor.extractNative("write_file",
                    Map.of("file_path", "  "), Path.of("/work"));
            assertThat(out).isEmpty();
        }

        @Test
        void relativePath_resolvedAgainstWorkspace() {
            List<PathAccess> out = PathAccessExtractor.extractNative("read_file",
                    Map.of("file_path", "a/b.txt"), Path.of("/work"));
            assertThat(out).hasSize(1);
            assertThat(posix(out.get(0).getPath())).isEqualTo("/work/a/b.txt");
        }

        @Test
        void nullArgs_extractsNothing() {
            assertThat(PathAccessExtractor.extractNative("read_file", null, Path.of("/work"))).isEmpty();
        }
    }

    @Nested
    class ExtractLegacy {
        @Test
        void rm_extractsWritePath() {
            List<PathAccess> out = PathAccessExtractor.extractLegacy("rm /etc/hosts");
            assertThat(out).hasSize(1);
            assertThat(posix(out.get(0).getPath())).isEqualTo("/etc/hosts");
            assertThat(out.get(0).getAction()).isEqualTo(FileGuardAction.WRITE);
        }

        @Test
        void cat_extractsReadPath() {
            List<PathAccess> out = PathAccessExtractor.extractLegacy("cat /etc/hosts");
            assertThat(out).hasSize(1);
            assertThat(posix(out.get(0).getPath())).isEqualTo("/etc/hosts");
            assertThat(out.get(0).getAction()).isEqualTo(FileGuardAction.READ);
        }

        @Test
        void cp_extractsReadThenWrite() {
            List<PathAccess> out = PathAccessExtractor.extractLegacy("cp /a /b");
            assertThat(out).hasSize(2);
            assertThat(posix(out.get(0).getPath())).isEqualTo("/a");
            assertThat(out.get(0).getAction()).isEqualTo(FileGuardAction.READ);
            assertThat(posix(out.get(1).getPath())).isEqualTo("/b");
            assertThat(out.get(1).getAction()).isEqualTo(FileGuardAction.WRITE);
        }

        @Test
        void mkdir_extractsWritePath() {
            List<PathAccess> out = PathAccessExtractor.extractLegacy("mkdir /new/dir");
            assertThat(out).hasSize(1);
            assertThat(out.get(0).getAction()).isEqualTo(FileGuardAction.WRITE);
        }

        @Test
        void flagsSkipped() {
            assertThat(PathAccessExtractor.extractLegacy("rm -rf /data")).hasSize(1);
            assertThat(PathAccessExtractor.extractLegacy("ls -la /work")).hasSize(1);
        }

        @Test
        void nonPathAwareCommand_extractsNothing() {
            assertThat(PathAccessExtractor.extractLegacy("echo hello")).isEmpty();
        }

        @Test
        void blankCommand_extractsNothing() {
            assertThat(PathAccessExtractor.extractLegacy("")).isEmpty();
            assertThat(PathAccessExtractor.extractLegacy(null)).isEmpty();
        }
    }
}
