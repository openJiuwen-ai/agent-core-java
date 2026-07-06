/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.multi_agent.schema.TeamCard;
import com.openjiuwen.core.runner.drunner.remote_client.RemoteAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sys_operation.OperationRegistry;
import com.openjiuwen.core.sys_operation.SysOperation;
import com.openjiuwen.core.sys_operation.SysOperationCard;
import com.openjiuwen.core.sys_operation.SysOperationToolAdapter;
import com.openjiuwen.core.workflow.WorkflowCard;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Aggregate resource manager facade.
 *
 * <p>Mirrors Python's {@code ResourceMgr} in
 * {@code openjiuwen/core/runner/resources_manager/resource_manager.py}.</p>
 */
public class ResourceMgr {

    private AgentTeamManager agentTeamManager;
    private AgentManager agentManager;
    private WorkflowManager workflowManager;
    private ResourceRegistry resourceRegistry;
    private ToolMgr toolMgr;
    private ToolManager toolManager;
    private ModelManager modelManager;
    private PromptManager promptManager;
    private SysOperationManager sysOperationManager;
    private TagMgr tagMgr;
    private TagManager tagManager;
    private Map<String, BaseCard> idToCard;

    public ResourceMgr() {
        resetManagers();
    }

    public CompletionStage<Result<?, ?>> addAgentTeam(TeamCard card, Supplier<?> agentTeam) {
        return addAgentTeam(card, agentTeam, null);
    }

    public CompletionStage<Result<?, ?>> addAgentTeam(TeamCard card, Supplier<?> agentTeam,
                                                      Collection<String> tag) {
        validateResourceCard(card, "team", TeamCard.class);
        validateResourceId(card.getId(), "team");
        validateProvider(agentTeam, "team");
        validateOptionalTags(tag);
        return CompletableFuture.completedFuture(innerAddResource(
                card.getId(), ResourceKind.TEAM, agentTeam, card, tag, null));
    }

    public Result<?, ?> removeAgentTeam(String teamId) {
        return singleResult(innerRemoveResources(List.of(teamId), ResourceKind.TEAM, null,
                TagMatchStrategy.ALL, false));
    }

    public CompletionStage<Object> getAgentTeam(String teamId) {
        validateResourceId(teamId, "team");
        if (!tagManager.hasResource(teamId)) {
            return CompletableFuture.completedFuture(null);
        }
        return dispatchGet(ResourceKind.TEAM, teamId, null);
    }

    public CompletionStage<List<Object>> getAgentTeamsByTag(Collection<String> tag,
                                                            TagMatchStrategy tagMatchStrategy) {
        return innerGetProviderResources(null, ResourceKind.TEAM, tag, tagMatchStrategy, null);
    }

    public Result<?, ?> addAgent(AgentCard card, Supplier<?> agent) {
        return addAgent(card, agent, null, null);
    }

    public com.openjiuwen.core.runner.base.Result<AgentCard> addAgent(AgentCard card, Supplier<?> agent, Object tag) {
        return baseResult(addAgent(card, agent, stringCollection(tag), null));
    }

    public Result<?, ?> addAgent(AgentCard card, Supplier<?> agent, Collection<String> tag, String interfaceUrl) {
        validateResourceCard(card, "agent", AgentCard.class);
        validateResourceId(card.getId(), "agent");
        validateProvider(agent, "agent");
        validateOptionalTags(tag);
        return innerAddResource(card.getId(), ResourceKind.AGENT, agent, card, tag, interfaceUrl);
    }

    public Result<?, ?> addAgent(AgentCard card, RemoteAgent agent) {
        return addAgent(card, agent, null, null);
    }

    public Result<?, ?> addAgent(AgentCard card, RemoteAgent agent, Collection<String> tag, String interfaceUrl) {
        validateResourceCard(card, "agent", AgentCard.class);
        validateResourceId(card.getId(), "agent");
        if (agent == null) {
            throw buildError(StatusCode.RESOURCE_PROVIDER_INVALID, "resource_type", "agent",
                    "reason", "provider cannot be None, must be a callable function");
        }
        validateOptionalTags(tag);
        return innerAddResource(card.getId(), ResourceKind.AGENT, agent, card, tag, interfaceUrl);
    }

    public List<Result<?, ?>> addAgents(List<AgentEntry> agents, Collection<String> tag) {
        validateProviderEntries(agents, "agent", AgentCard.class);
        validateOptionalTags(tag);
        List<Result<?, ?>> results = new ArrayList<>();
        for (AgentEntry entry : agents) {
            results.add(innerAddResource(entry.card().getId(), ResourceKind.AGENT,
                    entry.provider(), entry.card(), tag, null));
        }
        return results;
    }

    public Result<?, ?> removeAgent(String agentId) {
        return singleResult(innerRemoveResources(List.of(agentId), ResourceKind.AGENT, null,
                TagMatchStrategy.ALL, false));
    }

    public Object removeAgent(Object agentId, Object tag,
                              com.openjiuwen.core.runner.base.TagMatchStrategy tagMatchStrategy,
                              boolean skipIfTagNotExists) {
        if (isLegacyScalarEmptyId(agentId) && skipIfTagNotExists) {
            return noOpRemovalResults();
        }
        Collection<String> ids = stringCollection(agentId);
        if (ids == null) {
            return removeAgentsByTag(stringCollection(tag), strategy(tagMatchStrategy), skipIfTagNotExists);
        }
        return innerRemoveResources(new ArrayList<>(ids), ResourceKind.AGENT, null, strategy(tagMatchStrategy),
                skipIfTagNotExists);
    }

    public List<Result<?, ?>> removeAgentsByTag(Collection<String> tag, TagMatchStrategy tagMatchStrategy,
                                                boolean skipIfTagNotExists) {
        return innerRemoveResources(null, ResourceKind.AGENT, tag, tagMatchStrategy, skipIfTagNotExists);
    }

    public CompletionStage<Object> getAgent(String agentId) {
        validateResourceId(agentId, "agent");
        if (!tagManager.hasResource(agentId)) {
            return CompletableFuture.completedFuture(null);
        }
        return dispatchGet(ResourceKind.AGENT, agentId, null);
    }

    public Object getAgent(String agentId, Object tag,
                           com.openjiuwen.core.runner.base.TagMatchStrategy tagMatchStrategy) {
        if (agentId != null) {
            return getAgent(agentId).toCompletableFuture().join();
        }
        return getAgentsByTag(stringCollection(tag), strategy(tagMatchStrategy), null).toCompletableFuture().join();
    }

    public CompletionStage<List<Object>> getAgentsByTag(Collection<String> tag, TagMatchStrategy tagMatchStrategy,
                                                        Object session) {
        return innerGetProviderResources(null, ResourceKind.AGENT, tag, tagMatchStrategy, session);
    }

    public Result<?, ?> addWorkflow(WorkflowCard card, Supplier<?> workflow) {
        return addWorkflowResult(card, workflow, null);
    }

    public com.openjiuwen.core.runner.base.Result<WorkflowCard> addWorkflow(WorkflowCard card,
                                                                            Supplier<?> workflow,
                                                                            Object tag) {
        return baseResult(addWorkflowResult(card, workflow, stringCollection(tag)));
    }

    public com.openjiuwen.core.runner.base.Result<WorkflowCard> addWorkflow(WorkflowCard card,
                                                                            Supplier<?> workflow,
                                                                            Collection<String> tag) {
        return baseResult(addWorkflowResult(card, workflow, tag));
    }

    private Result<?, ?> addWorkflowResult(WorkflowCard card, Supplier<?> workflow, Collection<String> tag) {
        validateResourceCard(card, "workflow", WorkflowCard.class);
        validateResourceId(card.getId(), "workflow");
        validateProvider(workflow, "workflow");
        validateOptionalTags(tag);
        return innerAddResource(card.getId(), ResourceKind.WORKFLOW, workflow, card, tag, null);
    }

