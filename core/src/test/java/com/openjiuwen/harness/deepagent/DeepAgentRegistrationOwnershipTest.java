/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deepagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.tool.NoopTool;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.runner.resourcemanager.ResourceMgr;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The harness-side faces of the shared-registration ownership contract
 * on the process-global ResourceMgr — cross-instance shared entries survive
 * a destroying owner, registration failures surface visibly at the call
 * site, and HarnessFactory.createDeepAgent fails visibly on a conflicting
 * configured tool instead of silently skipping it.
 *
 * <p>These cases construct DeepAgent instances or call HarnessFactory, so
 * they live in the harness test tree (core must not depend on harness).
 * The ResourceMgr-level faces of the same contract stay in
 * {@code com.openjiuwen.core.runner.resourcemanager.ResourceMgrOwnershipTest};
 * the ownership table is reached through reflection the same way.</p>
 *
 * @since 0.1.16
 */
@DisplayName("DeepAgent/HarnessFactory ownership landing points (global registry)")
class DeepAgentRegistrationOwnershipTest {
    private final List<String> trackedToolIds = new ArrayList<>();
    private final List<String> trackedSysOpIds = new ArrayList<>();

    /**
     * Force-removes every entry the test registered. The public remove
     * overloads are the declared explicit-management escape hatch and
     * ignore ownership by design, which keeps tests isolated regardless
     * of how many owners an entry accumulated.
     */
    @AfterEach
    void cleanupGlobalEntries() {
        for (String toolId : trackedToolIds) {
            Runner.resourceMgr().removeTool(toolId, null, TagMatchStrategy.ALL, true);
        }
        for (String sysOpId : trackedSysOpIds) {
            Runner.resourceMgr().removeSysOperation(sysOpId, null, TagMatchStrategy.ALL, true);
        }
    }

    /**
     * two directly constructed DeepAgents sharing one card id
     * (EDPA shape: same cardId, distinct instance owner tokens) register
     * an equivalent tool; the first instance's destroy only releases its
     * own claim, the surviving instance keeps resolving and executing
     * against the shared entry, and the last release removes it with no
     * residue. The registration-failure retry interplay with the
     * lifecycle state machine is covered by the state-machine tests.
     *
     * @throws Exception when agent construction, registration, or the reflection path fails
     */
    @Test
    @DisplayName("Same-card instances share the entry; destroy releases only the destroying owner")
    void sameCardIdCrossDestroyKeepsSurvivingOwnersEntry() throws Exception {
        String toolId = uniqueId("ut14-tool");
        AgentCard sharedCard = AgentCard.builder().id(uniqueId("ut14-agent")).name("ut14-agent")
                .description("shared card").build();
        Tool firstTool = tool(toolCard(toolId, "ut14-tool", "shared tool"));
        Tool secondTool = tool(toolCard(toolId, "ut14-tool", "shared tool"));
        trackedToolIds.add(toolId);

        DeepAgent firstAgent = new DeepAgent(sharedCard, DeepAgentConfig.builder().build(),
                Workspace.builder().rootPath(Files.createTempDirectory("dfx005-ut14").toString()).build());
        firstAgent.registerHarnessTool(firstTool);
        DeepAgent secondAgent = new DeepAgent(sharedCard, DeepAgentConfig.builder().build(),
                Workspace.builder().rootPath(Files.createTempDirectory("dfx005-ut14").toString()).build());
        secondAgent.registerHarnessTool(secondTool);

        firstAgent.destroy();
        assertThat(Runner.resourceMgr().getTool(toolId))
                .as("surviving instance keeps resolving the shared tool").isNotNull();

        String firstToken = fieldValue(firstAgent, DeepAgent.class, "ownerToken");
        String secondToken = fieldValue(secondAgent, DeepAgent.class, "ownerToken");
        assertThat(firstToken).as("same-card instances carry distinct owner tokens").isNotEqualTo(secondToken);
        assertThat(ownersOf(toolId)).as("destroy only released the destroying instance's claim")
                .containsExactly(secondToken);

        secondAgent.destroy();
        assertThat(Runner.resourceMgr().getTool(toolId)).as("last owner release removes the entry").isNull();
        assertThat(ownersOf(toolId)).as("no ownership residue").isEmpty();
    }

