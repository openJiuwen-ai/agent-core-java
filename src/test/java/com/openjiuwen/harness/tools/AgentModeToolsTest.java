/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.harness.schema.AgentMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Mirrors Python's plan-mode helpers in
 * {@code openjiuwen/harness/tools/agent_mode_tools.py}.
 */
class AgentModeToolsTest {

    @TempDir
    private Path tempDir;

    @Test
    void generateWordSlugReturnsThreeSegments() {
        String slug = AgentModeTools.generateWordSlug();
        assertEquals(3, slug.split("-").length);
        assertFalse(slug.isBlank());
    }

    @Test
    void resolvePlanFilePathCreatesPlansDirectory() throws Exception {
        Path planPath = AgentModeTools.resolvePlanFilePath(tempDir.toString(), "demo-plan");

        assertEquals(tempDir.resolve(".plans").resolve("demo-plan.md"), planPath);
        assertTrue(Files.isDirectory(tempDir.resolve(".plans")));
    }

    @Test
    void switchModeRejectsInvalidAndUpdatesValidMode() throws Exception {
        FakePlanModeController controller = new FakePlanModeController(tempDir);
        AgentModeTools.SwitchModeTool tool = new AgentModeTools.SwitchModeTool(controller, "en");

        ToolOutput invalid = (ToolOutput) tool.invoke(Map.of("mode", "bad"), Map.of("session", "session-1"));
        assertFalse(invalid.isSuccess());
        assertTrue(invalid.getError().contains("Invalid mode"));

        ToolOutput valid = (ToolOutput) tool.invoke(Map.of("mode", "plan"), Map.of("session", "session-1"));
        assertTrue(valid.isSuccess());
        assertEquals(AgentMode.PLAN, controller.mode);
    }

    @Test
    void enterAndExitPlanModeRoundTripPlanFile() throws Exception {
        FakePlanModeController controller = new FakePlanModeController(tempDir);
        AgentModeTools.EnterPlanModeTool enterTool = new AgentModeTools.EnterPlanModeTool(controller, "en");
        AgentModeTools.ExitPlanModeTool exitTool = new AgentModeTools.ExitPlanModeTool(controller, "en");

        Object enterResult = enterTool.invoke(Map.of(), Map.of("session", "session-1"));
        assertTrue(String.valueOf(enterResult).contains("Plan file created at:"));
        assertNotNull(controller.slug);

        Path planPath = controller.getPlanFilePath("session-1");
        Files.writeString(planPath, "1. Keep parity", StandardCharsets.UTF_8);

        Object exitResult = exitTool.invoke(Map.of(), Map.of("session", "session-1"));
        assertTrue(String.valueOf(exitResult).contains("1. Keep parity"));
        assertTrue(controller.restoreCalled);
    }

    private static final class FakePlanModeController implements AgentModeTools.PlanModeController {
        private final Path workspaceRoot;
        private AgentMode mode;
        private String slug;
        private boolean restoreCalled;

        private FakePlanModeController(Path workspaceRoot) {
            this.workspaceRoot = workspaceRoot;
        }

        @Override
        public void switchMode(Object session, AgentMode mode) {
            this.mode = mode;
        }

        @Override
        public String getWorkspaceRoot() {
            return workspaceRoot.toString();
        }

        @Override
        public String getPlanSlug(Object session) {
            return slug;
        }

        @Override
        public void setPlanSlug(Object session, String slug) {
            this.slug = slug;
        }

        @Override
        public Path getPlanFilePath(Object session) {
            return workspaceRoot.resolve(".plans").resolve(slug + ".md");
        }

        @Override
        public void restoreModeAfterPlanExit(Object session) {
            restoreCalled = true;
        }
    }
}