    public List<com.openjiuwen.core.runner.base.Result<WorkflowCard>> addWorkflows(List<WorkflowEntry> workflows,
                                                                                   Object tag) {
        return baseResultList(addWorkflowsResult(workflows, stringCollection(tag)));
    }

    public List<com.openjiuwen.core.runner.base.Result<WorkflowCard>> addWorkflows(List<WorkflowEntry> workflows,
                                                                                   Collection<String> tag) {
        return baseResultList(addWorkflowsResult(workflows, tag));
    }

    private List<Result<?, ?>> addWorkflowsResult(List<WorkflowEntry> workflows, Collection<String> tag) {
        validateProviderEntries(workflows, "workflow", WorkflowCard.class);
        validateOptionalTags(tag);
        List<Result<?, ?>> results = new ArrayList<>();
        for (WorkflowEntry entry : workflows) {
            results.add(innerAddResource(entry.card().getId(), ResourceKind.WORKFLOW,
                    entry.provider(), entry.card(), tag, null));
        }
        return results;
    }

    public Result<?, ?> removeWorkflow(String workflowId) {
        return singleResult(innerRemoveResources(List.of(workflowId), ResourceKind.WORKFLOW, null,
                TagMatchStrategy.ALL, false));
    }

    public Object removeWorkflow(Object workflowId, Object tag,
                                 com.openjiuwen.core.runner.base.TagMatchStrategy tagMatchStrategy,
                                 boolean skipIfTagNotExists) {
        if (isLegacyScalarEmptyId(workflowId) && skipIfTagNotExists) {
            return noOpRemovalResults();
        }
        Collection<String> ids = stringCollection(workflowId);
        if (ids == null) {
            return innerRemoveResources(null, ResourceKind.WORKFLOW, stringCollection(tag),
                    strategy(tagMatchStrategy), skipIfTagNotExists);
        }
        return innerRemoveResources(new ArrayList<>(ids), ResourceKind.WORKFLOW, null, strategy(tagMatchStrategy),
                skipIfTagNotExists);
    }

    public CompletionStage<Object> getWorkflow(String workflowId, Object session) {
        validateResourceId(workflowId, "workflow");
        if (!tagManager.hasResource(workflowId)) {
            return CompletableFuture.completedFuture(null);
        }
        return dispatchGet(ResourceKind.WORKFLOW, workflowId, session);
    }

    public Object getWorkflow(String workflowId, Object tag,
                              com.openjiuwen.core.runner.base.TagMatchStrategy tagMatchStrategy) {
        if (workflowId != null) {
            return getWorkflow(workflowId, null).toCompletableFuture().join();
        }
        return getWorkflowsByTag(stringCollection(tag), strategy(tagMatchStrategy), null).toCompletableFuture().join();
    }

    public CompletionStage<List<Object>> getWorkflowsByTag(Collection<String> tag, TagMatchStrategy tagMatchStrategy,
                                                           Object session) {
        return innerGetProviderResources(null, ResourceKind.WORKFLOW, tag, tagMatchStrategy, session);
    }

    public Result<?, ?> addTool(Tool tool) {
        return addTool(tool, null, false);
    }

    public com.openjiuwen.core.runner.base.Result<ToolCard> addTool(Tool tool, Object tag) {
        return baseResult(addTool(tool, stringCollection(tag), false));
    }

    public Result<?, ?> addTool(Tool tool, Collection<String> tag, boolean refresh) {
        validateTool(tool);
        validateOptionalTags(tag);
        refreshExistingToolIfNeeded(tool, refresh);
        return innerAddResource(tool.getCard().getId(), ResourceKind.TOOL, tool, tool.getCard(), tag, null);
    }

    public List<com.openjiuwen.core.runner.base.Result<ToolCard>> addTools(List<? extends Tool> tools, Object tag) {
        return baseResultList(addTools(tools, stringCollection(tag), false));
    }

    public List<Result<?, ?>> addTools(List<? extends Tool> tools, Collection<String> tag, boolean refresh) {
        validateToolList(tools);
        validateOptionalTags(tag);
        List<Result<?, ?>> results = new ArrayList<>();
        for (Tool tool : tools) {
            refreshExistingToolIfNeeded(tool, refresh);
            results.add(innerAddResource(tool.getCard().getId(), ResourceKind.TOOL, tool, tool.getCard(), tag, null));
        }
        return results;
    }

    public Tool getTool(String toolId) {
        Object result = innerGetResources(List.of(toolId), ResourceKind.TOOL, null, TagMatchStrategy.ALL, null);
        return result instanceof Tool tool ? tool : null;
    }

    public List<Tool> getToolsByTag(Collection<String> tag, TagMatchStrategy tagMatchStrategy, Object session) {
        Object result = innerGetResources(null, ResourceKind.TOOL, tag, tagMatchStrategy, session);
        return typedList(result, Tool.class);
    }

    public Object getTool(String toolId, Object tag,
                          com.openjiuwen.core.runner.base.TagMatchStrategy tagMatchStrategy) {
        if (toolId != null) {
            return getTool(toolId);
        }
        return getToolsByTag(stringCollection(tag), strategy(tagMatchStrategy), null);
    }

    public Result<?, ?> removeTool(String toolId) {
        return singleResult(innerRemoveResources(List.of(toolId), ResourceKind.TOOL, null,
                TagMatchStrategy.ALL, false));
    }

    public List<Result<?, ?>> removeTools(Collection<String> toolIds) {
        return innerRemoveResources(new ArrayList<>(toolIds), ResourceKind.TOOL, null,
                TagMatchStrategy.ALL, false);
    }

    public List<Result<?, ?>> removeToolsByTag(Collection<String> tag, TagMatchStrategy tagMatchStrategy,
                                               boolean skipIfTagNotExists) {
        return innerRemoveResources(null, ResourceKind.TOOL, tag, tagMatchStrategy, skipIfTagNotExists);
    }

    public Object removeTool(Object toolId, Object tag,
                             com.openjiuwen.core.runner.base.TagMatchStrategy tagMatchStrategy,
                             boolean skipIfTagNotExists) {
        if (isLegacyScalarEmptyId(toolId) && skipIfTagNotExists) {
            return noOpRemovalResults();
        }
        Collection<String> ids = stringCollection(toolId);
        if (ids == null) {
            return removeToolsByTag(stringCollection(tag), strategy(tagMatchStrategy), skipIfTagNotExists);
        }
        return removeTools(ids);
    }

    public Result<?, ?> addModel(String modelId, Supplier<?> model) {
        return addModel(modelId, model, null);
    }

    public Result<?, ?> addModel(String modelId, Supplier<?> model, Collection<String> tag) {
        validateResourceId(modelId, "model");
        validateProvider(model, "model");
        validateOptionalTags(tag);
        return innerAddResource(modelId, ResourceKind.MODEL, model, null, tag, null);
    }

    public List<Result<?, ?>> addModels(List<ModelEntry> models, Collection<String> tag) {
        validateProviderEntries(models, "model", null);
        validateOptionalTags(tag);
        List<Result<?, ?>> results = new ArrayList<>();
        for (ModelEntry entry : models) {
            results.add(innerAddResource(entry.modelId(), ResourceKind.MODEL, entry.provider(), null, tag, null));
        }
        return results;
    }

    public Result<?, ?> removeModel(String modelId) {
        return singleResult(innerRemoveResources(List.of(modelId), ResourceKind.MODEL, null,
                TagMatchStrategy.ALL, false));
    }

    public CompletionStage<Object> getModel(String modelId, Object session) {
        validateResourceId(modelId, "model");
        if (!tagManager.hasResource(modelId)) {
            return CompletableFuture.completedFuture(null);
        }
        return dispatchGet(ResourceKind.MODEL, modelId, session);
    }

