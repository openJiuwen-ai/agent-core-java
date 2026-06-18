/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpTool;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.multi_agent.schema.TeamCard;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import com.openjiuwen.core.sys_operation.OperationMode;
import com.openjiuwen.core.sys_operation.SysOperationCard;
import com.openjiuwen.core.sys_operation.config.LocalWorkConfig;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code ResourceMgr} in
 * {@code openjiuwen/core/runner/resources_manager/resource_manager.py}.
 */
class ResourceMgrTest {

    @Test
    void addAgentRejectsNullCard() {
        ResourceMgr manager = new ResourceMgr();
        BaseError error = assertThrows(BaseError.class, () -> manager.addAgent(null, Object::new));

        assertEquals(StatusCode.RESOURCE_CARD_VALUE_INVALID, error.getStatus());
    }

    @Test
    void addAgentRejectsNonAgentCard() {
        BaseCard card = new BaseCard("agent-1", "agent", "");
        BaseError error = assertThrows(BaseError.class,
                () -> ResourceMgr.validateResourceCard(card, "agent", AgentCard.class));

        assertEquals(StatusCode.RESOURCE_CARD_VALUE_INVALID, error.getStatus());
    }

    @Test
    void addAgentRejectsEmptyId() {
        ResourceMgr manager = new ResourceMgr();
        AgentCard card = agentCard("");

        BaseError error = assertThrows(BaseError.class, () -> manager.addAgent(card, Object::new));

        assertEquals(StatusCode.RESOURCE_ID_VALUE_INVALID, error.getStatus());
    }

    @Test
    void addAgentRejectsNullId() {
        ResourceMgr manager = new ResourceMgr();
        AgentCard card = agentCard(null);

        BaseError error = assertThrows(BaseError.class, () -> manager.addAgent(card, Object::new));

        assertEquals(StatusCode.RESOURCE_ID_VALUE_INVALID, error.getStatus());
    }

    @Test
    void addAgentRejectsWhitespaceId() {
        ResourceMgr manager = new ResourceMgr();
        AgentCard card = agentCard("   ");

        BaseError error = assertThrows(BaseError.class, () -> manager.addAgent(card, Object::new));

        assertEquals(StatusCode.RESOURCE_ID_VALUE_INVALID, error.getStatus());
    }

    @Test
    void addAgentRejectsNullProvider() {
        ResourceMgr manager = new ResourceMgr();
        AgentCard card = agentCard("agent-1");

        BaseError error = assertThrows(BaseError.class, () -> manager.addAgent(card, (java.util.function.Supplier<?>) null));

        assertEquals(StatusCode.RESOURCE_PROVIDER_INVALID, error.getStatus());
    }

    @Test
    void addAgentStoresProviderAndCardTag() {
        ResourceMgr manager = new ResourceMgr();
        Object agent = new Object();
        AgentCard card = agentCard("agent-1");

        Result<?, ?> result = manager.addAgent(card, () -> agent, List.of("agent-tag"),
                "http://127.0.0.1:8000/a2a/jsonrpc");

        assertTrue(result.isOk());
        assertSame(card, result.msg());
        assertSame(agent, manager.getAgent("agent-1").toCompletableFuture().join());
        assertEquals(List.of(card), manager.getResourceByTag("agent-tag"));
    }

    @Test
    void addAgentsRejectsNullList() {
        ResourceMgr manager = new ResourceMgr();

        BaseError error = assertThrows(BaseError.class, () -> manager.addAgents(null, null));

        assertEquals(StatusCode.RESOURCE_PROVIDER_INVALID, error.getStatus());
    }

    @Test
    void addAgentsRejectsNullEntry() {
        ResourceMgr manager = new ResourceMgr();
        List<ResourceMgr.AgentEntry> entries = new ArrayList<>();
        entries.add(null);

        BaseError error = assertThrows(BaseError.class, () -> manager.addAgents(entries, null));

        assertEquals(StatusCode.RESOURCE_PROVIDER_INVALID, error.getStatus());
    }

    @Test
    void addAgentsRejectsNullProviderItem() {
        ResourceMgr manager = new ResourceMgr();
        List<ResourceMgr.AgentEntry> entries = List.of(new ResourceMgr.AgentEntry(agentCard("agent-1"), null));

        BaseError error = assertThrows(BaseError.class, () -> manager.addAgents(entries, null));

        assertEquals(StatusCode.RESOURCE_PROVIDER_INVALID, error.getStatus());
    }

