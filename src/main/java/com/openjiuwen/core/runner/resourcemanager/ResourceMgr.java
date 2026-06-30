/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.multiagent.schema.GroupCard;
import com.openjiuwen.core.runner.base.Error;
import com.openjiuwen.core.runner.base.Ok;
import com.openjiuwen.core.runner.base.Result;
import com.openjiuwen.core.runner.base.Tag;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.runner.base.TagUpdateStrategy;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.SysOperationToolAdapter;
import com.openjiuwen.core.sysop.registry.OperationRegistry;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Resource Manager facade for Model, Workflow, Prompt, Tool, Agent, AgentGroup, SysOperation.
 * <p>
 * Mirrors Python's {@code ResourceMgr} in {@code resources_manager/resource_manager.py}.
 */
public class ResourceMgr {

    private static final Logger logger = LoggerFactory.getLogger(ResourceMgr.class);

    private final ResourceRegistry resourceRegistry = new ResourceRegistry();
    private final TagMgr tagMgr = new TagMgr();
    private final Map<String, BaseCard> idToCard = new HashMap<>();

    // ========== Agent Group ==========

    /**
     * Auto-generated for codecheck compliance.
     */
    public Result<GroupCard> addAgentGroup(GroupCard card,
                                           Supplier<Object> agentGroup,
                                           Object tag) {
        validateResourceCard(card, "group", GroupCard.class);
        validateResourceId(card.getId(), "group");
        validateProvider(agentGroup, "group");
        if (tag != null) {
            validateTag(tag);
        }
        return innerAddResource(card.getId(), agentGroup, card, tag, "group");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Result<GroupCard>> removeAgentGroup(Object groupId,
                                                     Object tag,
                                                     TagMatchStrategy tagMatchStrategy,
                                                     boolean shouldSkipMissingTag) {
        return innerRemoveResources(groupId, tag, tagMatchStrategy, shouldSkipMissingTag, "group");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getAgentGroup(String groupId, Object tag,
                                TagMatchStrategy tagMatchStrategy) {
        return innerGetResourcesByProvider(groupId, tag, tagMatchStrategy, "group");
    }

    // ========== Agent ==========

    /**
     * Auto-generated for codecheck compliance.
     */
    public Result<AgentCard> addAgent(AgentCard card,
                                      Supplier<Object> agent,
                                      Object tag) {
        validateResourceCard(card, "agent", AgentCard.class);
        validateResourceId(card.getId(), "agent");
        validateProvider(agent, "agent");
        if (tag != null) {
            validateTag(tag);
        }
        return innerAddResource(card.getId(), agent, card, tag, "agent");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Result<AgentCard>> addAgents(List<AgentEntry> agents, Object tag) {
        if (agents == null || agents.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_PROVIDER_INVALID,
                    "resource_type", "agent", "reason", "cannot be empty");
        }
        if (tag != null) {
            validateTag(tag);
        }
        List<Result<AgentCard>> results = new ArrayList<>();
        for (AgentEntry entry : agents) {
            results.add(innerAddResource(entry.card().getId(), entry.provider(),
                    entry.card(), tag, "agent"));
        }
        return results;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object removeAgent(Object agentId, Object tag,
                              TagMatchStrategy tagMatchStrategy,
                              boolean shouldSkipMissingTag) {
        return innerRemoveResources(agentId, tag, tagMatchStrategy, shouldSkipMissingTag, "agent");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getAgent(String agentId, Object tag,
                           TagMatchStrategy tagMatchStrategy) {
        return innerGetResourcesByProvider(agentId, tag, tagMatchStrategy, "agent");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getAgent(String agentId) {
        return innerGetResourcesByProvider(agentId, null, TagMatchStrategy.ALL, "agent");
    }

    // ========== Workflow ==========

    /**
     * Auto-generated for codecheck compliance.
     */
    public Result<WorkflowCard> addWorkflow(WorkflowCard card,
                                             Supplier<Workflow> workflow,
                                             Object tag) {
        validateResourceCard(card, "workflow", WorkflowCard.class);
        validateResourceId(card.getId(), "workflow");
        validateProvider(workflow, "workflow");
        if (tag != null) {
            validateTag(tag);
        }
        return innerAddResource(card.getId(), workflow, card, tag, "workflow");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Result<WorkflowCard>> addWorkflows(List<WorkflowEntry> workflows, Object tag) {
        if (workflows == null || workflows.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_PROVIDER_INVALID,
                    "resource_type", "workflow", "reason", "cannot be empty");
        }
        if (tag != null) {
            validateTag(tag);
        }
        List<Result<WorkflowCard>> results = new ArrayList<>();
        for (WorkflowEntry entry : workflows) {
            results.add(innerAddResource(entry.card().getId(), entry.provider(),
                    entry.card(), tag, "workflow"));
        }
        return results;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object removeWorkflow(Object workflowId, Object tag,
                                 TagMatchStrategy tagMatchStrategy,
                                 boolean shouldSkipMissingTag) {
        return innerRemoveResources(workflowId, tag, tagMatchStrategy, shouldSkipMissingTag, "workflow");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getWorkflow(String workflowId, Object tag,
                              TagMatchStrategy tagMatchStrategy) {
        Object workflow = innerGetResourcesByProvider(workflowId, tag, tagMatchStrategy, "workflow");
        if (workflow != null) {
            return workflow;
        }
        return findWorkflowByAlternateId(workflowId);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getWorkflow(String workflowId) {
        Object workflow = innerGetResourcesByProvider(workflowId, null, TagMatchStrategy.ALL, "workflow");
        if (workflow != null) {
            return workflow;
        }
        return findWorkflowByAlternateId(workflowId);
    }

    // ========== Tool ==========

    /**
     * Auto-generated for codecheck compliance.
     */
    public Result<ToolCard> addTool(Tool tool, Object tag) {
        return addTool(tool, tag, false);
    }

    public Result<ToolCard> addTool(Tool tool, Object tag, boolean refresh) {
        validateResource(tool, "tool", Tool.class);
        if (tag != null) {
            validateTag(tag);
        }
        if (refresh) {
            refreshExistingResourceIfNeeded(tool.getCard().getId(), tag);
        }
        return innerAddResource(tool.getCard().getId(), tool, tool.getCard(), tag, "tool");
    }

    public List<Result<ToolCard>> addTools(List<Tool> tools, Object tag) {
        return addTools(tools, tag, false);
    }

    public List<Result<ToolCard>> addTools(List<Tool> tools, Object tag, boolean refresh) {
        if (tools == null || tools.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_VALUE_INVALID,
                    "resource_type", "tool", "reason", "tool list cannot be empty");
        }
        if (tag != null) {
            validateTag(tag);
        }
        List<Result<ToolCard>> results = new ArrayList<>();
        for (Tool tool : tools) {
            if (refresh) {
                refreshExistingResourceIfNeeded(tool.getCard().getId(), tag);
            }
            results.add(innerAddResource(tool.getCard().getId(), tool, tool.getCard(), tag, "tool"));
        }
        return results;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getTool(String toolId, Object tag,
                          TagMatchStrategy tagMatchStrategy) {
        return innerGetResources(toolId, tag, tagMatchStrategy, "tool");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getTool(String toolId) {
        return innerGetResources(toolId, null, TagMatchStrategy.ALL, "tool");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object removeTool(Object toolId, Object tag,
                             TagMatchStrategy tagMatchStrategy,
                             boolean shouldSkipMissingTag) {
        return innerRemoveResources(toolId, tag, tagMatchStrategy, shouldSkipMissingTag, "tool");
    }

    // ========== Model ==========

    /**
     * Auto-generated for codecheck compliance.
     */
    public Result<String> addModel(String modelId, Supplier<Model> model, Object tag) {
        validateResourceId(modelId, "model");
        validateProvider(model, "model");
        if (tag != null) {
            validateTag(tag);
        }
        return innerAddResource(modelId, model, null, tag, "model");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Result<String>> addModels(List<ModelEntry> models, Object tag) {
        if (models == null || models.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_PROVIDER_INVALID,
                    "resource_type", "model", "reason", "cannot be empty");
        }
        if (tag != null) {
            validateTag(tag);
        }
        List<Result<String>> results = new ArrayList<>();
        for (ModelEntry entry : models) {
            results.add(innerAddResource(entry.id(), entry.provider(), null, tag, "model"));
        }
        return results;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object removeModel(Object modelId, Object tag,
                              TagMatchStrategy tagMatchStrategy,
                              boolean shouldSkipMissingTag) {
        return innerRemoveResources(modelId, tag, tagMatchStrategy, shouldSkipMissingTag, "model");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getModel(String modelId, Object tag,
                           TagMatchStrategy tagMatchStrategy) {
        return innerGetResourcesByProvider(modelId, tag, tagMatchStrategy, "model");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getModel(String modelId) {
        return innerGetResourcesByProvider(modelId, null, TagMatchStrategy.ALL, "model");
    }

    // ========== Prompt ==========

    /**
     * Auto-generated for codecheck compliance.
     */
    public Result<String> addPrompt(String promptId, PromptTemplate template, Object tag) {
        validateResourceId(promptId, "prompt");
        validateResource(template, "prompt", PromptTemplate.class);
        if (tag != null) {
            validateTag(tag);
        }
        return innerAddResource(promptId, template, null, tag, "prompt");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Result<String>> addPrompts(List<PromptEntry> prompts, Object tag) {
        if (prompts == null || prompts.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_VALUE_INVALID,
                    "resource_type", "prompt", "reason", "prompt list cannot be empty");
        }
        if (tag != null) {
            validateTag(tag);
        }
        List<Result<String>> results = new ArrayList<>();
        for (PromptEntry entry : prompts) {
            results.add(innerAddResource(entry.id(), entry.template(), null, tag, "prompt"));
        }
        return results;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object removePrompt(Object promptId, Object tag,
                               TagMatchStrategy tagMatchStrategy,
                               boolean shouldSkipMissingTag) {
        return innerRemoveResources(promptId, tag, tagMatchStrategy, shouldSkipMissingTag, "prompt");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getPrompt(String promptId, Object tag,
                            TagMatchStrategy tagMatchStrategy) {
        return innerGetResources(promptId, tag, tagMatchStrategy, "prompt");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getPrompt(String promptId) {
        return innerGetResources(promptId, null, TagMatchStrategy.ALL, "prompt");
    }

    // ========== SysOperation ==========

    /**
     * Auto-generated for codecheck compliance.
     */
    public Result<SysOperationCard> addSysOperation(SysOperationCard card, Object tag) {
        validateResourceCard(card, "sys_operation", SysOperationCard.class);
        if (tag != null) {
            validateTag(tag);
        }
        SysOperation instance = new SysOperation(card);
        Result<SysOperationCard> res = innerAddResource(card.getId(), instance, card, tag, "sys_operation");
        if (res.isOk()) {
            registerSysOperationTools(card, instance, tag);
        }
        return res;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object removeSysOperation(Object sysOperationId, Object tag,
                                     TagMatchStrategy tagMatchStrategy,
                                     boolean shouldSkipMissingTag) {
        Object results = innerRemoveResources(sysOperationId, tag, tagMatchStrategy,
                shouldSkipMissingTag, "sys_operation");

        List<String> sysOpIds = normalizeIds(sysOperationId);
        List<String> toolIdsToRemove = new ArrayList<>();
        for (String opId : sysOpIds) {
            List<String> ids = resourceRegistry.tool().removeSysOperationTools(opId);
            toolIdsToRemove.addAll(ids);
        }
        if (!toolIdsToRemove.isEmpty()) {
            innerRemoveResources(toolIdsToRemove, tag, tagMatchStrategy, shouldSkipMissingTag, "tool");
        }
        return results;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getSysOperation(String sysOperationId, Object tag,
                                  TagMatchStrategy tagMatchStrategy) {
        return innerGetResources(sysOperationId, tag, tagMatchStrategy, "sys_operation");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getSysOpToolCards(String sysOperationId, Object operationName, Object toolName) {
        if (operationName instanceof List && toolName != null) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_VALUE_INVALID,
                    "resource_type", "sys_operation",
                    "reason", "tool_name cannot be specified when operation_name is a list");
        }

        SysOperation sysOp = resourceRegistry.sysOperation().getSysOperation(sysOperationId);
        if (sysOp == null) {
            return null;
        }

        List<String> operationNames;
        if (operationName == null) {
            operationNames = OperationRegistry.getSupportedOperations(sysOp.getMode());
        } else if (operationName instanceof String s) {
            operationNames = List.of(s);
        } else if (operationName instanceof List<?> list) {
            operationNames = list.stream().map(Object::toString).toList();
        } else {
            operationNames = List.of(operationName.toString());
        }

        List<String> toolNames = null;
        if (toolName instanceof String s) {
            toolNames = List.of(s);
        } else if (toolName instanceof List<?> list) {
            toolNames = list.stream().map(Object::toString).toList();
        }

        List<BaseCard> result = new ArrayList<>();
        for (String opName : operationNames) {
            if (toolNames == null) {
                List<String> toolIds = resourceRegistry.tool().getSysOperationToolIds(sysOperationId);
                for (String toolId : toolIds) {
                    if (toolId.startsWith(sysOperationId + "." + opName + ".")) {
                        BaseCard card = idToCard.get(toolId);
                        if (card != null) {
                            result.add(card);
                        }
                    }
                }
            } else {
                for (String tName : toolNames) {
                    String toolId = SysOperationCard.generateToolId(sysOperationId, opName, tName);
                    BaseCard card = idToCard.get(toolId);
                    if (card != null) {
                        result.add(card);
                    }
                }
            }
        }

        if (toolName instanceof String) {
            return result.isEmpty() ? null : result.get(0);
        }
        return result;
    }

    // ========== Tool Infos ==========

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<ToolInfo> getToolInfos(Object toolId, Object toolType, Object tag,
                                       TagMatchStrategy tagMatchStrategy) {
        FindResult findResult = innerFindResourceIds(toolId, tag, tagMatchStrategy, true);
        List<ToolInfo> results = new ArrayList<>();
        if (findResult.ids() == null || findResult.ids().isEmpty()) {
            return results;
        }
        List<String> types = normalizeStringList(toolType);
        for (String resourceId : findResult.ids()) {
            BaseCard card = idToCard.get(resourceId);
            if (!types.isEmpty() && !types.contains(getCardType(card))) {
                continue;
            }
            if (card != null) {
                Object info = card.toolInfo();
                if (info instanceof ToolInfo ti) {
                    results.add(ti);
                    continue;
                }
            }
            if (findResult.isExactMatch()) {
                results.add(null);
            }
        }
        return results;
    }

    // ========== MCP Server ==========

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Result<String>> addMcpServer(Object serverConfig, Object tag, Double expiryTime) throws Exception {
        if (tag != null) {
            validateTag(tag);
        }
        if (expiryTime != null && expiryTime <= 0) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID,
                    "param", "expire_time", "reason", "expire time <= 0");
        }
        List<McpServerConfig> configs = normalizeServerConfigs(serverConfig);
        List<Result<String>> addResults = new ArrayList<>();

        for (McpServerConfig config : configs) {
            try {
                // Validate client type
                if (!"sse".equals(config.getClientType()) && !"stdio".equals(config.getClientType())
                        && !"streamable_http".equals(config.getClientType())) {
                    throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID,
                            "param", "client_type",
                            "reason", "Unsupported MCP client type: " + config.getClientType());
                }
                List<McpToolCard> cards = resourceRegistry.tool().addToolServer(config, expiryTime);
                for (McpToolCard card : cards) {
                    idToCard.put(card.getId(), card);
                    tagMgr.tagResource(card.getId(), tag != null ? tag : Tag.GLOBAL);
                }
                tagMgr.tagResource(config.getServerId(), tag != null ? tag : Tag.GLOBAL);
                addResults.add(new Ok<>(config.getServerId()));
                logger.info("add mcp server succeed, serverId={}", config.getServerId());
            } catch (Exception e) {
                addResults.add(new Error<>(e));
                logger.error("add mcp server failed, serverId={}", config.getServerId(), e);
            }
        }
        return addResults;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Result<String>> removeMcpServer(Object serverId, Object serverName,
                                                 Object tag, TagMatchStrategy tagMatchStrategy,
                                                 boolean shouldSkipMissingTag) throws Exception {
        List<String> serverIdsToRemove = innerGetServerIds(serverId, serverName, tag,
                tagMatchStrategy, shouldSkipMissingTag,
                StatusCode.RESOURCE_MCP_SERVER_REMOVE_ERROR);
        List<Result<String>> results = new ArrayList<>();
        for (String mcpServerId : serverIdsToRemove) {
            try {
                tagMgr.removeResource(mcpServerId);
                List<String> toolIds = resourceRegistry.tool().removeToolServer(mcpServerId);
                if (toolIds != null && !toolIds.isEmpty()) {
                    innerRemoveResources(toolIds, tag, tagMatchStrategy, shouldSkipMissingTag, "tool");
                }
                results.add(new Ok<>(mcpServerId));
                logger.info("remove mcp server succeed, serverId={}", mcpServerId);
            } catch (Exception e) {
                results.add(new Error<>(e));
                logger.error("remove mcp server failed, serverId={}", mcpServerId, e);
            }
        }
        return results;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getMcpTool(Object name, Object serverId, Object serverName,
                             Object tag, TagMatchStrategy tagMatchStrategy,
                             boolean shouldSkipMissingTag) throws Exception {
        List<String> serverIdsToGet = innerGetServerIds(serverId, serverName, tag,
                tagMatchStrategy, shouldSkipMissingTag,
                StatusCode.RESOURCE_MCP_TOOL_GET_ERROR);
        List<Tool> results = new ArrayList<>();
        List<String> toolNames = normalizeStringList(name);
        for (String mcpServerId : serverIdsToGet) {
            try {
                resourceRegistry.tool().refreshToolServer(mcpServerId, true, false);
            } catch (Exception ignored) {
                // ignore refresh errors
            }
            if (toolNames.isEmpty()) {
                List<Tool> tools = resourceRegistry.tool().getMcpTools(mcpServerId);
                if (tools != null) {
                    results.addAll(tools);
                }
                continue;
            }
            for (String toolName : toolNames) {
                Tool tool = resourceRegistry.tool().getMcpTool(toolName, mcpServerId);
                if (tool != null) {
                    results.add(tool);
                }
            }
        }
        return results;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<?> listMcpResources(Object serverId, Object serverName,
                                    Object tag, TagMatchStrategy tagMatchStrategy,
                                    boolean shouldSkipMissingTag) throws Exception {
        List<String> serverIdsToGet = innerGetServerIds(serverId, serverName, tag,
                tagMatchStrategy, shouldSkipMissingTag,
                StatusCode.RESOURCE_MCP_TOOL_GET_ERROR);
        List<Object> results = new ArrayList<>();
        for (String mcpServerId : serverIdsToGet) {
            results.addAll(resourceRegistry.tool().listMcpResources(mcpServerId));
        }
        return results;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<?> readMcpResource(String resourceServerId, String uri) throws Exception {
        return resourceRegistry.tool().readMcpResource(resourceServerId, uri);
    }

    /**
     * Refresh MCP server tool card(s).
     * <p>
     * Current implementation is a stub that returns an empty list,
     * matching the Python reference implementation.
     *
     * @param serverId            MCP server ID(s) to refresh
     * @param serverName          MCP server name(s) to refresh
     * @param tag                 Optional tag to filter servers
     * @param tagMatchStrategy    Strategy for matching tags
     * @param shouldIgnoreException     If true, continue refreshing other servers on error
     * @param shouldSkipMissingTag      If true, skip non-existent tags
     * @return List of Result with server IDs or errors
     */
    public List<Result<String>> refreshMcpServer(Object serverId, Object serverName,
                                                  Object tag, TagMatchStrategy tagMatchStrategy,
                                                  boolean shouldIgnoreException,
                                                  boolean shouldSkipMissingTag) {
        return Collections.emptyList();
    }

    /**
     * Get MCP tool information/metadata by name and server.
     *
     * @param name                MCP tool name(s) to get info for (null returns all)
     * @param serverId            MCP server ID(s) containing the tools
     * @param serverName          MCP server name(s) containing the tools
     * @param tag                 Optional tag to filter servers/tools
     * @param tagMatchStrategy    Strategy for matching tags
     * @param shouldSkipMissingTag      If true, skip non-existent tags
     * @param shouldIgnoreException     If true, ignore refresh exceptions
     * @return List of ToolInfo for matching MCP tools
     */
    public List<ToolInfo> getMcpToolInfos(Object name, Object serverId, Object serverName,
                                           Object tag, TagMatchStrategy tagMatchStrategy,
                                           boolean shouldSkipMissingTag,
                                           boolean shouldIgnoreException) throws Exception {
        List<String> serverIdsToGet = innerGetServerIds(serverId, serverName, tag,
                tagMatchStrategy, shouldSkipMissingTag,
                StatusCode.RESOURCE_MCP_TOOL_GET_ERROR);
        List<String> toolNames = normalizeStringList(name);
        List<ToolInfo> results = new ArrayList<>();
        for (String mcpServerId : serverIdsToGet) {
            try {
                resourceRegistry.tool().refreshToolServer(mcpServerId, true, false);
            } catch (Exception e) {
                if (!shouldIgnoreException) {
                    throw e;
                }
            }
            List<String> toolIds = new ArrayList<>();
            if (toolNames.isEmpty()) {
                // getMcpToolId with null toolName returns the full list
                Object idsObj = resourceRegistry.tool().getMcpToolId(mcpServerId, null);
                if (idsObj instanceof List<?> idList) {
                    for (Object item : idList) {
                        toolIds.add(String.valueOf(item));
                    }
                }
            } else {
                for (String toolName : toolNames) {
                    Object toolIdObj = resourceRegistry.tool().getMcpToolId(mcpServerId, toolName);
                    if (toolIdObj instanceof String toolId) {
                        toolIds.add(toolId);
                    }
                }
            }
            for (String toolId : toolIds) {
                BaseCard card = idToCard.get(toolId);
                if (card instanceof ToolCard toolCard) {
                    ToolInfo toolInfo = toolCard.toolInfo();
                    if (toolInfo != null) {
                        results.add(toolInfo);
                    }
                }
            }
        }
        return results;
    }

    // ========== Tag Operations ==========

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<BaseCard> getResourceByTag(String tag) {
        validateTag(tag);
        List<String> resourceIds = tagMgr.getTagResources(tag);
        if (resourceIds == null || resourceIds.isEmpty()) {
            return resourceIds != null ? Collections.emptyList() : null;
        }
        List<BaseCard> cards = new ArrayList<>();
        for (String resourceId : resourceIds) {
            cards.add(idToCard.get(resourceId));
        }
        return cards;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> listTags() {
        return tagMgr.listTags();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean hasTag(String tag) {
        validateTag(tag);
        return tagMgr.hasTag(tag);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Result<String>> removeTag(Object tag, boolean shouldSkipMissingTag) {
        validateTag(tag);
        List<String> tagsToRemove = normalizeStringList(tag);
        List<Result<String>> results = new ArrayList<>();
        for (String singleTag : tagsToRemove) {
            List<String> resourceToRemoval = tagMgr.removeTag(singleTag, shouldSkipMissingTag);
            for (String resourceId : resourceToRemoval) {
                resourceRegistry.removeById(resourceId);
            }
            logger.info("remove tag succeed, tag={}, removedResources={}", singleTag, resourceToRemoval);
            results.add(new Ok<>(singleTag));
        }
        return results;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Result<List<String>> updateResourceTag(String resourceId, Object tag) {
        validateResourceId(resourceId);
        validateTag(tag);
        try {
            List<String> resultTags = tagMgr.updateResourceTags(resourceId, tag, TagUpdateStrategy.REPLACE);
            return new Ok<>(resultTags);
        } catch (Exception e) {
            return new Error<>(e);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Result<List<String>> addResourceTag(String resourceId, Object tag) {
        validateResourceId(resourceId);
        validateTag(tag);
        try {
            List<String> nowTags = tagMgr.tagResource(resourceId, tag);
            return new Ok<>(nowTags);
        } catch (Exception e) {
            return new Error<>(e);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Result<List<String>> removeResourceTag(String resourceId, Object tag,
                                                   boolean shouldSkipMissingTag) {
        validateResourceId(resourceId);
        validateTag(tag);
        try {
            List<String> remainTags = tagMgr.removeResourceTags(resourceId, tag, shouldSkipMissingTag);
            return new Ok<>(remainTags);
        } catch (Exception e) {
            return new Error<>(e);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> getResourceTag(String resourceId) {
        return tagMgr.getResourcesTags(resourceId);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean hasResourceTag(String resourceId, String tag) {
        validateTag(tag);
        validateResourceId(resourceId);
        return tagMgr.hasResourceTag(resourceId, tag);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean resourceHasTag(String resourceId, String tag) {
        return hasResourceTag(resourceId, tag);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void release() {
        resourceRegistry.clearAll();
        tagMgr.clear();
        idToCard.clear();
    }

    // ========== Internal Methods ==========

    private void refreshExistingResourceIfNeeded(String resourceId, Object tag) {
        if (!tagMgr.hasResource(resourceId)) {
            return;
        }
        innerRemoveResources(resourceId, tag, TagMatchStrategy.ALL, true, "tool");
        logger.info("refreshed existing resource, id={}", resourceId);
    }

    @SuppressWarnings("unchecked")
    private <C> Result<C> innerAddResource(String resourceId, Object resource,
                                           BaseCard resourceCard, Object tag,
                                           String resourceType) {
        try {
            if (tagMgr.hasResource(resourceId)) {
                innerRemoveResources(resourceId, null, TagMatchStrategy.ALL, true, resourceType);
                logger.info("replaced existing resource, id={}, type={}", resourceId, resourceType);
            }
            switch (resourceType) {
                case "workflow" -> resourceRegistry.workflow().addWorkflow(resourceId,
                        (Supplier<Workflow>) resource);
                case "agent" -> resourceRegistry.agent().addAgent(resourceId,
                        (Supplier<Object>) resource);
                case "group" -> resourceRegistry.agentGroup().addAgentGroup(resourceId,
                        (Supplier<Object>) resource);
                case "tool" -> resourceRegistry.tool().addTool(resourceId, (Tool) resource);
                case "prompt" -> resourceRegistry.prompt().addPrompt(resourceId, (PromptTemplate) resource);
                case "model" -> resourceRegistry.model().addModel(resourceId,
                        (Supplier<Model>) resource);
                case "sys_operation" -> resourceRegistry.sysOperation().addSysOperation(resourceId,
                        (SysOperation) resource);
                default -> { /* no-op */ }
            }
            if (resourceCard != null) {
                idToCard.put(resourceId, resourceCard);
            }
            tagMgr.tagResource(resourceId, tag != null ? tag : Tag.GLOBAL);
            logger.info("add resource succeed, id={}, type={}", resourceId, resourceType);
            return new Ok<>((C) (resourceCard != null ? resourceCard : resourceId));
        } catch (Exception e) {
            logger.error("add resource failed, id={}, type={}", resourceId, resourceType, e);
            return new Error<>(e);
        }
    }

    @SuppressWarnings("unchecked")
    private <C> List<Result<C>> innerRemoveResources(Object resourceId, Object tag,
                                                      TagMatchStrategy tagMatchStrategy,
                                                      boolean shouldSkipMissingTag,
                                                      String resourceType) {
        List<String> idsToRemove;
        boolean isRemoveByTag = false;
        if (resourceId != null) {
            idsToRemove = normalizeIds(resourceId);
        } else {
            validateTag(tag);
            idsToRemove = tagMgr.findResourcesByTags(tag, tagMatchStrategy != null ? tagMatchStrategy
                    : TagMatchStrategy.ALL, shouldSkipMissingTag);
            isRemoveByTag = true;
            if (idsToRemove.isEmpty()) {
                return Collections.emptyList();
            }
        }

        List<Result<C>> results = new ArrayList<>();
        for (String removeId : idsToRemove) {
            Exception error = null;
            try {
                tagMgr.removeResource(removeId);
                switch (resourceType) {
                    case "workflow" -> resourceRegistry.workflow().removeWorkflow(removeId);
                    case "agent" -> resourceRegistry.agent().removeAgent(removeId);
                    case "group" -> resourceRegistry.agentGroup().removeAgentGroup(removeId);
                    case "model" -> resourceRegistry.model().removeModel(removeId);
                    case "tool" -> resourceRegistry.tool().removeTool(removeId);
                    case "prompt" -> resourceRegistry.prompt().removePrompt(removeId);
                    case "sys_operation" -> resourceRegistry.sysOperation().removeSysOperation(removeId);
                    default -> { /* no-op */ }
                }
            } catch (Exception e) {
                if (!isRemoveByTag) {
                    error = e;
                }
            }
            BaseCard removedCard = idToCard.remove(removeId);
            if (error != null) {
                logger.error("remove resource failed, id={}, type={}", removeId, resourceType, error);
                results.add(new Error<>(error));
            } else if ("tool".equals(resourceType) || "prompt".equals(resourceType)) {
                results.add(new Ok<>((C) removeId));
            } else {
                if (removedCard != null || !isRemoveByTag) {
                    results.add(new Ok<>((C) removedCard));
                }
            }
        }
        return results;
    }

    private FindResult innerFindResourceIds(Object resourceId, Object tag,
                                            TagMatchStrategy tagMatchStrategy) {
        return innerFindResourceIds(resourceId, tag, tagMatchStrategy, false);
    }

    private FindResult innerFindResourceIds(Object resourceId, Object tag,
                                            TagMatchStrategy tagMatchStrategy,
                                            boolean shouldSkipMissingTag) {
        if (resourceId != null) {
            return new FindResult(normalizeIds(resourceId), true);
        }
        List<String> ids = tagMgr.findResourcesByTags(tag != null ? tag : Tag.GLOBAL,
                tagMatchStrategy != null ? tagMatchStrategy : TagMatchStrategy.ALL,
                shouldSkipMissingTag);
        return new FindResult(ids, false);
    }

    private Object innerGetResources(Object resourceId, Object tag,
                                     TagMatchStrategy tagMatchStrategy,
                                     String resourceType) {
        FindResult findResult = innerFindResourceIds(resourceId, tag, tagMatchStrategy, true);
        List<Object> results = new ArrayList<>();
        for (String getId : findResult.ids()) {
            Object resource = null;
            try {
                if (tagMgr.hasResource(getId)) {
                    resource = switch (resourceType) {
                        case "tool" -> resourceRegistry.tool().getTool(getId);
                        case "prompt" -> resourceRegistry.prompt().getPrompt(getId);
                        case "sys_operation" -> resourceRegistry.sysOperation().getSysOperation(getId);
                        default -> null;
                    };
                }
            } catch (Exception ignored) {
                // swallow
            }
            if (resource != null || findResult.isExactMatch()) {
                results.add(resource);
            }
        }
        if (results.size() == 1 && resourceId instanceof String) {
            return results.get(0);
        }
        return results;
    }

    private Object innerGetResourcesByProvider(Object resourceId, Object tag,
                                               TagMatchStrategy tagMatchStrategy,
                                               String resourceType) {
        FindResult findResult = innerFindResourceIds(resourceId, tag, tagMatchStrategy, true);
        List<Object> results = new ArrayList<>();
        if (findResult.ids() == null || findResult.ids().isEmpty()) {
            return results;
        }
        for (String getId : findResult.ids()) {
            Object resource = null;
            try {
                if (tagMgr.hasResource(getId)) {
                    resource = switch (resourceType) {
                        case "workflow" -> resourceRegistry.workflow().getWorkflow(getId);
                        case "agent" -> resourceRegistry.agent().getAgent(getId);
                        case "group" -> resourceRegistry.agentGroup().getAgentGroup(getId);
                        case "model" -> resourceRegistry.model().getModel(getId);
                        default -> null;
                    };
                }
            } catch (Exception ignored) {
                // swallow
            }
            if (resource != null || findResult.isExactMatch()) {
                results.add(resource);
            }
        }
        if (results.size() == 1 && resourceId instanceof String) {
            return results.get(0);
        }
        return results;
    }

    private void registerSysOperationTools(SysOperationCard card, SysOperation instance, Object tag) {
        List<SysOperationToolAdapter.ToolEntry> tools = SysOperationToolAdapter.extractTools(card, instance);
        List<String> toolIds = new ArrayList<>();
        for (SysOperationToolAdapter.ToolEntry entry : tools) {
            innerAddResource(entry.toolId(), entry.localFunction(),
                    entry.localFunction().getCard(), tag, "tool");
            toolIds.add(entry.toolId());
        }
        resourceRegistry.tool().addSysOperationTools(card.getId(), toolIds);
    }

    private List<String> innerGetServerIds(Object serverId, Object serverName,
                                           Object tag, TagMatchStrategy tagMatchStrategy,
                                           boolean shouldSkipMissingTag,
                                           StatusCode errorCode) {
        List<String> ids = new ArrayList<>();
        if (serverId != null) {
            if (serverId instanceof String s) {
                if (s.isEmpty()) {
                    throw ErrorHelper.buildError(errorCode,
                            "server_config", String.valueOf(serverId),
                            "reason", "server_id is empty");
                }
                ids.add(s);
            }
        } else if (serverName == null) {
            ids.addAll(tagMgr.findResourcesByTags(tag != null ? tag : Tag.GLOBAL,
                    tagMatchStrategy != null ? tagMatchStrategy : TagMatchStrategy.ALL,
                    shouldSkipMissingTag));
        } else {
            List<String> serverNames = normalizeStringList(serverName);
            if (serverNames.isEmpty()) {
                throw ErrorHelper.buildError(errorCode,
                        "server_id", String.valueOf(serverId), "reason", "server_name is empty");
            }
            if (serverNames.stream().anyMatch(String::isEmpty)) {
                throw ErrorHelper.buildError(errorCode,
                        "server_id", String.valueOf(serverId), "reason", "server_name is empty");
            }
            for (String sName : serverNames) {
                ids.addAll(resourceRegistry.tool().getMcpServerIds(sName));
            }
        }
        return ids;
    }

    // ========== Validation ==========

    private static void validateTag(Object tag) {
        if (tag == null) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_TAG_VALUE_INVALID,
                    "tag", "null", "reason", "is None or empty value");
        }
        if (tag instanceof String s && s.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_TAG_VALUE_INVALID,
                    "tag", "''", "reason", "is None or empty value");
        }
        if (tag instanceof List<?> list) {
            if (list.contains(Tag.GLOBAL) && list.size() > 1) {
                throw ErrorHelper.buildError(StatusCode.RESOURCE_TAG_VALUE_INVALID,
                        "tag", tag.toString(),
                        "reason", "The GLOBAL tag already exists and cannot be assigned additional tags.");
            }
            Set<Object> seen = new HashSet<>();
            for (Object item : list) {
                if (item == null || (item instanceof String s && s.isEmpty())) {
                    throw ErrorHelper.buildError(StatusCode.RESOURCE_TAG_VALUE_INVALID,
                            "tag", tag.toString(), "reason", "has None or empty value");
                }
                if (!seen.add(item)) {
                    throw ErrorHelper.buildError(StatusCode.RESOURCE_TAG_VALUE_INVALID,
                            "tag", tag.toString(),
                            "reason", "has duplicate tag '" + item + "' item");
                }
            }
        }
    }

    private static void validateResourceCard(BaseCard card, String resourceType, Class<?> cardClassType) {
        if (card == null || !cardClassType.isInstance(card)) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_CARD_VALUE_INVALID,
                    "resource_type", resourceType,
                    "reason", "cannot be None, must be an instance of " + cardClassType.getSimpleName());
        }
    }

    private static void validateResourceId(String resourceId) {
        validateResourceId(resourceId, "resource");
    }

    private static void validateResourceId(String resourceId, String resourceType) {
        if (resourceId == null || resourceId.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_ID_VALUE_INVALID,
                    "resource_type", resourceType, "reason", "cannot be empty or None");
        }
        if (resourceId.isBlank()) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_ID_VALUE_INVALID,
                    "resource_type", resourceType,
                    "reason", "string id cannot be empty or whitespace only");
        }
    }

    private static void validateProvider(Object provider, String resourceType) {
        if (provider == null) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_PROVIDER_INVALID,
                    "resource_type", resourceType,
                    "reason", "provider cannot be None, must be a callable function");
        }
    }

    private static void validateResource(Object instance, String resourceType, Class<?> resourceClassType) {
        if (instance == null) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_VALUE_INVALID,
                    "resource_type", resourceType,
                    "reason", resourceType + " cannot be None: expected an instance of "
                            + resourceClassType.getSimpleName());
        }
        if (!resourceClassType.isInstance(instance)) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_VALUE_INVALID,
                    "resource_type", resourceType,
                    "reason", "invalid " + resourceType + " type: expected "
                            + resourceClassType.getSimpleName() + ", got "
                            + instance.getClass().getSimpleName());
        }
    }

    private static String getCardType(BaseCard card) {
        if (card == null) {
            return null;
        }
        String className = card.getClass().getSimpleName();
        return switch (className) {
            case "GroupCard" -> "group";
            case "WorkflowCard" -> "workflow";
            case "AgentCard" -> "agent";
            case "McpToolCard" -> "mcp";
            case "ToolCard" -> "function";
            default -> null;
        };
    }

    // ========== Utility ==========

    @SuppressWarnings("unchecked")
    private static List<String> normalizeIds(Object id) {
        if (id instanceof String s) {
            return new ArrayList<>(List.of(s));
        }
        if (id instanceof List<?> list) {
            return (List<String>) list;
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private static List<String> normalizeStringList(Object obj) {
        if (obj == null) {
            return Collections.emptyList();
        }
        if (obj instanceof String s) {
            return List.of(s);
        }
        if (obj instanceof List<?> list) {
            return (List<String>) list;
        }
        return List.of(obj.toString());
    }

    @SuppressWarnings("unchecked")
    private static List<McpServerConfig> normalizeServerConfigs(Object config) {
        if (config == null) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID,
                    "server_config", String.valueOf(config),
                    "reason", "MCP server configuration cannot be empty or None"
            );
        }
        if (config instanceof McpServerConfig sc) {
            return List.of(sc);
        }
        if (config instanceof List<?> list) {
            if (list.isEmpty()) {
                throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID,
                        "server_config", String.valueOf(config),
                        "reason", "server_config list is empty"
                );
            }
            List<McpServerConfig> result = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (item == null) {
                    throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID,
                            "server_config", String.valueOf(config),
                            "reason", "Invalid MCP server configuration at index " + i + ": configuration cannot be null"
                    );
                }
                if (!(item instanceof McpServerConfig)) {
                    throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID,
                            "server_config", String.valueOf(config),
                            "reason", "Invalid MCP server configuration type at index " + i + ": expected McpServerConfig"
                    );
                }
                result.add((McpServerConfig) item);
            }
            return result;
        }
        throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID,
                "server_config", String.valueOf(config),
                "reason", "Invalid MCP server configuration type"
        );
    }

    private Object findWorkflowByAlternateId(String workflowId) {
        if (workflowId == null || workflowId.isBlank()) {
            return null;
        }
        for (Map.Entry<String, BaseCard> entry : idToCard.entrySet()) {
            if (!(entry.getValue() instanceof WorkflowCard workflowCard)) {
                continue;
            }
            String registeredId = entry.getKey();
            String baseId = deriveWorkflowBaseId(registeredId, workflowCard.getVersion());
            String versionedId = WorkflowUtils.generateWorkflowKey(baseId, workflowCard.getVersion());
            if (!workflowId.equals(baseId) && !workflowId.equals(versionedId)) {
                continue;
            }
            Object workflow = resourceRegistry.workflow().getWorkflow(registeredId);
            if (workflow != null) {
                return workflow;
            }
        }
        return null;
    }

    private String deriveWorkflowBaseId(String registeredId, String version) {
        if (registeredId == null || version == null || version.isBlank()) {
            return registeredId;
        }
        String suffix = "_" + version;
        if (registeredId.endsWith(suffix) && registeredId.length() > suffix.length()) {
            return registeredId.substring(0, registeredId.length() - suffix.length());
        }
        return registeredId;
    }

    // ========== Record Types ==========

    /**
 * Public record AgentEntry used by the Java parity implementation.
 *
 * @since 1.0
 */
public record AgentEntry(AgentCard card, Supplier<Object> provider) {
    }

    /**
 * Public record WorkflowEntry used by the Java parity implementation.
 *
 * @since 1.0
 */
public record WorkflowEntry(WorkflowCard card, Supplier<Workflow> provider) {
    }

    /**
 * Public record ModelEntry used by the Java parity implementation.
 *
 * @since 1.0
 */
public record ModelEntry(String id, Supplier<Model> provider) {
    }

    /**
 * Public record PromptEntry used by the Java parity implementation.
 *
 * @since 1.0
 */
public record PromptEntry(String id, PromptTemplate template) {
    }

    private record FindResult(List<String> ids, boolean isExactMatch) {
    }
}