    public Result<?, ?> addPrompt(String promptId, PromptTemplate template) {
        return addPrompt(promptId, template, null);
    }

    public Result<?, ?> addPrompt(String promptId, PromptTemplate template, Collection<String> tag) {
        validateResourceId(promptId, "prompt");
        validateResource(template, "prompt", PromptTemplate.class);
        validateOptionalTags(tag);
        return innerAddResource(promptId, ResourceKind.PROMPT, template, null, tag, null);
    }

    public List<Result<?, ?>> addPrompts(List<PromptEntry> prompts, Collection<String> tag) {
        if (prompts == null || prompts.isEmpty()) {
            throw buildError(StatusCode.RESOURCE_VALUE_INVALID, "resource_type", "prompt",
                    "reason", "prompt list cannot be empty: expected a non-empty list of PromptTemplate");
        }
        validateOptionalTags(tag);
        List<Result<?, ?>> results = new ArrayList<>();
        for (PromptEntry entry : prompts) {
            validateResourceId(entry.promptId(), "prompt");
            validateResource(entry.template(), "prompt", PromptTemplate.class);
            results.add(innerAddResource(entry.promptId(), ResourceKind.PROMPT, entry.template(), null, tag, null));
        }
        return results;
    }

    public PromptTemplate getPrompt(String promptId) {
        Object result = innerGetResources(List.of(promptId), ResourceKind.PROMPT, null, TagMatchStrategy.ALL, null);
        return result instanceof PromptTemplate promptTemplate ? promptTemplate : null;
    }

    public Result<?, ?> removePrompt(String promptId) {
        return singleResult(innerRemoveResources(List.of(promptId), ResourceKind.PROMPT, null,
                TagMatchStrategy.ALL, false));
    }

    public Result<?, ?> addSysOperation(SysOperationCard card) {
        return addSysOperation(card, null);
    }

    public Result<?, ?> addSysOperation(SysOperationCard card, Collection<String> tag) {
        validateResourceCard(card, "sys_operation", SysOperationCard.class);
        validateOptionalTags(tag);
        SysOperation instance = new SysOperation(card);
        Result<?, ?> result = innerAddResource(card.getId(), ResourceKind.SYS_OPERATION, instance, card, tag, null);
        if (result.isOk()) {
            registerSysOperationTools(card, instance, tag);
        }
        return result;
    }

    public List<Result<?, ?>> addSysOperations(List<SysOperationCard> cards, Collection<String> tag) {
        if (cards == null || cards.isEmpty()) {
            return List.of();
        }
        List<Result<?, ?>> results = new ArrayList<>();
        for (SysOperationCard card : cards) {
            results.add(addSysOperation(card, tag));
        }
        return results;
    }

    public Result<?, ?> removeSysOperation(String sysOperationId) {
        List<String> ids = List.of(sysOperationId);
        List<Result<?, ?>> results = innerRemoveResources(ids, ResourceKind.SYS_OPERATION, null,
                TagMatchStrategy.ALL, false);
        removeSysOperationTools(ids, null, false);
        return singleResult(results);
    }

    public List<Result<?, ?>> removeSysOperationsByTag(Collection<String> tag, TagMatchStrategy tagMatchStrategy,
                                                       boolean skipIfTagNotExists) {
        ServerIdLookup lookup = findResourceIds(null, tag, tagMatchStrategy, skipIfTagNotExists);
        List<Result<?, ?>> results = innerRemoveResources(lookup.ids(), ResourceKind.SYS_OPERATION, tag,
                tagMatchStrategy, skipIfTagNotExists);
        removeSysOperationTools(lookup.ids(), tag, skipIfTagNotExists);
        return results;
    }

    public SysOperation getSysOperation(String sysOperationId) {
        Object result = innerGetResources(List.of(sysOperationId), ResourceKind.SYS_OPERATION, null,
                TagMatchStrategy.ALL, null);
        return result instanceof SysOperation sysOperation ? sysOperation : null;
    }

    public Object getSysOpToolCards(String sysOperationId, Collection<String> operationNames,
                                    Collection<String> toolNames) {
        if (operationNames != null && operationNames.size() > 1 && toolNames != null && !toolNames.isEmpty()) {
            throw buildError(StatusCode.RESOURCE_VALUE_INVALID, "resource_type", "sys_operation",
                    "reason", "tool_name cannot be specified when operation_name is a list");
        }
        SysOperation sysOperation = sysOperationManager.getSysOperation(sysOperationId);
        if (sysOperation == null) {
            return null;
        }
        Collection<String> effectiveOperations = operationNames == null || operationNames.isEmpty()
                ? OperationRegistry.getSupportedOperations(sysOperation.getMode())
                : operationNames;
        List<ToolCard> result = new ArrayList<>();
        for (String operationName : effectiveOperations) {
            if (toolNames == null || toolNames.isEmpty()) {
                for (String toolId : toolManager.getSysOperationToolIds(sysOperationId)) {
                    if (toolId.startsWith(sysOperationId + "." + operationName + ".")) {
                        BaseCard card = idToCard.get(toolId);
                        if (card instanceof ToolCard toolCard) {
                            result.add(toolCard);
                        }
                    }
                }
                continue;
            }
            for (String toolName : toolNames) {
                BaseCard card = idToCard.get(SysOperationCard.generateToolId(sysOperationId, operationName, toolName));
                if (card instanceof ToolCard toolCard) {
                    result.add(toolCard);
                }
            }
        }
        if (toolNames != null && toolNames.size() == 1) {
            return result.isEmpty() ? null : result.get(0);
        }
        return result;
    }

    public List<ToolInfo> getToolInfos(Collection<String> toolIds, Collection<String> toolTypes,
                                       Collection<String> tag, TagMatchStrategy tagMatchStrategy) {
        ServerIdLookup lookup = findResourceIds(toolIds, tag, tagMatchStrategy, true);
        if (lookup.ids().isEmpty()) {
            return List.of();
        }
        List<ToolInfo> results = new ArrayList<>();
        for (String resourceId : lookup.ids()) {
            BaseCard card = idToCard.get(resourceId);
            if (toolTypes != null && !toolTypes.isEmpty() && !toolTypes.contains(getCardType(card))) {
                continue;
            }
            if (card != null && card.toolInfo() instanceof ToolInfo toolInfo) {
                results.add(toolInfo);
            } else if (lookup.exactMatch()) {
                results.add(null);
            }
        }
        return results;
    }

    public List<ToolInfo> getToolInfos(Object toolIds, Object toolTypes, Object tag,
                                       com.openjiuwen.core.runner.base.TagMatchStrategy tagMatchStrategy) {
        return getToolInfos(stringCollection(toolIds), stringCollection(toolTypes), stringCollection(tag),
                strategy(tagMatchStrategy));
    }

    public CompletionStage<Result<?, ?>> addMcpServer(McpServerConfig serverConfig) {
        return addMcpServer(serverConfig, null, null);
    }

    public List<com.openjiuwen.core.runner.base.Result<String>> addMcpServer(McpServerConfig serverConfig,
                                                                             Object tag,
                                                                             Double expiryTime) {
        return baseResultList(List.of(addOneMcpServer(serverConfig, stringCollection(tag), expiryTime)));
    }

    public CompletionStage<Result<?, ?>> addMcpServer(McpServerConfig serverConfig,
                                                      Collection<String> tag,
                                                      Double expiryTime) {
        validateServerConfig(serverConfig);
        validateOptionalTags(tag);
        validateExpiry(expiryTime);
        return CompletableFuture.completedFuture(addOneMcpServer(serverConfig, tag, expiryTime));
    }