    @Test
    void addAgentsRegistersAllProviders() {
        ResourceMgr manager = new ResourceMgr();
        Object first = new Object();
        Object second = new Object();

        List<Result<?, ?>> results = manager.addAgents(List.of(
                new ResourceMgr.AgentEntry(agentCard("agent-1"), () -> first),
                new ResourceMgr.AgentEntry(agentCard("agent-2"), () -> second)), List.of("agent-tag"));

        assertTrue(results.stream().allMatch(Result::isOk));
        List<Object> agents = manager.getAgentsByTag(List.of("agent-tag"), TagMatchStrategy.ALL, null)
                .toCompletableFuture()
                .join();
        assertTrue(agents.contains(first));
        assertTrue(agents.contains(second));
    }

    @Test
    void addToolRejectsNullTool() {
        BaseError error = assertThrows(BaseError.class, () -> ResourceMgr.validateTool(null));

        assertEquals(StatusCode.RESOURCE_VALUE_INVALID, error.getStatus());
    }

    @Test
    void addToolsRejectsEmptyList() {
        ResourceMgr manager = new ResourceMgr();

        BaseError error = assertThrows(BaseError.class, () -> manager.addTools(List.of(), null, false));

        assertEquals(StatusCode.RESOURCE_VALUE_INVALID, error.getStatus());
    }

    @Test
    void addToolsRejectsNullItem() {
        ResourceMgr manager = new ResourceMgr();
        List<Tool> tools = new ArrayList<>();
        tools.add(null);

        BaseError error = assertThrows(BaseError.class, () -> manager.addTools(tools, null, false));

        assertEquals(StatusCode.RESOURCE_VALUE_INVALID, error.getStatus());
    }

    @Test
    void addGetAndRemoveToolKeepsCardAndTagLedgersInSync() {
        ResourceMgr manager = new ResourceMgr();
        EchoTool tool = new EchoTool("tool-1", "echo");

        Result<?, ?> addResult = manager.addTool(tool, List.of("runner"), false);

        assertTrue(addResult.isOk());
        assertSame(tool.getCard(), addResult.msg());
        assertSame(tool, manager.getTool("tool-1"));
        assertEquals(List.of(tool.getCard()), manager.getResourceByTag("runner"));
        assertTrue(manager.listTags().contains("runner"));

        Result<?, ?> removeResult = manager.removeTool("tool-1");

        assertTrue(removeResult.isOk());
        assertEquals("tool-1", removeResult.msg());
        assertNull(manager.getTool("tool-1"));
        assertFalse(manager.tagManagerForTest().hasResource("tool-1"));
    }

    @Test
    void getToolByTagOnlyReturnsTaggedTools() {
        ResourceMgr manager = new ResourceMgr();
        EchoTool first = new EchoTool("tool-a", "search");
        EchoTool second = new EchoTool("tool-b", "calculator");
        manager.addTool(first, List.of("agent-1"), false);
        manager.addTool(second, List.of("agent-2"), false);

        List<Tool> tools = manager.getToolsByTag(List.of("agent-1"), TagMatchStrategy.ALL, null);

        assertEquals(List.of(first), tools);
    }

    @Test
    void addToolWithoutTagGetsGlobal() {
        ResourceMgr manager = new ResourceMgr();
        EchoTool tool = new EchoTool("tool-global", "global");

        manager.addTool(tool);

        assertTrue(manager.resourceHasTag("tool-global", ResourceManagerBase.GLOBAL));
    }

    @Test
    void addToolWithTagDoesNotGetGlobal() {
        ResourceMgr manager = new ResourceMgr();
        EchoTool tool = new EchoTool("tool-scoped", "scoped");

        manager.addTool(tool, List.of("agent-1"), false);

        assertFalse(manager.resourceHasTag("tool-scoped", ResourceManagerBase.GLOBAL));
        assertTrue(manager.resourceHasTag("tool-scoped", "agent-1"));
    }

    @Test
    void twoAgentsToolsAreIsolatedByTag() {
        ResourceMgr manager = new ResourceMgr();
        EchoTool first = new EchoTool("tool-agent-1", "search");
        EchoTool second = new EchoTool("tool-agent-2", "search2");

        manager.addTool(first, List.of("agent-1"), false);
        manager.addTool(second, List.of("agent-2"), false);

        assertEquals(List.of(first), manager.getToolsByTag(List.of("agent-1"), TagMatchStrategy.ALL, null));
        assertEquals(List.of(second), manager.getToolsByTag(List.of("agent-2"), TagMatchStrategy.ALL, null));
    }

