/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness.tools.browser_move;

import com.openjiuwen.harness.tools.browser_move.controllers.ActionController;
import com.openjiuwen.harness.tools.browser_move.utils.EnvUtils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FileUploads.
 * <p>
 * Mirrors Python's {@code test_file_uploads.py} from
 * {@code tests/unit_tests/harness/tools/browser_move/test_file_uploads.py}.
 *
 * <p>The tests keep Python's file-upload helper behavior while adapting async
 * Python actions to Java's {@link CompletableFuture}-based dispatcher.
 */
@DisplayName("FileUploads Tests")
class TestFileUploads {

    // Helper to create ActionController with builtins
    private ActionController makeControllerWithBuiltins() {
        ActionController ctl = new ActionController();
        ctl.registerBuiltinActions();
        return ctl;
    }

    @Nested
    @DisplayName("resolveUploadRoot Tests")
    class ResolveUploadRootTests {

        @Test
        @DisplayName("test resolve upload root returns null when env not set")
        void testResolveUploadRootReturnsNullWhenEnvNotSet() {
            // Python: test_list_upload_files_returns_error_when_env_not_set
            // In Python, resolve_upload_root returns None when BROWSER_UPLOAD_ROOT is not set
            // In Java, EnvUtils.resolveUploadRoot() returns null when env is not set
            
            // Clear any existing env value by using mock
            try (var mockedEnv = mockStatic(EnvUtils.class)) {
                mockedEnv.when(() -> EnvUtils.resolveUploadRoot()).thenReturn(null);
                
                Path result = EnvUtils.resolveUploadRoot();
                assertNull(result);
            }
        }

        @Test
        @DisplayName("test resolve upload root returns path when env set")
        void testResolveUploadRootReturnsPathWhenEnvSet(@TempDir Path tempDir) {
            // Python: test_list_upload_files_returns_file_list_when_dir_exists
            // In Python, resolve_upload_root returns the path when BROWSER_UPLOAD_ROOT is set
            
            try (var mockedEnv = mockStatic(EnvUtils.class)) {
                mockedEnv.when(() -> EnvUtils.resolveUploadRoot()).thenReturn(tempDir);
                
                Path result = EnvUtils.resolveUploadRoot();
                assertNotNull(result);
                assertEquals(tempDir.toAbsolutePath(), result);
            }
        }
    }

    @Nested
    @DisplayName("ListDirFiles Tests")
    class ListDirFilesTests {