    public List<com.openjiuwen.core.runner.base.Result<String>> addMcpServer(List<McpServerConfig> serverConfigs,
                                                                             Object tag,
                                                                             Double expiryTime) {
        return baseResultList(addMcpServers(serverConfigs, stringCollection(tag), expiryTime)
                .toCompletableFuture().join());
    }

    public CompletionStage<List<Result<?, ?>>> addMcpServers(List<McpServerConfig> serverConfigs,
                                                             Collection<String> tag,
                                                             Double expiryTime) {
        validateServerConfigs(serverConfigs);
        validateOptionalTags(tag);
        validateExpiry(expiryTime);
        List<Result<?, ?>> results = new ArrayList<>();
        for (McpServerConfig config : serverConfigs) {
            results.add(addOneMcpServer(config, tag, expiryTime));
        }
        return CompletableFuture.completedFuture(results);
    }

    public List<Result<?, ?>> refreshMcpServer(String serverId) {
        if (serverId != null) {
            validateResourceId(serverId, "mcp server");
        }
        return List.of();
    }

    public List<com.openjiuwen.core.runner.base.Result<String>> removeMcpServer(Object serverIds,
                                                                                Object serverNames,
                                                                                Object tag,
                                                                                com.openjiuwen.core.runner.base.TagMatchStrategy tagMatchStrategy,
                                                                                boolean skipIfTagNotExists) {
        return baseResultList(removeMcpServer(stringCollection(serverIds), stringCollection(serverNames),
                stringCollection(tag), strategy(tagMatchStrategy), skipIfTagNotExists, false)
                .toCompletableFuture().join());
    }

    public CompletionStage<List<Result<?, ?>>> removeMcpServer(Collection<String> serverIds,
                                                               Collection<String> serverNames,
                                                               Collection<String> tag,
                                                               TagMatchStrategy tagMatchStrategy,
                                                               boolean skipIfTagNotExists,
                                                               boolean ignoreException) {
        ServerIdLookup lookup = getServerIds(serverIds, serverNames, tag, tagMatchStrategy,
                skipIfTagNotExists, StatusCode.RESOURCE_MCP_SERVER_REMOVE_ERROR);
        List<Result<?, ?>> results = new ArrayList<>();
        for (String mcpServerId : lookup.ids()) {
            try {
                tagManager.removeResource(mcpServerId);
                List<String> toolIds = toolMgr.removeToolServer(mcpServerId, true);
                if (!toolIds.isEmpty()) {
                    innerRemoveResources(toolIds, ResourceKind.TOOL, tag, TagMatchStrategy.ALL, skipIfTagNotExists);
                }
                results.add(new Ok<>(mcpServerId));
            } catch (Exception exception) {
                if (!ignoreException) {
                    throw exception;
                }
                results.add(new ErrorResult<>(exception));
            }
        }
        return CompletableFuture.completedFuture(results);
    }

    public List<Tool> getMcpTool(Object names,
                                 Object serverIds,
                                 Object serverNames,
                                 Object tag,
                                 com.openjiuwen.core.runner.base.TagMatchStrategy tagMatchStrategy,
                                 boolean skipIfTagNotExists) {
        return getMcpTool(stringCollection(names), stringCollection(serverIds), stringCollection(serverNames),
                stringCollection(tag), strategy(tagMatchStrategy), skipIfTagNotExists, false, null)
                .toCompletableFuture().join();
    }

    public CompletionStage<List<Tool>> getMcpTool(Collection<String> names,
                                                  Collection<String> serverIds,
                                                  Collection<String> serverNames,
                                                  Collection<String> tag,
                                                  TagMatchStrategy tagMatchStrategy,
                                                  boolean skipIfTagNotExists,
                                                  boolean ignoreException,
                                                  Object session) {
        ServerIdLookup lookup = getServerIds(serverIds, serverNames, tag, tagMatchStrategy,
                skipIfTagNotExists, StatusCode.RESOURCE_MCP_TOOL_GET_ERROR);
        List<Tool> results = new ArrayList<>();
        for (String mcpServerId : lookup.ids()) {
            try {
                toolMgr.refreshToolServer(mcpServerId, true, false);
            } catch (Exception exception) {
                if (!ignoreException) {
                    throw exception;
                }
            }
            if (names == null || names.isEmpty()) {
                List<Tool> tools = session == null ? toolMgr.getMcpTools(mcpServerId)
                        : toolManager.getMcpTools(mcpServerId, session);
                if (tools != null) {
                    results.addAll(tools);
                }
                continue;
            }
            for (String name : names) {
                Tool tool = session == null ? toolMgr.getMcpTool(name, mcpServerId)
                        : toolManager.getMcpTool(name, mcpServerId, session);
                if (lookup.exactMatch() || tool != null) {
                    results.add(tool);
                }
            }
        }
        return CompletableFuture.completedFuture(results);
    }

    public CompletionStage<List<ToolInfo>> getMcpToolInfos(Collection<String> names,
                                                           Collection<String> serverIds,
                                                           Collection<String> serverNames,
                                                           Collection<String> tag,
                                                           TagMatchStrategy tagMatchStrategy,
                                                           boolean skipIfTagNotExists,
                                                           boolean ignoreException) {
        ServerIdLookup lookup = getServerIds(serverIds, serverNames, tag, tagMatchStrategy,
                skipIfTagNotExists, StatusCode.RESOURCE_MCP_TOOL_GET_ERROR);
        List<ToolInfo> results = new ArrayList<>();
        for (String mcpServerId : lookup.ids()) {
            try {
                toolMgr.refreshToolServer(mcpServerId, true, false);
            } catch (Exception exception) {
                if (!ignoreException) {
                    throw exception;
                }
            }
            List<String> toolIds = new ArrayList<>();
            if (names == null || names.isEmpty()) {
                toolIds.addAll(toolMgr.getMcpToolIds(mcpServerId));
            } else {
                for (String name : names) {
                    Object toolId = toolMgr.getMcpToolId(mcpServerId, name);
                    if (toolId != null) {
                        toolIds.add(String.valueOf(toolId));
                    }
                }
            }
            for (String toolId : toolIds) {
                BaseCard card = idToCard.get(toolId);
                if (card != null && card.toolInfo() instanceof ToolInfo toolInfo) {
                    results.add(toolInfo);
                } else if (lookup.exactMatch()) {
                    results.add(null);
                }
            }
        }
        return CompletableFuture.completedFuture(results);
    }

    public List<ToolInfo> getMcpToolInfos(Object names,
                                          Object serverIds,
                                          Object serverNames,
                                          Object tag,
                                          com.openjiuwen.core.runner.base.TagMatchStrategy tagMatchStrategy,
                                          boolean skipIfTagNotExists,
                                          boolean ignoreException) {
        return getMcpToolInfos(stringCollection(names), stringCollection(serverIds), stringCollection(serverNames),
                stringCollection(tag), strategy(tagMatchStrategy), skipIfTagNotExists, ignoreException)
                .toCompletableFuture().join();
    }

    public McpServerConfig getMcpServerConfig(String serverId) {
        validateResourceId(serverId, "mcp server");
        return toolMgr.getMcpServerConfig(serverId);
    }

    public List<String> getMcpToolIds(String serverId) {
        validateResourceId(serverId, "mcp server");
        return toolMgr.getMcpToolIds(serverId);
    }

    public Object getMcpClient(String serverId) {
        validateResourceId(serverId, "mcp server");
        return toolMgr.getMcpClient(serverId);
    }

    public CompletionStage<Object> listMcpResources(String serverId) {
        validateResourceId(serverId, "mcp server");
        Object client = toolMgr.getMcpClient(serverId);
        if (client == null) {
            throw buildError(StatusCode.RESOURCE_MCP_TOOL_GET_ERROR,
                    "server_id", serverId, "reason", "server not found");
        }
        return invokeAsync(client, "listResources",
                ToolManager.operationTimeout(toolMgr.getMcpServerConfig(serverId)));
    }