    @Test
    void getToolInfosWithTagOnlyReturnsMatchingCards() {
        ResourceMgr manager = new ResourceMgr();
        EchoTool first = new EchoTool("tool-info-1", "tool-one");
        EchoTool second = new EchoTool("tool-info-2", "tool-two");
        manager.addTool(first, List.of("agent-x"), false);
        manager.addTool(second, List.of("agent-y"), false);

        List<ToolInfo> infos = manager.getToolInfos(null, null, List.of("agent-x"), TagMatchStrategy.ALL);

        assertEquals(List.of("tool-one"), infos.stream().map(ToolInfo::getName).toList());
    }

    @Test
    void getToolInfosFiltersByPythonCardType() {
        ResourceMgr manager = new ResourceMgr();
        EchoTool localTool = new EchoTool("local-1", "local");
        McpTool mcpTool = new McpTool(new FakeMcpClient(), McpToolCard.builder()
                .id("mcp-1")
                .name("search")
                .description("Search")
                .serverId("srv-1")
                .serverName("demo")
                .inputParams(Map.of("type", "object"))
                .build());

        assertTrue(manager.addTool(localTool).isOk());
        assertTrue(manager.addTool(mcpTool).isOk());

        List<ToolInfo> functionInfos = manager.getToolInfos(null, List.of("function"),
                List.of(ResourceManagerBase.GLOBAL), TagMatchStrategy.ALL);
        List<ToolInfo> mcpInfos = manager.getToolInfos(null, List.of("mcp"),
                List.of(ResourceManagerBase.GLOBAL), TagMatchStrategy.ALL);

        assertEquals(List.of("local"), functionInfos.stream().map(ToolInfo::getName).toList());
        assertEquals(List.of("search"), mcpInfos.stream().map(ToolInfo::getName).toList());
    }

    @Test
    void addWorkflowWithTagCanBeFetchedBySameTag() {
        ResourceMgr manager = new ResourceMgr();
        WorkflowCard card = workflowCard("workflow-1");
        Workflow workflow = new Workflow(card);
        manager.addWorkflow(card, () -> workflow, List.of("agent-1"));

        List<Object> workflows = manager.getWorkflowsByTag(List.of("agent-1"), TagMatchStrategy.ALL, null)
                .toCompletableFuture()
                .join();

        assertEquals(1, workflows.size());
        assertNotNull(workflows.get(0));
    }

    @Test
    void getWorkflowByTagOnlyReturnsTaggedWorkflows() {
        ResourceMgr manager = new ResourceMgr();
        Workflow first = new Workflow(workflowCard("workflow-agent-1"));
        Workflow second = new Workflow(workflowCard("workflow-agent-2"));
        manager.addWorkflow(first.getCard(), () -> first, List.of("agent-1"));
        manager.addWorkflow(second.getCard(), () -> second, List.of("agent-2"));

        List<Object> workflows = manager.getWorkflowsByTag(List.of("agent-1"), TagMatchStrategy.ALL, null)
                .toCompletableFuture()
                .join();

        assertEquals(1, workflows.size());
        assertNotNull(workflows.get(0));
    }

    @Test
    void duplicateToolFailsUnlessRefreshDropsPreviousRegistration() {
        ResourceMgr manager = new ResourceMgr();
        EchoTool first = new EchoTool("tool-1", "first");
        EchoTool second = new EchoTool("tool-1", "second");

        assertTrue(manager.addTool(first, List.of("old"), false).isOk());

        Result<?, ?> duplicate = manager.addTool(second, List.of("new"), false);
        assertTrue(duplicate.isErr());
        assertSame(first, manager.getTool("tool-1"));
        assertTrue(manager.resourceHasTag("tool-1", "old"));

        Result<?, ?> refreshed = manager.addTool(second, List.of("new"), true);

        assertTrue(refreshed.isOk());
        assertSame(second, manager.getTool("tool-1"));
        assertFalse(manager.tagManagerForTest().hasResourceTag("tool-1", "old"));
        assertTrue(manager.resourceHasTag("tool-1", "new"));
    }

