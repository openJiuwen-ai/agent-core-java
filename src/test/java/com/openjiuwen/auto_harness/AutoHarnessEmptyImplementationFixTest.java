package com.openjiuwen.auto_harness;

import com.openjiuwen.auto_harness.agents.AutoHarnessAgentFactory;
import com.openjiuwen.auto_harness.infra.SessionBudgetController;
import com.openjiuwen.auto_harness.rails.BudgetRail;
import com.openjiuwen.auto_harness.rails.ContextRail;
import com.openjiuwen.auto_harness.rails.EditSafetyRail;
import com.openjiuwen.auto_harness.rails.ExperienceRail;
import com.openjiuwen.auto_harness.rails.SecurityRail;
import com.openjiuwen.auto_harness.schema.AutoHarnessConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.cli.rails.ToolTrackingRail;
import com.openjiuwen.harness.rails.LspRail;
import com.openjiuwen.harness.rails.SysOperationRail;
import com.openjiuwen.harness.rails.TaskPlanningRail;
import com.openjiuwen.harness.rails.skills.SkillUseRail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for auto-harness empty-implementation fixes.
 */
class AutoHarnessEmptyImplementationFixTest {

    @Test
    void autoHarnessFactoryAddsTaskPlanningRailWhenEnabled() {
        AutoHarnessConfig config = new AutoHarnessConfig();

        DeepAgent agent = AutoHarnessAgentFactory.createAutoHarnessAgent(
            config,
            null,
            null,
            List.of("implement"),
            true,
            true,
            true,
            null,
            null
        );

        DeepAgentConfig deepAgentConfig = (DeepAgentConfig) agent.getConfig();
        assertFalse(deepAgentConfig.getRails().contains(null));
        assertTrue(deepAgentConfig.getRails().stream().anyMatch(ToolTrackingRail.class::isInstance));
        assertTrue(deepAgentConfig.getRails().stream().anyMatch(SysOperationRail.class::isInstance));
        assertTrue(deepAgentConfig.getRails().stream().anyMatch(ContextRail.class::isInstance));
        assertTrue(deepAgentConfig.getRails().stream().anyMatch(LspRail.class::isInstance));
        assertTrue(deepAgentConfig.getRails().stream().anyMatch(ExperienceRail.class::isInstance));
        assertTrue(deepAgentConfig.getRails().stream().anyMatch(SecurityRail.class::isInstance));
        assertTrue(deepAgentConfig.getRails().stream().anyMatch(EditSafetyRail.class::isInstance));
        assertTrue(deepAgentConfig.getRails().stream().anyMatch(TaskPlanningRail.class::isInstance));
        SkillUseRail skillRail = deepAgentConfig.getRails().stream()
            .filter(SkillUseRail.class::isInstance)
            .map(SkillUseRail.class::cast)
            .findFirst()
            .orElseThrow();
        assertEquals(Set.of("implement"), skillRail.getEnabledSkills());
        assertTrue(skillRail.getSkillsDir().stream().anyMatch(path -> path.endsWith("auto_harness\\skills")
            || path.endsWith("auto_harness/skills")));
        assertTrue(deepAgentConfig.getWorkspace().getRootPath().contains("."));
        assertEquals("auto-harness_trusted_local", deepAgentConfig.getSysOperationId());
    }

    @Test
    void autoHarnessFactoryFiltersSkillRailByExistingRoots(@TempDir Path tempDir) throws Exception {
        Path skillsRoot = tempDir.resolve("skills");
        Files.createDirectories(skillsRoot.resolve("custom"));
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setSkillsDirs(List.of(skillsRoot.toString()));

        DeepAgent agent = AutoHarnessAgentFactory.createAutoHarnessAgent(
            config,
            null,
            null,
            List.of("custom", "missing"),
            true,
            false,
            true,
            null,
            null
        );

        DeepAgentConfig deepAgentConfig = (DeepAgentConfig) agent.getConfig();
        SkillUseRail skillRail = deepAgentConfig.getRails().stream()
            .filter(SkillUseRail.class::isInstance)
            .map(SkillUseRail.class::cast)
            .findFirst()
            .orElseThrow();
        assertEquals(Set.of("custom"), skillRail.getEnabledSkills());
        assertTrue(skillRail.getSkillsDir().contains(skillsRoot.toString()));
    }

    @Test
    void budgetRailAddsCostFromUsageMetadataAndMarksForceFinish() {
        SessionBudgetController budget = new SessionBudgetController(3600.0, 0.0001, 1200.0);
        BudgetRail rail = new BudgetRail(budget);
        UsageMetadata usage = UsageMetadata.builder()
            .inputTokens(100)
            .outputTokens(20)
            .build();
        AgentCallbackContext ctx = AgentCallbackContext.builder()
            .inputs(ModelCallInputs.builder().response(usage).build())
            .build();

        rail.afterModelCall(ctx);

        assertTrue(budget.isShouldStop());
        assertEquals("Cost budget exceeded", ((Map<?, ?>) ctx.getExtra().get("force_finish")).get("reason"));
    }

    @Test
    void budgetRailMarksForceFinishBeforeToolCallWhenSessionBudgetExceeded() {
        SessionBudgetController budget = new SessionBudgetController(3600.0, 1.0, 1200.0);
        budget.addCost(1.0);
        BudgetRail rail = new BudgetRail(budget);
        AgentCallbackContext ctx = AgentCallbackContext.builder().build();

        rail.beforeToolCall(ctx);

        assertEquals("Session budget exceeded", ((Map<?, ?>) ctx.getExtra().get("force_finish")).get("reason"));
    }

    @Test
    void editSafetyRailRejectsOutOfScopeWritesAndTracksAllowedEdits() {
        EditSafetyRail rail = new EditSafetyRail(1);
        ToolCallInputs blocked = ToolCallInputs.builder()
            .toolName("write_file")
            .toolArgs(Map.of("file_path", "secrets/token.txt"))
            .toolCall(ToolCall.builder().id("call-1").build())
            .build();
        AgentCallbackContext blockedCtx = AgentCallbackContext.builder().inputs(blocked).build();

        rail.beforeToolCall(blockedCtx);

        assertEquals(true, blockedCtx.getExtra().get("_skip_tool"));
        assertTrue(String.valueOf(blocked.getToolResult()).contains("Out-of-scope edit blocked"));

        ToolCallInputs allowed = ToolCallInputs.builder()
            .toolName("edit_file")
            .toolArgs(Map.of("file_path", "openjiuwen/core/Foo.java"))
            .build();
        rail.afterToolCall(AgentCallbackContext.builder().inputs(allowed).build());

        assertTrue(rail.getEditedFiles().contains("openjiuwen/core/Foo.java"));
    }
}