    public CompletionStage<Object> readMcpResource(String serverId, String uri) {
        validateResourceId(serverId, "mcp server");
        Object client = toolMgr.getMcpClient(serverId);
        if (client == null) {
            throw buildError(StatusCode.RESOURCE_MCP_TOOL_GET_ERROR,
                    "server_id", serverId, "reason", "server not found");
        }
        return invokeAsync(client, "readResource", uri,
                ToolManager.operationTimeout(toolMgr.getMcpServerConfig(serverId)));
    }

    public List<BaseCard> getResourceByTag(String tag) {
        validateTag(tag);
        List<String> resourceIds = tagManager.getTagResources(tag);
        if (resourceIds == null || resourceIds.isEmpty()) {
            return List.of();
        }
        List<BaseCard> resources = new ArrayList<>();
        for (String resourceId : resourceIds) {
            BaseCard card = idToCard.get(resourceId);
            if (card != null) {
                resources.add(card);
            }
        }
        return resources;
    }

    public List<String> listTags() {
        return tagManager.listTags();
    }

    public boolean hasTag(String tag) {
        validateTag(tag);
        return tagManager.hasTag(tag);
    }

    public List<Result<?, ?>> removeTag(String tag, boolean skipIfTagNotExists) {
        return removeTag(List.of(tag), skipIfTagNotExists);
    }

    public List<Result<?, ?>> removeTag(Collection<String> tags, boolean skipIfTagNotExists) {
        validateTags(tags);
        List<Result<?, ?>> results = new ArrayList<>();
        for (String tag : tags) {
            List<String> removedResourceIds = tagManager.removeTag(tag, skipIfTagNotExists);
            for (String resourceId : removedResourceIds) {
                removeById(resourceId);
            }
            results.add(new Ok<>(tag));
        }
        return results;
    }

    public Result<?, ?> updateResourceTag(String resourceId, String tag) {
        return updateResourceTag(resourceId, List.of(tag));
    }

    public Result<?, ?> updateResourceTag(String resourceId, Collection<String> tag) {
        validateResourceId(resourceId, "resource");
        validateTags(tag);
        try {
            return new Ok<>(tagManager.updateResourceTags(resourceId, tag, TagUpdateStrategy.REPLACE));
        } catch (Exception exception) {
            return new ErrorResult<>(exception);
        }
    }

    public Result<?, ?> addResourceTag(String resourceId, String tag) {
        return addResourceTag(resourceId, List.of(tag));
    }

    public Result<?, ?> addResourceTag(String resourceId, Collection<String> tag) {
        validateResourceId(resourceId, "resource");
        validateTags(tag);
        try {
            return new Ok<>(tagManager.tagResource(resourceId, tag));
        } catch (Exception exception) {
            return new ErrorResult<>(exception);
        }
    }

    public Result<?, ?> removeResourceTag(String resourceId, String tag, boolean skipIfTagNotExists) {
        return removeResourceTag(resourceId, List.of(tag), skipIfTagNotExists);
    }

    public Result<?, ?> removeResourceTag(String resourceId, Collection<String> tag, boolean skipIfTagNotExists) {
        validateResourceId(resourceId, "resource");
        validateTags(tag);
        try {
            return new Ok<>(tagManager.removeResourceTags(resourceId, tag, skipIfTagNotExists));
        } catch (Exception exception) {
            return new ErrorResult<>(exception);
        }
    }

    public List<String> getResourceTag(String resourceId) {
        List<String> tags = tagManager.getResourcesTags(resourceId);
        return tags.isEmpty() ? null : tags;
    }

    public boolean resourceHasTag(String resourceId, String tag) {
        validateTag(tag);
        validateResourceId(resourceId, "resource");
        return tagManager.hasResourceTag(resourceId, tag);
    }

    public CompletionStage<Void> release() {
        return toolManager.release().thenRun(this::resetManagers);
    }

    public TagManager tagManagerForTest() {
        return tagManager;
    }

    Map<String, BaseCard> idToCardForTest() {
        return new LinkedHashMap<>(idToCard);
    }

    private Result<?, ?> addOneMcpServer(McpServerConfig config, Collection<String> tag, Double expiryTime) {
        try {
            List<McpToolCard> cards = toolMgr.addToolServer(config, expiryTime);
            Collection<String> effectiveTags = effectiveTags(tag);
            for (McpToolCard card : cards) {
                idToCard.put(card.getId(), card);
                tagManager.tagResource(card.getId(), effectiveTags);
            }
            tagManager.tagResource(config.getServerId(), effectiveTags);
            return new Ok<>(config.getServerId());
        } catch (Exception exception) {
            return new ErrorResult<>(exception);
        }
    }

    private Result<?, ?> innerAddResource(String resourceId, ResourceKind resourceType, Object resource,
                                          BaseCard resourceCard, Collection<String> tag, String interfaceUrl) {
        try {
            if (tagManager.hasResource(resourceId)) {
                Object card = resourceCard != null ? resourceCard : resourceId;
                throw buildError(StatusCode.RESOURCE_ADD_ERROR,
                        "card", String.valueOf(card), "reason", "resource already exist");
            }
            dispatchAdd(resourceType, resourceId, resource, resourceCard, interfaceUrl);
            if (resourceCard != null) {
                idToCard.put(resourceId, resourceCard);
            }
            tagManager.tagResource(resourceId, effectiveTags(tag));
            return new Ok<>(resourceCard != null ? resourceCard : resourceId);
        } catch (Exception exception) {
            return new ErrorResult<>(exception);
        }
    }

    private List<Result<?, ?>> innerRemoveResources(List<String> resourceIds, ResourceKind resourceType,
                                                    Collection<String> tag,
                                                    TagMatchStrategy tagMatchStrategy,
                                                    boolean skipIfTagNotExists) {
        List<String> idsToRemove = new ArrayList<>();
        if (resourceIds != null) {
            validateResourceIds(resourceIds, resourceType.pythonName());
            idsToRemove.addAll(resourceIds);
        }

        boolean removeByTag = false;
        if (idsToRemove.isEmpty()) {
            validateTags(tag);
            idsToRemove.addAll(findResourcesByTags(tag, tagMatchStrategy, skipIfTagNotExists));
            removeByTag = true;
            if (idsToRemove.isEmpty()) {
                return List.of();
            }
        }

        List<Result<?, ?>> results = new ArrayList<>();
        for (String removeId : idsToRemove) {
            Exception error = null;
            try {
                tagManager.removeResource(removeId);
                dispatchRemove(resourceType, removeId);
            } catch (Exception exception) {
                if (!removeByTag) {
                    error = exception;
                }
            }
            BaseCard removedCard = idToCard.remove(removeId);
            if (error != null) {
                results.add(new ErrorResult<>(error));
            } else if (resourceType.returnsId()) {
                results.add(new Ok<>(removeId));
            } else if (removedCard != null || !removeByTag) {
                results.add(new Ok<>(removedCard));
            }
        }
        return results;
    }

    private Object innerGetResources(List<String> resourceIds, ResourceKind resourceType, Collection<String> tag,
                                     TagMatchStrategy tagMatchStrategy, Object session) {
        ServerIdLookup lookup = findResourceIds(resourceIds, tag, tagMatchStrategy, true);
        List<Object> results = new ArrayList<>();
        for (String getId : lookup.ids()) {
            Object resource = null;
            Exception error = null;
            try {
                if (tagManager.hasResource(getId)) {
                    resource = dispatchGet(resourceType, getId, session).toCompletableFuture().join();
                }
            } catch (Exception exception) {
                if (lookup.exactMatch()) {
                    throw exception;
                }
                error = exception;
            }
            if (error != null) {
                results.add(new ErrorResult<>(error));
            } else if (resource != null || lookup.exactMatch()) {
                results.add(resource);
            }
        }
        return lookup.exactMatch() && results.size() == 1 ? results.get(0) : results;
    }