    @Test
    void refreshOnMissingToolBehavesAsPlainAdd() {
        ResourceMgr manager = new ResourceMgr();
        EchoTool tool = new EchoTool("fresh-tool", "fresh");

        Result<?, ?> result = manager.addTool(tool, null, true);

        assertTrue(result.isOk());
        assertSame(tool, manager.getTool("fresh-tool"));
    }

    @Test
    void refreshListInputReplacesOnlyExistingIds() {
        ResourceMgr manager = new ResourceMgr();
        EchoTool existing = new EchoTool("list-existing", "old");
        EchoTool replacement = new EchoTool("list-existing", "new");
        EchoTool fresh = new EchoTool("list-fresh", "fresh");
        manager.addTool(existing);

        List<Result<?, ?>> results = manager.addTools(List.of(replacement, fresh), null, true);

        assertTrue(results.stream().allMatch(Result::isOk));
        assertSame(replacement, manager.getTool("list-existing"));
        assertSame(fresh, manager.getTool("list-fresh"));
    }

    @Test
    void getSingleSysOperationToolCard() {
        ResourceMgr manager = resourceMgrWithSysOperation();

        Object card = manager.getSysOpToolCards("test-sys-op", List.of("fs"), List.of("read_file"));

        assertTrue(card instanceof ToolCard);
        assertEquals("read_file", ((ToolCard) card).getName());
    }

    @Test
    void getSingleSysOperationToolCardReturnsNullWhenMissing() {
        ResourceMgr manager = resourceMgrWithSysOperation();

        Object card = manager.getSysOpToolCards("test-sys-op", List.of("fs"), List.of("missing_tool"));

        assertNull(card);
    }

    @Test
    void getMultipleSysOperationToolCardsFromSameOperation() {
        ResourceMgr manager = resourceMgrWithSysOperation();

        Object cards = manager.getSysOpToolCards("test-sys-op", List.of("fs"), List.of("read_file", "write_file"));

        assertTrue(cards instanceof List<?>);
        List<?> values = (List<?>) cards;
        assertEquals(2, values.size());
        assertTrue(values.stream().map(ToolCard.class::cast).map(ToolCard::getName).toList().contains("read_file"));
        assertTrue(values.stream().map(ToolCard.class::cast).map(ToolCard::getName).toList().contains("write_file"));
    }

    @Test
    void getAllSysOperationToolCardsFromSingleOperation() {
        ResourceMgr manager = resourceMgrWithSysOperation();

        Object cards = manager.getSysOpToolCards("test-sys-op", List.of("fs"), null);

        assertTrue(cards instanceof List<?>);
        assertFalse(((List<?>) cards).isEmpty());
    }

    @Test
    void getAllSysOperationToolCardsFromMultipleOperations() {
        ResourceMgr manager = resourceMgrWithSysOperation();

        Object cards = manager.getSysOpToolCards("test-sys-op", List.of("fs", "shell"), null);

        assertTrue(cards instanceof List<?>);
        assertFalse(((List<?>) cards).isEmpty());
    }

    @Test
    void getAllSysOperationToolCardsFromAllOperations() {
        ResourceMgr manager = resourceMgrWithSysOperation();

        Object cards = manager.getSysOpToolCards("test-sys-op", null, null);

        assertTrue(cards instanceof List<?>);
        assertFalse(((List<?>) cards).isEmpty());
    }

    @Test
    void getSysOperationToolCardsReturnsNullForMissingSysOperation() {
        ResourceMgr manager = new ResourceMgr();

        assertNull(manager.getSysOpToolCards("missing-sys-op", null, null));
    }

    @Test
    void operationNameListRejectsExplicitToolName() {
        ResourceMgr manager = resourceMgrWithSysOperation();

        BaseError error = assertThrows(BaseError.class,
                () -> manager.getSysOpToolCards("test-sys-op", List.of("fs", "shell"), List.of("read_file")));

        assertEquals(StatusCode.RESOURCE_VALUE_INVALID, error.getStatus());
        assertTrue(error.getMessage().contains("tool_name cannot be specified"));
    }