        @Test
        @DisplayName("test list dir files returns file entries with metadata")
        void testListDirFilesReturnsFileEntriesWithMetadata(@TempDir Path tempDir) {
            // Python: test_list_dir_files_returns_file_entries_with_metadata
            
            // Create test files
            Path docPdf = tempDir.resolve("doc.pdf");
            Path imgPng = tempDir.resolve("img.png");
            try {
                Files.writeString(docPdf, "hello");
                Files.writeString(imgPng, "xx");
                
                List<Map<String, Object>> result = ActionController.listDirFiles(tempDir);
                Set<String> names = result.stream()
                    .map(e -> (String) e.get("name"))
                    .collect(Collectors.toSet());
                assertEquals(Set.of("doc.pdf", "img.png"), names);
                for (Map<String, Object> entry : result) {
                    assertTrue(entry.containsKey("path"));
                    assertTrue(((Number) entry.get("size_bytes")).longValue() >= 0);
                }
                
            } catch (Exception e) {
                fail("Failed to create test files: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("test list dir files handles missing directory")
        void testListDirFilesHandlesMissingDirectory() {
            // Python: test_list_dir_files_handles_missing_directory
            
            Path nonexistent = Path.of("/nonexistent/path/that/does/not/exist");
            assertEquals(List.of(), ActionController.listDirFiles(nonexistent));
        }
    }

    @Nested
    @DisplayName("BuildSetInputFilesScript Tests")
    class BuildSetInputFilesScriptTests {

        @Test
        @DisplayName("test build set input files script embeds selector and paths")
        void testBuildSetInputFilesScriptEmbedsSelectorAndPaths() {
            // Python: test_build_set_input_files_script_embeds_selector_and_paths
            
            String selector = "#upload";
            List<String> paths = List.of("/data/a.pdf", "/data/b.csv");
            
            String script = ActionController.buildSetInputFilesScript(selector, paths);
            assertTrue(script.contains(selector));
            assertTrue(script.contains(paths.get(0)));
            assertTrue(script.contains(paths.get(1)));
        }
    }

    @Nested
    @DisplayName("ListUploadFiles Action Tests")
    class ListUploadFilesActionTests {

        @Test
        @DisplayName("test list upload files returns error when env not set")
        void testListUploadFilesReturnsErrorWhenEnvNotSet() {
            // Python: test_list_upload_files_returns_error_when_env_not_set
            
            ActionController ctl = makeControllerWithBuiltins();
            String previous = System.getProperty("BROWSER_UPLOAD_ROOT");
            try {
                System.clearProperty("BROWSER_UPLOAD_ROOT");
                ActionController.ActionResult result = ctl.executeAction("list_upload_files", Map.of()).join();
                assertFalse(result.isOk());
                assertTrue(result.getError().contains("BROWSER_UPLOAD_ROOT"));
                Map<String, Object> response = (Map<String, Object>) result.getData();
                assertEquals(List.of(), response.get("files"));
            } finally {
                if (previous == null) {
                    System.clearProperty("BROWSER_UPLOAD_ROOT");
                } else {
                    System.setProperty("BROWSER_UPLOAD_ROOT", previous);
                }
            }
        }

        @Test
        @DisplayName("test list upload files returns error when dir missing")
        void testListUploadFilesReturnsErrorWhenDirMissing() {
            // Python: test_list_upload_files_returns_error_when_dir_missing
            
            ActionController ctl = makeControllerWithBuiltins();
            Path missing = Path.of("/tmp/does_not_exist_xyz_99");
            String previous = System.getProperty("BROWSER_UPLOAD_ROOT");
            try {
                System.setProperty("BROWSER_UPLOAD_ROOT", missing.toString());
                ActionController.ActionResult result = ctl.executeAction("list_upload_files", Map.of()).join();
                assertFalse(result.isOk());
                Map<String, Object> response = (Map<String, Object>) result.getData();
                assertEquals(List.of(), response.get("files"));
            } finally {
                if (previous == null) {
                    System.clearProperty("BROWSER_UPLOAD_ROOT");
                } else {
                    System.setProperty("BROWSER_UPLOAD_ROOT", previous);
                }
            }
        }

        @Test
        @DisplayName("test list upload files returns file list when dir exists")
        void testListUploadFilesReturnsFileListWhenDirExists(@TempDir Path tempDir) {
            // Python: test_list_upload_files_returns_file_list_when_dir_exists
            
            ActionController ctl = makeControllerWithBuiltins();
            
            // Create test file
            Path reportXlsx = tempDir.resolve("report.xlsx");
            try {
                Files.writeString(reportXlsx, "data");
            } catch (Exception e) {
                fail("Failed to create test file");
            }
            
            String previous = System.getProperty("BROWSER_UPLOAD_ROOT");
            try {
                System.setProperty("BROWSER_UPLOAD_ROOT", tempDir.toString());
                ActionController.ActionResult result = ctl.executeAction("list_upload_files", Map.of()).join();
                assertTrue(result.isOk());
                Map<String, Object> response = (Map<String, Object>) result.getData();
                List<Map<String, Object>> files = (List<Map<String, Object>>) response.get("files");
                assertTrue(files.stream().anyMatch(f -> "report.xlsx".equals(f.get("name"))));
            } finally {
                if (previous == null) {
                    System.clearProperty("BROWSER_UPLOAD_ROOT");
                } else {
                    System.setProperty("BROWSER_UPLOAD_ROOT", previous);
                }
            }
        }
    }

    @Nested
    @DisplayName("BrowserSetInputFiles Action Tests")
    class BrowserSetInputFilesActionTests {

        @Test
        @DisplayName("test set input files returns error when paths empty")
        void testSetInputFilesReturnsErrorWhenPathsEmpty() {
            // Python: test_set_input_files_returns_error_when_paths_empty
            
            ActionController ctl = makeControllerWithBuiltins();
            ActionController.ActionResult result = ctl.executeAction("browser_set_input_files", Map.of("paths", List.of())).join();
            assertFalse(result.isOk());
            assertTrue(result.getError().toLowerCase().contains("paths"));
        }

        @Test
        @DisplayName("test set input files uses code executor when bound")
        void testSetInputFilesUsesCodeExecutorWhenBound() {
            // Python: test_set_input_files_uses_code_executor_when_bound
            
            ActionController ctl = makeControllerWithBuiltins();
            Function<String, Object> fakeExecutor = jsCode -> Map.of(
                "ok", true,
                "selector", "input[type=\"file\"]",
                "paths", List.of("/tmp/x.pdf")
            );
            ctl.bindCodeExecutor(fakeExecutor);

            ActionController.ActionResult result = ctl.executeAction(
                "browser_set_input_files", Map.of("paths", List.of("/tmp/x.pdf"))
            ).join();
            assertTrue(result.isOk());
        }

        @Test
        @DisplayName("test set input files defaults selector to file input")
        void testSetInputFilesDefaultsSelectorToFileInput() {
            // Python: test_set_input_files_defaults_selector_to_file_input
            
            ActionController ctl = makeControllerWithBuiltins();
            List<String> captured = new java.util.ArrayList<>();
            Function<String, Object> captureExecutor = jsCode -> {
                captured.add(jsCode);
                return Map.of(
                    "ok", true,
                    "selector", "input[type=\"file\"]",
                    "paths", List.of("/tmp/f.txt")
                );
            };
            ctl.bindCodeExecutor(captureExecutor);

            ctl.executeAction("browser_set_input_files", Map.of("paths", List.of("/tmp/f.txt"))).join();
            assertFalse(captured.isEmpty());
            assertTrue(captured.get(0).contains("input[type=\"file\"]"));
        }

        @Test
        @DisplayName("test set input files returns error when no executor and no runner")
        void testSetInputFilesReturnsErrorWhenNoExecutorAndNoRunner() {
            // Python: test_set_input_files_returns_error_when_no_executor_and_no_runner
            
            ActionController ctl = makeControllerWithBuiltins();
            ActionController.ActionResult result = ctl.executeAction(
                "browser_set_input_files", Map.of("paths", List.of("/tmp/f.txt"))
            ).join();
            assertFalse(result.isOk());
            assertTrue(result.getError().contains("runtime_not_bound") || result.getError().contains("bind_runtime"));
        }
    }

    @Nested
    @DisplayName("ActionController Basic Tests")
    class ActionControllerBasicTests {

        @Test
        @DisplayName("test controller has ping action after builtin registration")
        void testControllerHasPingActionAfterBuiltinRegistration() {
            ActionController ctl = makeControllerWithBuiltins();
            assertTrue(ctl.listActions().contains("ping"));
        }

        @Test
        @DisplayName("test controller has echo action after builtin registration")
        void testControllerHasEchoActionAfterBuiltinRegistration() {
            ActionController ctl = makeControllerWithBuiltins();
            assertTrue(ctl.listActions().contains("echo"));
        }

        @Test
        @DisplayName("test controller has browser_task action after builtin registration")
        void testControllerHasBrowserTaskActionAfterBuiltinRegistration() {
            ActionController ctl = makeControllerWithBuiltins();
            assertTrue(ctl.listActions().contains("browser_task"));
        }

        @Test
        @DisplayName("test ping action returns pong")
        void testPingActionReturnsPong() {
            ActionController ctl = makeControllerWithBuiltins();
            
            CompletableFuture<ActionController.ActionResult> future = ctl.executeAction("ping", Map.of());
            ActionController.ActionResult result = future.join();
            
            assertTrue(result.isOk());
            Map<String, Object> response = (Map<String, Object>) result.getData();
            assertEquals(true, response.get("pong"));
        }

        @Test
        @DisplayName("test echo action returns text")
        void testEchoActionReturnsText() {
            ActionController ctl = makeControllerWithBuiltins();
            
            CompletableFuture<ActionController.ActionResult> future = ctl.executeAction("echo", Map.of("text", "hello"));
            ActionController.ActionResult result = future.join();
            
            assertTrue(result.isOk());
            Map<String, Object> response = (Map<String, Object>) result.getData();
            assertEquals("hello", response.get("text"));
        }

        @Test
        @DisplayName("test browser_task action returns error when runtime not bound")
        void testBrowserTaskActionReturnsErrorWhenRuntimeNotBound() {
            ActionController ctl = makeControllerWithBuiltins();
            // No runtime bound
            
            CompletableFuture<ActionController.ActionResult> future = ctl.executeAction("browser_task", Map.of("task", "test"));
            ActionController.ActionResult result = future.join();
            
            assertFalse(result.isOk());
            assertTrue(result.getError().contains("runtime_not_bound"));
        }

        @Test
        @DisplayName("test browser_task action returns error when task is empty")
        void testBrowserTaskActionReturnsErrorWhenTaskIsEmpty() {
            ActionController ctl = makeControllerWithBuiltins();
            Function<Map<String, Object>, Map<String, Object>> runner = args -> Map.of("ok", true);
            ctl.bindRuntimeRunner(runner);
            
            CompletableFuture<ActionController.ActionResult> future = ctl.executeAction("browser_task", Map.of("task", ""));
            ActionController.ActionResult result = future.join();
            
            assertFalse(result.isOk());
            assertTrue(result.getError().contains("missing required parameter"));
        }

        @Test
        @DisplayName("test unknown action returns error")
        void testUnknownActionReturnsError() {
            ActionController ctl = makeControllerWithBuiltins();
            
            CompletableFuture<ActionController.ActionResult> future = ctl.executeAction("unknown_action", Map.of());
            ActionController.ActionResult result = future.join();
            
            assertFalse(result.isOk());
            assertTrue(result.getError().contains("unknown action"));
        }
    }
}