    private CompletionStage<List<Object>> innerGetProviderResources(List<String> resourceIds,
                                                                    ResourceKind resourceType,
                                                                    Collection<String> tag,
                                                                    TagMatchStrategy tagMatchStrategy,
                                                                    Object session) {
        ServerIdLookup lookup = findResourceIds(resourceIds, tag, tagMatchStrategy, true);
        if (lookup.ids().isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        List<Object> results = new ArrayList<>();
        for (String getId : lookup.ids()) {
            try {
                if (tagManager.hasResource(getId)) {
                    Object resource = dispatchGet(resourceType, getId, session).toCompletableFuture().join();
                    if (resource != null || lookup.exactMatch()) {
                        results.add(resource);
                    }
                }
            } catch (Exception exception) {
                if (lookup.exactMatch()) {
                    throw exception;
                }
                results.add(new ErrorResult<>(exception));
            }
        }
        return CompletableFuture.completedFuture(results);
    }

    private ServerIdLookup findResourceIds(Collection<String> resourceIds, Collection<String> tag,
                                           TagMatchStrategy tagMatchStrategy, boolean skipIfTagNotExists) {
        if (resourceIds != null) {
            validateResourceIds(resourceIds, "resource");
            return new ServerIdLookup(new ArrayList<>(resourceIds), true);
        }
        List<String> ids = findResourcesByTags(tag == null ? List.of(ResourceManagerBase.GLOBAL) : tag,
                tagMatchStrategy, skipIfTagNotExists);
        return new ServerIdLookup(ids, false);
    }

    private ServerIdLookup getServerIds(Collection<String> serverIds, Collection<String> serverNames,
                                        Collection<String> tag, TagMatchStrategy tagMatchStrategy,
                                        boolean skipIfTagNotExists, StatusCode errorCode) {
        if (serverIds != null) {
            if (serverIds.isEmpty()) {
                throw buildError(errorCode, "server_config", String.valueOf(serverIds), "reason", "server_id is empty");
            }
            validateResourceIds(serverIds, "mcp server");
            return new ServerIdLookup(new ArrayList<>(serverIds), true);
        }

        List<String> ids = new ArrayList<>();
        if (serverNames == null) {
            ids.addAll(findResourcesByTags(tag == null ? List.of(ResourceManagerBase.GLOBAL) : tag,
                    tagMatchStrategy, skipIfTagNotExists));
        } else {
            if (serverNames.isEmpty()) {
                throw buildError(errorCode, "server_id", null, "reason", "server_name is empty");
            }
            for (String serverName : serverNames) {
                ids.addAll(toolMgr.getMcpServerIds(serverName));
            }
        }
        return new ServerIdLookup(ids, false);
    }

    private List<String> findResourcesByTags(Collection<String> tag, TagMatchStrategy tagMatchStrategy,
                                             boolean skipIfTagNotExists) {
        Collection<String> effectiveTag = tag == null ? List.of(ResourceManagerBase.GLOBAL) : tag;
        if (effectiveTag.size() == 1) {
            return tagManager.findResourcesByTags(effectiveTag.iterator().next(), tagMatchStrategy,
                    skipIfTagNotExists);
        }
        return tagManager.findResourcesByTags(effectiveTag, tagMatchStrategy, skipIfTagNotExists);
    }

    private void dispatchAdd(ResourceKind resourceType, String resourceId, Object resource,
                             BaseCard resourceCard, String interfaceUrl) {
        switch (resourceType) {
            case TEAM -> agentTeamManager.addAgentTeam(resourceId, (Supplier<?>) resource);
            case AGENT -> {
                AgentCard agentCard = resourceCard instanceof AgentCard card ? card : null;
                if (resource instanceof RemoteAgent remoteAgent) {
                    agentManager.addAgent(resourceId, remoteAgent, agentCard, interfaceUrl);
                } else {
                    agentManager.addAgent(resourceId, (Supplier<?>) resource, agentCard, interfaceUrl);
                }
            }
            case WORKFLOW -> workflowManager.addWorkflow(resourceId, (Supplier<?>) resource);
            case TOOL -> toolManager.addTool(resourceId, (Tool) resource);
            case MODEL -> modelManager.addModel(resourceId, (Supplier<?>) resource);
            case PROMPT -> promptManager.addPrompt(resourceId, (PromptTemplate) resource);
            case SYS_OPERATION -> sysOperationManager.addSysOperation(resourceId, (SysOperation) resource);
        }
    }

    private void dispatchRemove(ResourceKind resourceType, String resourceId) {
        switch (resourceType) {
            case TEAM -> agentTeamManager.removeAgentTeam(resourceId);
            case AGENT -> agentManager.removeAgent(resourceId);
            case WORKFLOW -> workflowManager.removeWorkflow(resourceId);
            case TOOL -> toolManager.removeTool(resourceId);
            case MODEL -> modelManager.removeModel(resourceId);
            case PROMPT -> promptManager.removePrompt(resourceId);
            case SYS_OPERATION -> sysOperationManager.removeSysOperation(resourceId);
        }
    }

    private CompletionStage<Object> dispatchGet(ResourceKind resourceType, String resourceId, Object session) {
        return switch (resourceType) {
            case TEAM -> agentTeamManager.getAgentTeam(resourceId);
            case AGENT -> agentManager.getAgent(resourceId);
            case WORKFLOW -> workflowManager.getWorkflow(resourceId, session).thenApply(workflow -> workflow);
            case TOOL -> CompletableFuture.completedFuture(toolManager.getTool(resourceId, session));
            case MODEL -> modelManager.getModel(resourceId, session);
            case PROMPT -> CompletableFuture.completedFuture(promptManager.getPrompt(resourceId));
            case SYS_OPERATION -> CompletableFuture.completedFuture(sysOperationManager.getSysOperation(resourceId));
        };
    }

    private void refreshExistingToolIfNeeded(Tool tool, boolean refresh) {
        if (!refresh || tool == null || tool.getCard() == null) {
            return;
        }
        String toolId = tool.getCard().getId();
        if (!tagManager.hasResource(toolId)) {
            return;
        }
        innerRemoveResources(List.of(toolId), ResourceKind.TOOL, null, TagMatchStrategy.ALL, false);
    }

    private void registerSysOperationTools(SysOperationCard card, SysOperation instance, Collection<String> tag) {
        List<String> toolIds = new ArrayList<>();
        for (SysOperationToolAdapter.ToolBinding binding : SysOperationToolAdapter.extractTools(card, instance)) {
            innerAddResource(binding.toolId(), ResourceKind.TOOL, binding.localFunction(),
                    binding.localFunction().getCard(), tag, null);
            toolIds.add(binding.toolId());
        }
        toolManager.addSysOperationTools(card.getId(), toolIds);
    }

    private void removeSysOperationTools(Collection<String> sysOperationIds, Collection<String> tag,
                                         boolean skipIfTagNotExists) {
        List<String> toolIdsToRemove = new ArrayList<>();
        for (String sysOperationId : sysOperationIds) {
            toolIdsToRemove.addAll(toolManager.removeSysOperationTools(sysOperationId));
        }
        if (!toolIdsToRemove.isEmpty()) {
            innerRemoveResources(toolIdsToRemove, ResourceKind.TOOL, tag, TagMatchStrategy.ALL, skipIfTagNotExists);
        }
    }

    private void removeById(String resourceId) {
        for (ResourceKind kind : ResourceKind.values()) {
            try {
                dispatchRemove(kind, resourceId);
            } catch (Exception ignored) {
                // Mirrors Python's best-effort registry removal by id.
            }
        }
        idToCard.remove(resourceId);
    }

    private static Result<?, ?> singleResult(List<Result<?, ?>> results) {
        return results.isEmpty() ? new Ok<>(null) : results.get(0);
    }

    private static List<Result<?, ?>> noOpRemovalResults() {
        return List.of(new Ok<>(null));
    }

    @SuppressWarnings("unchecked")
    private static <T> com.openjiuwen.core.runner.base.Result<T> baseResult(Result<?, ?> result) {
        return (com.openjiuwen.core.runner.base.Result<T>)
                (com.openjiuwen.core.runner.base.Result<?>) result;
    }

    private static <T> List<com.openjiuwen.core.runner.base.Result<T>> baseResultList(
            List<? extends Result<?, ?>> results) {
        List<com.openjiuwen.core.runner.base.Result<T>> converted = new ArrayList<>();
        for (Result<?, ?> result : results) {
            converted.add(baseResult(result));
        }
        return converted;
    }

    private static Collection<String> effectiveTags(Collection<String> tags) {
        return tags == null || tags.isEmpty() ? List.of(ResourceManagerBase.GLOBAL) : List.copyOf(tags);
    }

    private static void validateOptionalTags(Collection<String> tag) {
        if (tag != null) {
            validateTags(tag);
        }
    }

    public static void validateTag(String tag) {
        if (tag == null || tag.isEmpty()) {
            throw buildError(StatusCode.RESOURCE_TAG_VALUE_INVALID, "tag", String.valueOf(tag),
                    "reason", "is None or empty value");
        }
    }

    public static void validateTags(Collection<String> tags) {
        if (tags == null || tags.isEmpty()) {
            throw buildError(StatusCode.RESOURCE_TAG_VALUE_INVALID, "tag", String.valueOf(tags),
                    "reason", "is None or empty value");
        }
        if (tags.contains(ResourceManagerBase.GLOBAL) && tags.size() > 1) {
            throw buildError(StatusCode.RESOURCE_TAG_VALUE_INVALID, "tag", String.valueOf(tags),
                    "reason", "The GLOBAL tag already exists and cannot be assigned additional tags.");
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag == null || tag.isEmpty()) {
                throw buildError(StatusCode.RESOURCE_TAG_VALUE_INVALID, "tag", String.valueOf(tags),
                        "reason", "has None or empty value");
            }
            if (!seen.add(tag)) {
                throw buildError(StatusCode.RESOURCE_TAG_VALUE_INVALID, "tag", String.valueOf(tags),
                        "reason", "has duplicate tag '" + tag + "' item");
            }
        }
    }

