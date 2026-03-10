/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
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

    public List<Result<GroupCard>> removeAgentGroup(Object groupId,
                                                     Object tag,
                                                     TagMatchStrategy tagMatchStrategy,
                                                     boolean skipIfTagNotExists) {
        return innerRemoveResources(groupId, tag, tagMatchStrategy, skipIfTagNotExists, "group");
    }

    public Object getAgentGroup(String groupId, Object tag,
                                TagMatchStrategy tagMatchStrategy) {
        return innerGetResourcesByProvider(groupId, tag, tagMatchStrategy, "group");
    }

    // ========== Agent ==========

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

    public Object removeAgent(Object agentId, Object tag,
                              TagMatchStrategy tagMatchStrategy,
                              boolean skipIfTagNotExists) {
        return innerRemoveResources(agentId, tag, tagMatchStrategy, skipIfTagNotExists, "agent");
    }

    public Object getAgent(String agentId, Object tag,
                           TagMatchStrategy tagMatchStrategy) {
        return innerGetResourcesByProvider(agentId, tag, tagMatchStrategy, "agent");
    }

    public Object getAgent(String agentId) {
        return innerGetResourcesByProvider(agentId, null, TagMatchStrategy.ALL, "agent");
    }

    // ========== Workflow ==========

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

    public Object removeWorkflow(Object workflowId, Object tag,
                                 TagMatchStrategy tagMatchStrategy,
                                 boolean skipIfTagNotExists) {
        return innerRemoveResources(workflowId, tag, tagMatchStrategy, skipIfTagNotExists, "workflow");
    }

    public Object getWorkflow(String workflowId, Object tag,
                              TagMatchStrategy tagMatchStrategy) {
        return innerGetResourcesByProvider(workflowId, tag, tagMatchStrategy, "workflow");
    }

    public Object getWorkflow(String workflowId) {
        return innerGetResourcesByProvider(workflowId, null, TagMatchStrategy.ALL, "workflow");
    }

    // ========== Tool ==========

    public Result<ToolCard> addTool(Tool tool, Object tag) {
        validateResource(tool, "tool", Tool.class);
        if (tag != null) {
            validateTag(tag);
        }
        return innerAddResource(tool.getCard().getId(), tool, tool.getCard(), tag, "tool");
    }

    public List<Result<ToolCard>> addTools(List<Tool> tools, Object tag) {
        if (tools == null || tools.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_VALUE_INVALID,
                    "resource_type", "tool", "reason", "tool list cannot be empty");
        }
        if (tag != null) {
            validateTag(tag);
        }
        List<Result<ToolCard>> results = new ArrayList<>();
        for (Tool tool : tools) {
            results.add(innerAddResource(tool.getCard().getId(), tool, tool.getCard(), tag, "tool"));
        }
        return results;
    }

    public Object getTool(String toolId, Object tag,
                          TagMatchStrategy tagMatchStrategy) {
        return innerGetResources(toolId, tag, tagMatchStrategy, "tool");
    }

    public Object getTool(String toolId) {
        return innerGetResources(toolId, null, TagMatchStrategy.ALL, "tool");
    }

    public Object removeTool(Object toolId, Object tag,
                             TagMatchStrategy tagMatchStrategy,
                             boolean skipIfTagNotExists) {
        return innerRemoveResources(toolId, tag, tagMatchStrategy, skipIfTagNotExists, "tool");
    }

    // ========== Model ==========

    public Result<String> addModel(String modelId, Supplier<Model> model, Object tag) {
        validateResourceId(modelId, "model");
        validateProvider(model, "model");
        if (tag != null) {
            validateTag(tag);
        }
        return innerAddResource(modelId, model, null, tag, "model");
    }

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

    public Object removeModel(Object modelId, Object tag,
                              TagMatchStrategy tagMatchStrategy,
                              boolean skipIfTagNotExists) {
        return innerRemoveResources(modelId, tag, tagMatchStrategy, skipIfTagNotExists, "model");
    }

    public Object getModel(String modelId, Object tag,
                           TagMatchStrategy tagMatchStrategy) {
        return innerGetResourcesByProvider(modelId, tag, tagMatchStrategy, "model");
    }

    public Object getModel(String modelId) {
        return innerGetResourcesByProvider(modelId, null, TagMatchStrategy.ALL, "model");
    }

    // ========== Prompt ==========

    public Result<String> addPrompt(String promptId, PromptTemplate template, Object tag) {
        validateResourceId(promptId, "prompt");
        validateResource(template, "prompt", PromptTemplate.class);
        if (tag != null) {
            validateTag(tag);
        }
        return innerAddResource(promptId, template, null, tag, "prompt");
    }

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

    public Object removePrompt(Object promptId, Object tag,
                               TagMatchStrategy tagMatchStrategy,
                               boolean skipIfTagNotExists) {
        return innerRemoveResources(promptId, tag, tagMatchStrategy, skipIfTagNotExists, "prompt");
    }

    public Object getPrompt(String promptId, Object tag,
                            TagMatchStrategy tagMatchStrategy) {
        return innerGetResources(promptId, tag, tagMatchStrategy, "prompt");
    }

    public Object getPrompt(String promptId) {
        return innerGetResources(promptId, null, TagMatchStrategy.ALL, "prompt");
    }

    // ========== SysOperation ==========

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

    public Object removeSysOperation(Object sysOperationId, Object tag,
                                     TagMatchStrategy tagMatchStrategy,
                                     boolean skipIfTagNotExists) {
        Object results = innerRemoveResources(sysOperationId, tag, tagMatchStrategy,
                skipIfTagNotExists, "sys_operation");

        List<String> sysOpIds = normalizeIds(sysOperationId);
        List<String> toolIdsToRemove = new ArrayList<>();
        for (String opId : sysOpIds) {
            List<String> ids = resourceRegistry.tool().removeSysOperationTools(opId);
            toolIdsToRemove.addAll(ids);
        }
        if (!toolIdsToRemove.isEmpty()) {
            innerRemoveResources(toolIdsToRemove, tag, tagMatchStrategy, skipIfTagNotExists, "tool");
        }
        return results;
    }

    public Object getSysOperation(String sysOperationId, Object tag,
                                  TagMatchStrategy tagMatchStrategy) {
        return innerGetResources(sysOperationId, tag, tagMatchStrategy, "sys_operation");
    }

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

    public List<ToolInfo> getToolInfos(Object toolId, Object toolType, Object tag,
                                       TagMatchStrategy tagMatchStrategy) {
        FindResult findResult = innerFindResourceIds(toolId, tag, tagMatchStrategy);
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
            if (findResult.exactMatch()) {
                results.add(null);
            }
        }
        return results;
    }

    // ========== MCP Server ==========

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

    public List<Result<String>> removeMcpServer(Object serverId, Object serverName,
                                                 Object tag, TagMatchStrategy tagMatchStrategy,
                                                 boolean skipIfTagNotExists) throws Exception {
        List<String> serverIdsToRemove = innerGetServerIds(serverId, serverName, tag,
                tagMatchStrategy, skipIfTagNotExists,
                StatusCode.RESOURCE_MCP_SERVER_REMOVE_ERROR);
        List<Result<String>> results = new ArrayList<>();
        for (String mcpServerId : serverIdsToRemove) {
            try {
                tagMgr.removeResource(mcpServerId);
                List<String> toolIds = resourceRegistry.tool().removeToolServer(mcpServerId);
                if (toolIds != null && !toolIds.isEmpty()) {
                    innerRemoveResources(toolIds, tag, tagMatchStrategy, skipIfTagNotExists, "tool");
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

    public Object getMcpTool(Object name, Object serverId, Object serverName,
                             Object tag, TagMatchStrategy tagMatchStrategy,
                             boolean skipIfTagNotExists) throws Exception {
        List<String> serverIdsToGet = innerGetServerIds(serverId, serverName, tag,
                tagMatchStrategy, skipIfTagNotExists,
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

    // ========== Tag Operations ==========

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

    public List<String> listTags() {
        return tagMgr.listTags();
    }

    public boolean hasTag(String tag) {
        validateTag(tag);
        return tagMgr.hasTag(tag);
    }

    public List<Result<String>> removeTag(Object tag, boolean skipIfTagNotExists) {
        validateTag(tag);
        List<String> tagsToRemove = normalizeStringList(tag);
        List<Result<String>> results = new ArrayList<>();
        for (String singleTag : tagsToRemove) {
            List<String> resourceToRemoval = tagMgr.removeTag(singleTag, skipIfTagNotExists);
            for (String resourceId : resourceToRemoval) {
                resourceRegistry.removeById(resourceId);
            }
            logger.info("remove tag succeed, tag={}, removedResources={}", singleTag, resourceToRemoval);
            results.add(new Ok<>(singleTag));
        }
        return results;
    }

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

    public Result<List<String>> removeResourceTag(String resourceId, Object tag,
                                                   boolean skipIfTagNotExists) {
        validateResourceId(resourceId);
        validateTag(tag);
        try {
            List<String> remainTags = tagMgr.removeResourceTags(resourceId, tag, skipIfTagNotExists);
            return new Ok<>(remainTags);
        } catch (Exception e) {
            return new Error<>(e);
        }
    }

    public List<String> getResourceTag(String resourceId) {
        return tagMgr.getResourcesTags(resourceId);
    }

    public boolean resourceHasTag(String resourceId, String tag) {
        validateTag(tag);
        validateResourceId(resourceId);
        return tagMgr.hasResourceTag(resourceId, tag);
    }

    public void release() {
        resourceRegistry.tool().release();
    }

    // ========== Internal Methods ==========

    @SuppressWarnings("unchecked")
    private <C> Result<C> innerAddResource(String resourceId, Object resource,
                                           BaseCard resourceCard, Object tag,
                                           String resourceType) {
        try {
            if (tagMgr.hasResource(resourceId)) {
                throw ErrorHelper.buildError(StatusCode.RESOURCE_ADD_ERROR,
                        "card", resourceCard != null ? resourceCard.toString() : resourceId,
                        "reason", "resource already exist");
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
                                                      boolean skipIfTagNotExists,
                                                      String resourceType) {
        List<String> idsToRemove;
        boolean removeByTag = false;
        if (resourceId != null) {
            idsToRemove = normalizeIds(resourceId);
        } else {
            validateTag(tag);
            idsToRemove = tagMgr.findResourcesByTags(tag, tagMatchStrategy != null ? tagMatchStrategy
                    : TagMatchStrategy.ALL, skipIfTagNotExists);
            removeByTag = true;
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
                if (!removeByTag) {
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
                if (removedCard != null || !removeByTag) {
                    results.add(new Ok<>((C) removedCard));
                }
            }
        }
        return results;
    }

    private FindResult innerFindResourceIds(Object resourceId, Object tag,
                                            TagMatchStrategy tagMatchStrategy) {
        if (resourceId != null) {
            return new FindResult(normalizeIds(resourceId), true);
        }
        List<String> ids = tagMgr.findResourcesByTags(tag != null ? tag : Tag.GLOBAL,
                tagMatchStrategy != null ? tagMatchStrategy : TagMatchStrategy.ALL, false);
        return new FindResult(ids, false);
    }

    private Object innerGetResources(Object resourceId, Object tag,
                                     TagMatchStrategy tagMatchStrategy,
                                     String resourceType) {
        FindResult findResult = innerFindResourceIds(resourceId, tag, tagMatchStrategy);
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
            if (resource != null || findResult.exactMatch()) {
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
        FindResult findResult = innerFindResourceIds(resourceId, tag, tagMatchStrategy);
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
            if (resource != null || findResult.exactMatch()) {
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
                                           boolean skipIfTagNotExists,
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
                    skipIfTagNotExists));
        } else {
            List<String> serverNames = normalizeStringList(serverName);
            if (serverNames.isEmpty()) {
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
        if (config instanceof McpServerConfig sc) {
            return List.of(sc);
        }
        if (config instanceof List<?> list) {
            return (List<McpServerConfig>) list;
        }
        throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID,
                "server_config", String.valueOf(config),
                "reason", "Invalid MCP server configuration type");
    }

    // ========== Record Types ==========

    public record AgentEntry(AgentCard card, Supplier<Object> provider) {
    }

    public record WorkflowEntry(WorkflowCard card, Supplier<Workflow> provider) {
    }

    public record ModelEntry(String id, Supplier<Model> provider) {
    }

    public record PromptEntry(String id, PromptTemplate template) {
    }

    private record FindResult(List<String> ids, boolean exactMatch) {
    }
}