    @Test
    void removeTagRemovesAssociatedResourcesFromManagers() {
        ResourceMgr manager = new ResourceMgr();
        EchoTool tagged = new EchoTool("tool-1", "tagged");
        EchoTool untagged = new EchoTool("tool-2", "untagged");

        manager.addTool(tagged, List.of("remove-me"), false);
        manager.addTool(untagged, List.of("keep-me"), false);

        List<Result<?, ?>> results = manager.removeTag(List.of("remove-me"), false);

        assertEquals(1, results.size());
        assertTrue(results.get(0).isOk());
        assertNull(manager.getTool("tool-1"));
        assertSame(untagged, manager.getTool("tool-2"));
    }

    @Test
    void validationErrorsUsePythonResourceStatusCodes() {
        BaseError duplicateTag = assertThrows(BaseError.class,
                () -> ResourceMgr.validateTags(List.of("tag", "tag")));
        assertEquals(StatusCode.RESOURCE_TAG_VALUE_INVALID, duplicateTag.getStatus());

        BaseError mixedGlobal = assertThrows(BaseError.class,
                () -> ResourceMgr.validateTags(List.of(ResourceManagerBase.GLOBAL, "tag")));
        assertEquals(StatusCode.RESOURCE_TAG_VALUE_INVALID, mixedGlobal.getStatus());

        McpServerConfig invalidConfig = McpServerConfig.builder()
                .serverId("  ")
                .serverName("demo")
                .serverPath("http://localhost/mcp")
                .clientType("fake")
                .build();
        BaseError serverError = assertThrows(BaseError.class,
                () -> ResourceMgr.validateServerConfig(invalidConfig));
        assertEquals(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID, serverError.getStatus());
    }

    @Test
    void addMcpServerValidationRejectsNonPositiveExpiryBeforeClientLookup() {
        ResourceMgr manager = new ResourceMgr();
        McpServerConfig config = McpServerConfig.builder()
                .serverId("srv-1")
                .serverName("demo")
                .serverPath("http://localhost/mcp")
                .clientType("fake")
                .build();

        BaseError error = assertThrows(BaseError.class,
                () -> manager.addMcpServer(config, null, 0.0D));

        assertEquals(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID, error.getStatus());
    }

    @Test
    void removeAgentTeamReturnsOkWithRemovedCard() {
        ResourceMgr manager = new ResourceMgr();
        TeamCard card = new TeamCard("team-1", "team", "test team");

        assertTrue(manager.addAgentTeam(card, Object::new).toCompletableFuture().join().isOk());
        Result<?, ?> removed = manager.removeAgentTeam("team-1");

        assertTrue(removed.isOk());
        assertSame(card, removed.msg());
        assertNull(manager.getAgentTeam("team-1").toCompletableFuture().join());
    }

    @Test
    void getAgentTeamsByTagOnlyReturnsTaggedTeams() {
        ResourceMgr manager = new ResourceMgr();
        Object first = new Object();
        Object second = new Object();
        manager.addAgentTeam(new TeamCard("team-1", "team-1", ""), () -> first, List.of("tag-1"));
        manager.addAgentTeam(new TeamCard("team-2", "team-2", ""), () -> second, List.of("tag-2"));

        List<Object> teams = manager.getAgentTeamsByTag(List.of("tag-1"), TagMatchStrategy.ALL)
                .toCompletableFuture()
                .join();

        assertEquals(List.of(first), teams);
    }

    private static AgentCard agentCard(String id) {
        AgentCard card = new AgentCard();
        card.setId(id);
        card.setName("Test Agent");
        return card;
    }

    private static WorkflowCard workflowCard(String id) {
        WorkflowCard card = new WorkflowCard();
        card.setId(id);
        card.setName(id);
        return card;
    }

    private static ResourceMgr resourceMgrWithSysOperation() {
        ResourceMgr manager = new ResourceMgr();
        SysOperationCard card = new SysOperationCard("test-sys-op", OperationMode.LOCAL, new LocalWorkConfig());
        assertTrue(manager.addSysOperation(card).isOk());
        return manager;
    }

    /**
     * Mirrors a test-local concrete {@code Tool} provider used by
     * {@code openjiuwen/core/runner/resources_manager/resource_manager.py}.
     */
    private static final class EchoTool extends Tool {
        private EchoTool(String id, String name) {
            super(ToolCard.builder().id(id).name(name).description(name).inputParams(Map.of()).build());
        }
    }

    /**
     * Mirrors Python's dynamic MCP client boundary in
     * {@code openjiuwen/core/runner/resources_manager/resource_manager.py}.
     */
    private static final class FakeMcpClient {
    }
}