    /**
     * every conflicting registerHarnessTool call fails visibly
     * — the observable failure ratio is 100%, nothing is silently
     * swallowed, and the failed registrations leave no record in the
     * instance domain. Conflicts are injected naturally (a
     * different-definition entry pre-registered under the same id)
     * because Runner.resourceMgr exposes no mock seam.
     *
     * @throws Exception when agent construction or the registration attempt fails
     */
    @Test
    @DisplayName("Conflicting registerHarnessTool fails visibly on every call")
    void registrationFailuresAreVisibleOnEveryCall() throws Exception {
        String toolId = uniqueId("ut36-tool");
        assertThat(Runner.resourceMgr()
                .addTool(tool(toolCard(toolId, "ut36", "global holder definition")), "ut36-holder").isOk()).isTrue();
        trackedToolIds.add(toolId);

        DeepAgent agent = new DeepAgent(
                AgentCard.builder().id(uniqueId("ut36-agent")).name("ut36-agent").description("ut36").build(),
                DeepAgentConfig.builder().build(),
                Workspace.builder().rootPath(Files.createTempDirectory("dfx005-ut36").toString()).build());

        int visibleFailures = 0;
        for (int i = 0; i < 8; i++) {
            try {
                agent.registerHarnessTool(tool(toolCard(toolId, "ut36", "agent wants another definition")));
            } catch (BaseError expected) {
                visibleFailures++;
            }
        }
        assertThat(visibleFailures).as("every conflicting registration fails visibly").isEqualTo(8);
        assertThat(registeredToolsOf(agent)).as("failed registrations leave no instance-domain record").isEmpty();
    }

    /**
     * a failed registerHarnessTool throws at the registration
     * site (the direct-call Result check replaces the silent pre-check
     * skip), preserving the original RESOURCE_ADD_ERROR with the
     * conflicting resource id. HarnessPermissionIntegrationTest is the
     * declared compatibility regression and runs in the compatibility regression phase.
     *
     * @throws Exception when agent construction or the registration attempt fails
     */
    @Test
    @DisplayName("RegisterHarnessTool throws at the registration site with the conflict visible")
    void registerHarnessToolThrowsAtRegistrationSiteWithConflictVisible() throws Exception {
        String toolId = uniqueId("ut37-tool");
        assertThat(Runner.resourceMgr()
                .addTool(tool(toolCard(toolId, "ut37", "global holder definition")), "ut37-holder").isOk()).isTrue();
        trackedToolIds.add(toolId);

        DeepAgent agent = new DeepAgent(
                AgentCard.builder().id(uniqueId("ut37-agent")).name("ut37-agent").description("ut37").build(),
                DeepAgentConfig.builder().build(),
                Workspace.builder().rootPath(Files.createTempDirectory("dfx005-ut37").toString()).build());

        assertThatThrownBy(
                () -> agent.registerHarnessTool(tool(toolCard(toolId, "ut37", "different definition"))))
                .as("registration failure surfaces at the call site")
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("definition conflict");
    }

    /**
     * Factory-level landing point of the contract: HarnessFactory.registerToolInstances registers configured
     * tool instances directly under the instance owner token, so a
     * same-id tool with a different definition makes createDeepAgent
     * fail visibly instead of silently skipping the conflicting
     * instance. The sys_operation registered before the failure is
     * cleaned up by the tracked-ids teardown.
     *
     * @throws Exception when the factory create or its precondition fails
     */
    @Test
    @DisplayName("L2 4.4: factory create fails visibly on a conflicting configured tool")
    void factoryToolConflictFailsCreateVisibly() throws Exception {
        String toolId = uniqueId("utf-tool");
        String agentCardId = uniqueId("utf-agent");
        assertThat(Runner.resourceMgr()
                .addTool(tool(toolCard(toolId, "utf", "holder definition")), "utf-holder").isOk()).isTrue();
        trackedToolIds.add(toolId);

        AgentCard card = AgentCard.builder().id(agentCardId).name("utf-agent").description("utf").build();
        List<Object> tools = List.of(tool(toolCard(toolId, "utf", "conflicting definition")));
        DeepAgentConfig config = DeepAgentConfig.builder().tools(tools).build();
        trackedSysOpIds.add("utf-agent_" + agentCardId);

        assertThatThrownBy(() -> HarnessFactory.createDeepAgent(card, config,
                Workspace.builder().rootPath(Files.createTempDirectory("dfx005-utf").toString()).build()))
                .as("factory create fails visibly on a conflicting configured tool")
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("definition conflict");
    }

    private static Tool tool(ToolCard card) {
        return new NoopTool(card);
    }

    private static ToolCard toolCard(String id, String name, String description) {
        return ToolCard.builder().id(id).name(name).description(description).build();
    }

    private static String uniqueId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static Set<String> ownersOf(String resourceId) {
        Map<String, Set<String>> idToOwners = fieldValue(Runner.resourceMgr(), ResourceMgr.class, "idToOwners");
        Set<String> owners = idToOwners.get(resourceId);
        return owners != null ? owners : Set.of();
    }

    private static List<Object> registeredToolsOf(DeepAgent agent) {
        return fieldValue(agent, DeepAgent.class, "registeredTools");
    }

    @SuppressWarnings("unchecked")
    private static <T> T fieldValue(Object target, Class<?> declaringType, String name) {
        try {
            Field field = declaringType.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(
                    "ownership reflection path missing: " + declaringType.getSimpleName() + "." + name, e);
        }
    }
}