    public static void validateResourceCard(BaseCard card, String resourceType,
                                            Class<? extends BaseCard> cardClassType) {
        if (!cardClassType.isInstance(card)) {
            throw buildError(StatusCode.RESOURCE_CARD_VALUE_INVALID, "resource_type", resourceType,
                    "reason", "cannot be None, must be an instance of " + cardClassType.getSimpleName());
        }
    }

    public static void validateResourceId(String resourceId, String resourceType) {
        if (resourceId == null || resourceId.isEmpty()) {
            throw buildError(StatusCode.RESOURCE_ID_VALUE_INVALID, "resource_type", resourceType,
                    "reason", "cannot be empty or None");
        }
        if (resourceId.isBlank()) {
            throw buildError(StatusCode.RESOURCE_ID_VALUE_INVALID, "resource_type", resourceType,
                    "reason", "string id cannot be empty or whitespace only");
        }
    }

    public static void validateResourceIds(Collection<String> resourceIds, String resourceType) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            throw buildError(StatusCode.RESOURCE_ID_VALUE_INVALID, "resource_type", resourceType,
                    "reason", resourceType + " id list cannot be empty or None");
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        int index = 0;
        for (String resourceId : resourceIds) {
            try {
                validateResourceId(resourceId, resourceType);
            } catch (BaseError error) {
                throw buildError(StatusCode.RESOURCE_ID_VALUE_INVALID, "resource_type", resourceType,
                        "reason", "invalid " + resourceType + " id at idx " + index + ": "
                                + error.getMessage());
            }
            if (!seen.add(resourceId)) {
                throw buildError(StatusCode.RESOURCE_ID_VALUE_INVALID, "resource_type", resourceType,
                        "reason", "duplicate " + resourceType + " id found: '" + resourceId
                                + "' appears multiple times in the list");
            }
            index += 1;
        }
    }

    public static void validateProvider(Supplier<?> provider, String resourceType) {
        if (provider == null) {
            throw buildError(StatusCode.RESOURCE_PROVIDER_INVALID, "resource_type", resourceType,
                    "reason", "provider cannot be None, must be a callable function");
        }
    }

    public static void validateResource(Object instance, String resourceType, Class<?> resourceClassType) {
        if (instance == null) {
            throw buildError(StatusCode.RESOURCE_VALUE_INVALID, "resource_type", resourceType,
                    "reason", resourceType + " cannot be None: expected an instance or list of "
                            + resourceClassType.getSimpleName());
        }
        if (!resourceClassType.isInstance(instance)) {
            throw buildError(StatusCode.RESOURCE_VALUE_INVALID, "resource_type", resourceType,
                    "reason", "invalid " + resourceType + " type: expected "
                            + resourceClassType.getSimpleName() + ", got "
                            + instance.getClass().getSimpleName());
        }
    }

    public static void validateTool(Tool tool) {
        validateResource(tool, "tool", Tool.class);
        validateResourceCard(tool.getCard(), "tool", ToolCard.class);
        validateResourceId(tool.getCard().getId(), "tool");
    }

    public static void validateToolList(List<? extends Tool> tools) {
        if (tools == null || tools.isEmpty()) {
            throw buildError(StatusCode.RESOURCE_VALUE_INVALID, "resource_type", "tool",
                    "reason", "tool list cannot be empty: expected a non-empty list of Tool");
        }
        for (Tool tool : tools) {
            validateTool(tool);
        }
    }

    public static void validateServerConfig(McpServerConfig serverConfig) {
        if (serverConfig == null) {
            throw buildError(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID,
                    "server_config", "null", "reason", "MCP server configuration cannot be empty or None");
        }
        validateOneServerConfig(serverConfig);
    }

    public static void validateServerConfigs(List<McpServerConfig> serverConfigs) {
        if (serverConfigs == null || serverConfigs.isEmpty()) {
            throw buildError(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID,
                    "server_config", String.valueOf(serverConfigs), "reason", "MCP server configuration list is empty");
        }
        LinkedHashSet<String> seenIds = new LinkedHashSet<>();
        int index = 0;
        for (McpServerConfig config : serverConfigs) {
            if (config == null) {
                throw buildError(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID,
                        "server_config", String.valueOf(serverConfigs),
                        "reason", "invalid MCP server configuration at idx " + index
                                + ": configuration cannot be None");
            }
            validateOneServerConfig(config);
            if (!seenIds.add(config.getServerId())) {
                throw buildError(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID,
                        "server_config", String.valueOf(serverConfigs),
                        "reason", "duplicate MCP server_id found: '" + config.getServerId() + "'");
            }
            index += 1;
        }
    }

    public static String getCardType(BaseCard card) {
        if (card instanceof McpToolCard) {
            return "mcp";
        }
        if (card instanceof ToolCard) {
            return "function";
        }
        if (card instanceof TeamCard) {
            return "team";
        }
        if (card instanceof WorkflowCard) {
            return "workflow";
        }
        if (card instanceof AgentCard) {
            return "agent";
        }
        return null;
    }

    private static void validateOneServerConfig(McpServerConfig config) {
        if (config.getServerId() == null || config.getServerId().isEmpty()) {
            throw buildError(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID,
                    "server_config", String.valueOf(config), "reason", "MCP server configuration is missing server_id");
        }
        if (config.getServerId().isBlank()) {
            throw buildError(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID,
                    "server_config", String.valueOf(config), "reason", "MCP server_id cannot be empty or whitespace only");
        }
    }

    private static void validateExpiry(Double expiryTime) {
        if (expiryTime != null && expiryTime <= 0.0D) {
            throw buildError(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID,
                    "server_config", "expire_time", "reason", "expire time <= 0");
        }
    }

    private static void validateProviderEntries(List<? extends ProviderEntry> entries, String resourceType,
                                                Class<? extends BaseCard> cardClassType) {
        if (entries == null || entries.isEmpty()) {
            throw buildError(StatusCode.RESOURCE_PROVIDER_INVALID, "resource_type", resourceType,
                    "reason", " cannot be empty: expected a non-empty list of pairs");
        }
        int index = 0;
        for (ProviderEntry entry : entries) {
            if (entry == null || entry.resourceItem() == null || entry.provider() == null) {
                throw buildError(StatusCode.RESOURCE_PROVIDER_INVALID, "resource_type", resourceType,
                        "reason", "invalid provider format at idx " + index);
            }
            if (cardClassType != null) {
                validateResourceCard((BaseCard) entry.resourceItem(), resourceType, cardClassType);
                validateResourceId(((BaseCard) entry.resourceItem()).getId(), resourceType);
            } else if (entry.resourceItem() instanceof String resourceId) {
                validateResourceId(resourceId, resourceType);
            } else {
                throw buildError(StatusCode.RESOURCE_PROVIDER_INVALID, "resource_type", resourceType,
                        "reason", "invalid " + resourceType + " id at idx " + index);
            }
            validateProvider(entry.provider(), resourceType);
            index += 1;
        }
    }

    private static <T> List<T> typedList(Object value, Class<T> type) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        List<T> results = new ArrayList<>();
        for (Object item : values) {
            if (type.isInstance(item)) {
                results.add(type.cast(item));
            }
        }
        return results;
    }

    private static TagMatchStrategy strategy(com.openjiuwen.core.runner.base.TagMatchStrategy strategy) {
        return strategy == null ? TagMatchStrategy.ALL : strategy.toResourceManagerStrategy();
    }

    private static Collection<String> stringCollection(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Collection<?> collection) {
            List<String> values = new ArrayList<>();
            for (Object item : collection) {
                if (item != null) {
                    values.add(String.valueOf(item));
                }
            }
            return values;
        }
        return List.of(String.valueOf(value));
    }

    private static boolean isLegacyScalarEmptyId(Object value) {
        return value instanceof String stringValue && stringValue.isEmpty();
    }

    private static CompletionStage<Object> invokeAsync(Object target, String methodName) {
        return invokeAsync(target, methodName, null, null);
    }

    private static CompletionStage<Object> invokeAsync(Object target, String methodName, float timeout) {
        Method method = findMethod(target.getClass(), methodName, float.class);
        if (method != null) {
            return invokeAsyncMethod(target, method, timeout);
        }
        method = findMethod(target.getClass(), methodName, double.class);
        if (method != null) {
            return invokeAsyncMethod(target, method, (double) timeout);
        }
        return invokeAsync(target, methodName);
    }

    private static CompletionStage<Object> invokeAsync(Object target, String methodName, String argument,
                                                       float timeout) {
        Method method = findMethod(target.getClass(), methodName, String.class, float.class);
        if (method != null) {
            return invokeAsyncMethod(target, method, argument, timeout);
        }
        method = findMethod(target.getClass(), methodName, String.class, double.class);
        if (method != null) {
            return invokeAsyncMethod(target, method, argument, (double) timeout);
        }
        return invokeAsync(target, methodName, String.class, argument);
    }

    private static CompletionStage<Object> invokeAsync(Object target, String methodName,
                                                       Class<?> parameterType, Object argument) {
        try {
            Method method = parameterType == null
                    ? target.getClass().getMethod(methodName)
                    : target.getClass().getMethod(methodName, parameterType);
            Object value = parameterType == null ? method.invoke(target) : method.invoke(target, argument);
            return CompletableFuture.completedFuture(awaitIfNeeded(value));
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    private static CompletionStage<Object> invokeAsyncMethod(Object target, Method method, Object... args) {
        try {
            return CompletableFuture.completedFuture(awaitIfNeeded(method.invoke(target, args)));
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    private static Method findMethod(Class<?> type, String methodName, Class<?>... parameterTypes) {
        try {
            return type.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Object awaitIfNeeded(Object value) {
        if (!(value instanceof CompletionStage<?> stage)) {
            return value;
        }
        try {
            return stage.toCompletableFuture().get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CompletionException(interrupted);
        } catch (ExecutionException error) {
            throw new CompletionException(error.getCause());
        }
    }

    private static BaseError buildError(StatusCode status, String... kvPairs) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (int index = 0; index + 1 < kvPairs.length; index += 2) {
            params.put(kvPairs[index], kvPairs[index + 1]);
        }
        return ErrorHelper.buildError(status, null, null, null, params);
    }

    private void resetManagers() {
        resourceRegistry = new ResourceRegistry();
        agentTeamManager = resourceRegistry.agentTeamManager();
        agentManager = resourceRegistry.agentManager();
        workflowManager = resourceRegistry.workflowManager();
        toolMgr = resourceRegistry.tool();
        toolManager = resourceRegistry.toolManager();
        modelManager = resourceRegistry.modelManager();
        promptManager = resourceRegistry.promptManager();
        sysOperationManager = resourceRegistry.sysOperationManager();
        tagMgr = new TagMgr();
        tagManager = tagMgr;
        idToCard = new LinkedHashMap<>();
    }

    private enum ResourceKind {
        TEAM("team", false),
        AGENT("agent", false),
        WORKFLOW("workflow", false),
        TOOL("tool", true),
        MODEL("model", true),
        PROMPT("prompt", true),
        SYS_OPERATION("sys_operation", false);

        private final String pythonName;
        private final boolean returnsId;

        ResourceKind(String pythonName, boolean returnsId) {
            this.pythonName = pythonName;
            this.returnsId = returnsId;
        }

        private String pythonName() {
            return pythonName;
        }

        private boolean returnsId() {
            return returnsId;
        }
    }

    private record ServerIdLookup(List<String> ids, boolean exactMatch) {
    }

    private sealed interface ProviderEntry permits AgentEntry, WorkflowEntry, ModelEntry {
        Object resourceItem();

        Supplier<?> provider();
    }

    /**
     * Mirrors Python's {@code tuple[AgentCard, AgentProvider]} input item in
     * {@code openjiuwen/core/runner/resources_manager/resource_manager.py}.
     */
    public record AgentEntry(AgentCard card, Supplier<?> provider) implements ProviderEntry {
        @Override
        public Object resourceItem() {
            return card;
        }
    }

    /**
     * Mirrors Python's {@code tuple[WorkflowCard, WorkflowProvider]} input item in
     * {@code openjiuwen/core/runner/resources_manager/resource_manager.py}.
     */
    public record WorkflowEntry(WorkflowCard card, Supplier<?> provider) implements ProviderEntry {
        @Override
        public Object resourceItem() {
            return card;
        }
    }

    /**
     * Mirrors Python's {@code tuple[str, ModelProvider]} input item in
     * {@code openjiuwen/core/runner/resources_manager/resource_manager.py}.
     */
    public record ModelEntry(String modelId, Supplier<?> provider) implements ProviderEntry {
        @Override
        public Object resourceItem() {
            return modelId;
        }
    }

    /**
     * Mirrors Python's {@code tuple[str, PromptTemplate]} input item in
     * {@code openjiuwen/core/runner/resources_manager/resource_manager.py}.
     */
    public record PromptEntry(String promptId, PromptTemplate template) {
    }
}
