// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorBuilder;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.runner.drunner.remoteclient.RemoteAgent;
import com.openjiuwen.core.sysoperation.SysOperation;
import com.openjiuwen.core.sysoperation.SysOperationCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 资源管理器，提供Agent、Workflow、Tool、Model、Prompt、AgentGroup、SysOperation等资源的统一管理
 * 
 * 对应Python: resources_manager/resource_manager.py - ResourceMgr
 */
public class ResourceMgr {

    private static final Logger logger = LoggerFactory.getLogger(ResourceMgr.class);

    private final ResourceRegistry resourceRegistry;
    private final TagMgr tagMgr;
    private final Map<String, Object> idToCard;

    public ResourceMgr() {
        this.resourceRegistry = new ResourceRegistry();
        this.tagMgr = new TagMgr();
        this.idToCard = new ConcurrentHashMap<>();
    }

    /**
     * 获取底层资源注册表
     */
    public ResourceRegistry getResourceRegistry() {
        return resourceRegistry;
    }

    // ==================== Agent操作 ====================

    /**
     * 添加单个Agent
     *
     * @param cardId   Agent卡片ID（资源唯一标识）
     * @param cardName Agent卡片名称（可为null）
     * @param provider Agent实例的Supplier
     * @param tags     标签列表（可为null，默认使用GLOBAL标签）
     * @return Result对象，成功包含卡片信息
     */
    public Result<?> addAgent(String cardId, String cardName, Supplier<?> provider, List<String> tags) {
        innerValidateResourceCard(cardId);
        innerValidateProvider(cardId, provider);
        if (tags != null) {
            innerValidateTag(tags);
        }
        return innerAddResource(cardId, provider, cardName, tags, "agent");
    }

    /**
     * 添加单个Agent（支持RemoteAgent）
     *
     * @param cardId      Agent卡片ID
     * @param cardName    Agent卡片名称（可为null）
     * @param agent       Agent实例的Supplier或RemoteAgent
     * @param tags        标签列表（可为null）
     * @return Result对象
     */
    public Result<?> addAgent(String cardId, String cardName, Object agent, List<String> tags) {
        innerValidateResourceCard(cardId);
        if (!(agent instanceof RemoteAgent)) {
            if (agent instanceof Supplier) {
                innerValidateProvider(cardId, (Supplier<?>) agent);
            } else {
                throw ErrorBuilder.build(StatusCode.RESOURCE_PROVIDER_INVALID, "provider is None");
            }
        }
        if (tags != null) {
            innerValidateTag(tags);
        }
        return innerAddResource(cardId, agent, cardName, tags, "agent");
    }

    /**
     * 批量添加Agent
     *
     * @param agents Agent条目列表
     * @param tags   标签列表（可为null）
     * @return 结果列表
     */
    public List<Result<?>> addAgents(List<AgentEntry> agents, List<String> tags) {
        if (agents == null || agents.isEmpty()) {
            throw ErrorBuilder.build(StatusCode.RESOURCE_PROVIDER_INVALID, "resource list is None or empty");
        }
        for (AgentEntry entry : agents) {
            innerValidateResourceCard(entry.id);
            innerValidateProvider(entry.id, entry.provider);
        }
        if (tags != null) {
            innerValidateTag(tags);
        }
        List<Result<?>> results = new ArrayList<>();
        for (AgentEntry entry : agents) {
            results.add(innerAddResource(entry.id, entry.provider, null, tags, "agent"));
        }
        return results;
    }

    /**
     * 获取Agent实例
     *
     * @param agentId  Agent ID
     * @param tags     标签列表（可为null）
     * @param strategy 标签匹配策略（可为null，默认ALL）
     * @return 包含Agent实例的CompletableFuture
     */
    public CompletableFuture<?> getAgent(String agentId, List<String> tags, TagMatchStrategy strategy) {
        return innerGetResourceByProvider(agentId, tags, strategy, "agent");
    }

    /**
     * 移除Agent
     *
     * @param agentIds         Agent ID列表
     * @param tags             标签列表（可为null）
     * @param strategy         标签匹配策略
     * @param skipIfNotExists  是否跳过不存在的资源
     * @return 结果列表
     */
    public List<Result<?>> removeAgent(List<String> agentIds, List<String> tags,
                                       TagMatchStrategy strategy, boolean skipIfNotExists) {
        return innerRemoveResources(agentIds, tags, strategy, skipIfNotExists, "agent");
    }

    // ==================== Workflow操作 ====================

    /**
     * 添加单个Workflow
     *
     * @param cardId   Workflow卡片ID
     * @param cardName Workflow卡片名称（可为null）
     * @param provider Workflow实例的Supplier
     * @param tags     标签列表（可为null）
     * @return Result对象
     */
    public Result<?> addWorkflow(String cardId, String cardName, Supplier<?> provider, List<String> tags) {
        innerValidateResourceCard(cardId);
        innerValidateProvider(cardId, provider);
        if (tags != null) {
            innerValidateTag(tags);
        }
        return innerAddResource(cardId, provider, cardName, tags, "workflow");
    }

    /**
     * 批量添加Workflows
     *
     * @param workflows Workflow条目列表
     * @param tags      标签列表（可为null）
     * @return 结果列表
     */
    public List<Result<?>> addWorkflows(List<WorkflowEntry> workflows, List<String> tags) {
        if (workflows == null || workflows.isEmpty()) {
            throw ErrorBuilder.build(StatusCode.RESOURCE_PROVIDER_INVALID, "resource list is None or empty");
        }
        for (WorkflowEntry entry : workflows) {
            innerValidateResourceCard(entry.id);
            innerValidateProvider(entry.id, entry.provider);
        }
        if (tags != null) {
            innerValidateTag(tags);
        }
        List<Result<?>> results = new ArrayList<>();
        for (WorkflowEntry entry : workflows) {
            results.add(innerAddResource(entry.id, entry.provider, null, tags, "workflow"));
        }
        return results;
    }

    /**
     * 获取Workflow实例
     *
     * @param workflowId Workflow ID
     * @param tags       标签列表（可为null）
     * @param strategy   标签匹配策略（可为null）
     * @return 包含Workflow实例的CompletableFuture
     */
    public CompletableFuture<?> getWorkflow(String workflowId, List<String> tags, TagMatchStrategy strategy) {
        return innerGetResourceByProvider(workflowId, tags, strategy, "workflow");
    }

    /**
     * 移除Workflow
     *
     * @param workflowIds      Workflow ID列表
     * @param tags             标签列表（可为null）
     * @param strategy         标签匹配策略
     * @param skipIfNotExists  是否跳过不存在的资源
     * @return 结果列表
     */
    public List<Result<?>> removeWorkflow(List<String> workflowIds, List<String> tags,
                                          TagMatchStrategy strategy, boolean skipIfNotExists) {
        return innerRemoveResources(workflowIds, tags, strategy, skipIfNotExists, "workflow");
    }

    // ==================== Tool操作 ====================

    /**
     * 添加Tool（单个对象方式）
     *
     * @param tool   Tool实例
     * @param toolId Tool ID（可为null，从tool对象中提取）
     * @param tags   标签列表（可为null）
     * @return Result对象
     * @throws BaseError 如果tool为null
     */
    public Result<?> addTool(Object tool, String toolId, List<String> tags) {
        if (tool == null) {
            throw ErrorBuilder.build(StatusCode.RESOURCE_VALUE_INVALID, "resource(s) is None or empty");
        }
        if (tags != null) {
            innerValidateTag(tags);
        }
        // 如果是Tool类型，使用card中的信息
        if (tool instanceof Tool<?, ?> toolObj) {
            String id = toolId != null ? toolId : (toolObj.getCard() != null ? toolObj.getCard().getId() : null);
            Object card = toolObj.getCard();
            return innerAddResource(id, tool, card, tags, "tool");
        }
        return innerAddResource(toolId, tool, null, tags, "tool");
    }

    /**
     * 批量添加Tool
     *
     * @param tools Tool列表
     * @param tags  标签列表（可为null）
     * @return 结果列表
     * @throws BaseError 如果tools为null或空，或包含null元素
     */
    public List<Result<?>> addTools(List<?> tools, List<String> tags) {
        if (tools == null || tools.isEmpty()) {
            throw ErrorBuilder.build(StatusCode.RESOURCE_VALUE_INVALID, "resource(s) is None or empty");
        }
        for (Object item : tools) {
            if (item == null) {
                throw ErrorBuilder.build(StatusCode.RESOURCE_VALUE_INVALID, "tool item is None or empty");
            }
        }
        if (tags != null) {
            innerValidateTag(tags);
        }
        List<Result<?>> results = new ArrayList<>();
        for (Object item : tools) {
            if (item instanceof Tool<?, ?> toolObj) {
                String id = toolObj.getCard() != null ? toolObj.getCard().getId() : null;
                Object card = toolObj.getCard();
                results.add(innerAddResource(id, item, card, tags, "tool"));
            } else {
                results.add(innerAddResource(null, item, null, tags, "tool"));
            }
        }
        return results;
    }

    /**
     * 获取Tool（通过ID或标签）
     *
     * @param toolId   Tool ID（可为null）
     * @param tags     标签列表（可为null）
     * @param strategy 标签匹配策略（可为null）
     * @return Tool实例或Tool列表
     */
    public Object getTool(String toolId, List<String> tags, TagMatchStrategy strategy) {
        return innerGetResource(toolId, tags, strategy, "tool");
    }

    /**
     * 移除Tool
     *
     * @param toolIds          Tool ID列表
     * @param tags             标签列表（可为null）
     * @param strategy         标签匹配策略
     * @param skipIfNotExists  是否跳过不存在的资源
     * @return 结果列表
     */
    public List<Result<?>> removeTool(List<String> toolIds, List<String> tags,
                                      TagMatchStrategy strategy, boolean skipIfNotExists) {
        return innerRemoveResources(toolIds, tags, strategy, skipIfNotExists, "tool");
    }

    // ==================== Prompt操作 ====================

    /**
     * 添加Prompt模板
     *
     * @param promptId 模板ID
     * @param template 模板对象
     * @param tags     标签列表（可为null）
     * @return Result对象
     * @throws BaseError 如果promptId或template为null
     */
    public Result<?> addPrompt(String promptId, Object template, List<String> tags) {
        innerValidateResourceId(promptId);
        if (template == null) {
            throw ErrorBuilder.build(StatusCode.RESOURCE_VALUE_INVALID, "resource is None");
        }
        if (tags != null) {
            innerValidateTag(tags);
        }
        return innerAddResource(promptId, template, null, tags, "prompt");
    }

    /**
     * 批量添加Prompt模板
     *
     * @param prompts Prompt条目列表
     * @param tags    标签列表（可为null）
     * @return 结果列表
     */
    public List<Result<?>> addPrompts(List<PromptEntry> prompts, List<String> tags) {
        if (prompts == null || prompts.isEmpty()) {
            throw ErrorBuilder.build(StatusCode.RESOURCE_VALUE_INVALID, "resource(s) is None or empty");
        }
        for (PromptEntry entry : prompts) {
            innerValidateResourceId(entry.promptId);
            if (entry.template == null) {
                throw ErrorBuilder.build(StatusCode.RESOURCE_VALUE_INVALID, "resource is None");
            }
        }
        if (tags != null) {
            innerValidateTag(tags);
        }
        List<Result<?>> results = new ArrayList<>();
        for (PromptEntry entry : prompts) {
            results.add(innerAddResource(entry.promptId, entry.template, null, tags, "prompt"));
        }
        return results;
    }

    /**
     * 获取Prompt模板
     *
     * @param promptId 模板ID
     * @param tags     标签列表（可为null）
     * @param strategy 标签匹配策略（可为null）
     * @return Prompt模板对象
     */
    public Object getPrompt(String promptId, List<String> tags, TagMatchStrategy strategy) {
        return innerGetResource(promptId, tags, strategy, "prompt");
    }

    /**
     * 移除Prompt模板
     *
     * @param promptIds        Prompt ID列表
     * @param tags             标签列表（可为null）
     * @param strategy         标签匹配策略
     * @param skipIfNotExists  是否跳过不存在的资源
     * @return 结果列表
     */
    public List<Result<?>> removePrompt(List<String> promptIds, List<String> tags,
                                        TagMatchStrategy strategy, boolean skipIfNotExists) {
        return innerRemoveResources(promptIds, tags, strategy, skipIfNotExists, "prompt");
    }

    // ==================== Model操作 ====================

    /**
     * 添加Model
     *
     * @param modelId  Model ID
     * @param provider Model实例的Supplier
     * @param tags     标签列表（可为null）
     * @return Result对象
     */
    public Result<?> addModel(String modelId, Supplier<?> provider, List<String> tags) {
        innerValidateResourceId(modelId);
        innerValidateProvider(modelId, provider);
        if (tags != null) {
            innerValidateTag(tags);
        }
        return innerAddResource(modelId, provider, null, tags, "model");
    }

    /**
     * 批量添加Models
     *
     * @param models Model条目列表
     * @param tags   标签列表（可为null）
     * @return 结果列表
     */
    public List<Result<?>> addModels(List<ModelEntry> models, List<String> tags) {
        if (models == null || models.isEmpty()) {
            throw ErrorBuilder.build(StatusCode.RESOURCE_PROVIDER_INVALID, "resource(s) is None or empty");
        }
        for (ModelEntry entry : models) {
            innerValidateResourceId(entry.modelId);
            innerValidateProvider(entry.modelId, entry.provider);
        }
        if (tags != null) {
            innerValidateTag(tags);
        }
        List<Result<?>> results = new ArrayList<>();
        for (ModelEntry entry : models) {
            results.add(innerAddResource(entry.modelId, entry.provider, null, tags, "model"));
        }
        return results;
    }

    /**
     * 获取Model实例
     *
     * @param modelId  Model ID
     * @param tags     标签列表（可为null）
     * @param strategy 标签匹配策略（可为null）
     * @return 包含Model实例的CompletableFuture
     */
    public CompletableFuture<?> getModel(String modelId, List<String> tags, TagMatchStrategy strategy) {
        return innerGetResourceByProvider(modelId, tags, strategy, "model");
    }

    /**
     * 移除Model
     *
     * @param modelIds         Model ID列表
     * @param tags             标签列表（可为null）
     * @param strategy         标签匹配策略
     * @param skipIfNotExists  是否跳过不存在的资源
     * @return 结果列表
     */
    public List<Result<?>> removeModel(List<String> modelIds, List<String> tags,
                                       TagMatchStrategy strategy, boolean skipIfNotExists) {
        return innerRemoveResources(modelIds, tags, strategy, skipIfNotExists, "model");
    }

    // ==================== AgentGroup操作 ====================

    /**
     * 添加AgentGroup
     *
     * @param cardId   AgentGroup卡片ID
     * @param cardName AgentGroup卡片名称（可为null）
     * @param provider AgentGroup实例的Supplier
     * @param tags     标签列表（可为null）
     * @return Result对象
     */
    public Result<?> addAgentGroup(String cardId, String cardName, Supplier<?> provider, List<String> tags) {
        innerValidateResourceCard(cardId);
        innerValidateProvider(cardId, provider);
        if (tags != null) {
            innerValidateTag(tags);
        }
        return innerAddResource(cardId, provider, cardName, tags, "group");
    }

    /**
     * 获取AgentGroup实例
     *
     * @param groupId  AgentGroup ID
     * @param tags     标签列表（可为null）
     * @param strategy 标签匹配策略（可为null）
     * @return 包含AgentGroup实例的CompletableFuture
     */
    public CompletableFuture<?> getAgentGroup(String groupId, List<String> tags, TagMatchStrategy strategy) {
        return innerGetResourceByProvider(groupId, tags, strategy, "group");
    }

    /**
     * 移除AgentGroup
     *
     * @param groupIds         AgentGroup ID列表
     * @param tags             标签列表（可为null）
     * @param strategy         标签匹配策略
     * @param skipIfNotExists  是否跳过不存在的资源
     * @return 结果列表
     */
    public List<Result<?>> removeAgentGroup(List<String> groupIds, List<String> tags,
                                            TagMatchStrategy strategy, boolean skipIfNotExists) {
        return innerRemoveResources(groupIds, tags, strategy, skipIfNotExists, "group");
    }

    // ==================== SysOperation操作 ====================

    /**
     * 添加SysOperation
     *
     * @param card SysOperationCard（需要有有效id）
     * @param tags 标签列表（可为null）
     * @return Result对象
     */
    public Result<?> addSysOperation(SysOperationCard card, List<String> tags) {
        innerValidateResourceCard(card != null ? card.getId() : null);
        if (tags != null) {
            innerValidateTag(tags);
        }
        SysOperation sysOp = new SysOperation(card);
        return innerAddResource(card.getId(), sysOp, card, tags, "sys_operation");
    }

    /**
     * 移除SysOperation
     *
     * @param sysOperationIds  SysOperation ID列表
     * @param tags             标签列表（可为null）
     * @param strategy         标签匹配策略
     * @param skipIfNotExists  是否跳过不存在的资源
     * @return 结果列表
     */
    public List<Result<?>> removeSysOperation(List<String> sysOperationIds, List<String> tags,
                                              TagMatchStrategy strategy, boolean skipIfNotExists) {
        return innerRemoveResources(sysOperationIds, tags, strategy, skipIfNotExists, "sys_operation");
    }

    /**
     * 获取SysOperation（通过ID或标签）
     *
     * @param sysOperationId SysOperation ID（可为null）
     * @param tags           标签列表（可为null）
     * @param strategy       标签匹配策略（可为null）
     * @return SysOperation实例或列表
     */
    public Object getSysOperation(String sysOperationId, List<String> tags, TagMatchStrategy strategy) {
        return innerGetResource(sysOperationId, tags, strategy, "sys_operation");
    }

    // ==================== Tool信息操作 ====================

    /**
     * 获取Tool的信息/元数据
     *
     * @param toolId           Tool ID（可为null）
     * @param toolType         Tool类型过滤（可为null）
     * @param tags             标签列表（可为null）
     * @param strategy         标签匹配策略（可为null）
     * @param ignoreException  是否忽略异常
     * @return ToolInfo列表
     */
    public List<ToolInfo> getToolInfos(String toolId, List<String> toolType,
                                       List<String> tags, TagMatchStrategy strategy,
                                       boolean ignoreException) {
        List<String> idsToGet;
        boolean exactMatch;
        Object[] findResult = innerFindResourceIds(toolId, tags, strategy);
        idsToGet = (List<String>) findResult[0];
        exactMatch = (Boolean) findResult[1];

        List<ToolInfo> results = new ArrayList<>();
        if (idsToGet == null || idsToGet.isEmpty()) {
            return results;
        }

        for (String resourceId : idsToGet) {
            Object card = idToCard.get(resourceId);
            // 类型过滤
            if (toolType != null && !toolType.isEmpty()) {
                String cardType = getCardType(card);
                if (cardType == null || !toolType.contains(cardType)) {
                    continue;
                }
            }
            // 获取ToolInfo
            if (card instanceof BaseCard baseCard) {
                Object info = baseCard.toolInfo();
                if (info instanceof ToolInfo toolInfo) {
                    results.add(toolInfo);
                    continue;
                }
            }
            if (exactMatch) {
                results.add(null);
            }
        }
        return results;
    }

    // ==================== MCP服务器操作 ====================

    /**
     * 添加MCP服务器
     *
     * @param serverConfig MCP服务器配置（单个或列表）
     * @param tags         标签列表（可为null）
     * @param expiryTime   过期时间（秒，null表示不过期）
     * @return 结果列表
     */
    public CompletableFuture<List<Result<?>>> addMcpServer(McpServerConfig serverConfig,
                                                           List<String> tags,
                                                           Double expiryTime) {
        innerValidateServerConfig(serverConfig);
        if (tags != null) {
            innerValidateTag(tags);
        }
        if (expiryTime != null && expiryTime <= 0) {
            throw ErrorBuilder.build(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID, "expire time <= 0");
        }

        return CompletableFuture.supplyAsync(() -> {
            List<Result<?>> addResults = new ArrayList<>();
            try {
                List<McpToolCard> cards = resourceRegistry.tool()
                    .addToolServer(serverConfig, expiryTime).join();
                for (McpToolCard card : cards) {
                    idToCard.put(card.getId(), card);
                    tagMgr.tagResource(card.getId(), tags != null ? (Object) tags : Tag.GLOBAL);
                }
                tagMgr.tagResource(serverConfig.getServerId(), tags != null ? (Object) tags : Tag.GLOBAL);
                addResults.add(new Ok<>(serverConfig.getServerId()));
                List<String> toolNames = new ArrayList<>();
                for (McpToolCard card : cards) {
                    toolNames.add(card.getName());
                }
                logger.info("add mcp server succeed, id={}, server_name={}, tools={}",
                    serverConfig.getServerId(), serverConfig.getServerName(), toolNames);
            } catch (Exception e) {
                addResults.add(new Error<>(e));
                logger.info("add mcp server failed, id={}, server_name={}, reason={}",
                    serverConfig.getServerId(), serverConfig.getServerName(), e.getMessage());
            }
            return addResults;
        });
    }

    /**
     * 批量添加MCP服务器
     *
     * @param serverConfigs MCP服务器配置列表
     * @param tags          标签列表（可为null）
     * @param expiryTime    过期时间（秒，null表示不过期）
     * @return 结果列表
     */
    public CompletableFuture<List<Result<?>>> addMcpServers(List<McpServerConfig> serverConfigs,
                                                            List<String> tags,
                                                            Double expiryTime) {
        innerValidateServerConfigs(serverConfigs);
        if (tags != null) {
            innerValidateTag(tags);
        }
        if (expiryTime != null && expiryTime <= 0) {
            throw ErrorBuilder.build(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID, "expire time <= 0");
        }

        return CompletableFuture.supplyAsync(() -> {
            List<Result<?>> allResults = new ArrayList<>();
            for (McpServerConfig config : serverConfigs) {
                try {
                    List<McpToolCard> cards = resourceRegistry.tool()
                        .addToolServer(config, expiryTime).join();
                    for (McpToolCard card : cards) {
                        idToCard.put(card.getId(), card);
                        tagMgr.tagResource(card.getId(), tags != null ? (Object) tags : Tag.GLOBAL);
                    }
                    tagMgr.tagResource(config.getServerId(), tags != null ? (Object) tags : Tag.GLOBAL);
                    allResults.add(new Ok<>(config.getServerId()));
                    List<String> toolNames = new ArrayList<>();
                    for (McpToolCard card : cards) {
                        toolNames.add(card.getName());
                    }
                    logger.info("add mcp server succeed, id={}, server_name={}, tools={}",
                        config.getServerId(), config.getServerName(), toolNames);
                } catch (Exception e) {
                    allResults.add(new Error<>(e));
                    logger.info("add mcp server failed, id={}, server_name={}, reason={}",
                        config.getServerId(), config.getServerName(), e.getMessage());
                }
            }
            return allResults;
        });
    }

    /**
     * 刷新MCP服务器
     *
     * @param serverId             服务器ID（可为null）
     * @param serverName           服务器名称（可为null）
     * @param tags                 标签列表（可为null）
     * @param strategy             标签匹配策略
     * @param ignoreException      是否忽略异常
     * @param skipIfNotExists      是否跳过不存在的
     * @return 结果列表
     */
    public List<Result<?>> refreshMcpServer(String serverId, String serverName,
                                            List<String> tags, TagMatchStrategy strategy,
                                            boolean ignoreException, boolean skipIfNotExists) {
        // placeholder - 对应Python中的stub返回[]
        return new ArrayList<>();
    }

    /**
     * 移除MCP服务器
     *
     * @param serverId             服务器ID（可为null）
     * @param serverName           服务器名称（可为null）
     * @param tags                 标签列表（可为null）
     * @param strategy             标签匹配策略
     * @param skipIfNotExists      是否跳过不存在的
     * @param ignoreException      是否忽略异常
     * @return 结果列表
     */
    public CompletableFuture<List<Result<?>>> removeMcpServer(String serverId, String serverName,
                                                              List<String> tags, TagMatchStrategy strategy,
                                                              boolean skipIfNotExists, boolean ignoreException) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> serverIds;
            boolean exactMatch;
            Object[] found = innerGetServerIds(serverId, serverName, tags, strategy,
                skipIfNotExists, StatusCode.RESOURCE_MCP_SERVER_REMOVE_ERROR);
            serverIds = (List<String>) found[0];
            exactMatch = (Boolean) found[1];

            List<Result<?>> results = new ArrayList<>();
            for (String mcpServerId : serverIds) {
                try {
                    tagMgr.removeResource(mcpServerId);
                    List<String> toolIds = resourceRegistry.tool()
                        .removeToolServer(mcpServerId, false).join();
                    if (toolIds != null && !toolIds.isEmpty()) {
                        removeTool(toolIds, null, null, true);
                    }
                    logger.info("remove mcp server succeed, id={}", mcpServerId);
                    results.add(new Ok<>(mcpServerId));
                } catch (Exception e) {
                    if (!ignoreException) {
                        throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
                    }
                    logger.info("remove mcp server failed, id={}, reason={}", mcpServerId, e.getMessage());
                    results.add(new Error<>(e));
                }
            }
            return results;
        });
    }

    /**
     * 获取MCP工具
     *
     * @param name                 工具名称（可为null）
     * @param serverId             服务器ID（可为null）
     * @param serverName           服务器名称（可为null）
     * @param tags                 标签列表（可为null）
     * @param strategy             标签匹配策略
     * @param skipIfNotExists      是否跳过不存在的
     * @param ignoreException      是否忽略异常
     * @return 工具列表
     */
    public List<Object> getMcpTool(String name, String serverId, String serverName,
                                   List<String> tags, TagMatchStrategy strategy,
                                   boolean skipIfNotExists, boolean ignoreException) {
        Object[] found = innerGetServerIds(serverId, serverName, tags, strategy,
            skipIfNotExists, StatusCode.RESOURCE_MCP_TOOL_GET_ERROR);
        List<String> serverIds = (List<String>) found[0];
        boolean exactMatch = (Boolean) found[1];

        List<Object> results = new ArrayList<>();
        List<String> toolNames = name != null ? List.of(name) : null;

        for (String mcpServerId : serverIds) {
            try {
                resourceRegistry.tool().refreshToolServer(mcpServerId, true, false).join();
            } catch (Exception e) {
                if (!ignoreException) {
                    throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
                }
            }
            if (toolNames == null) {
                Object mcpTools = resourceRegistry.tool().getMcpTools(mcpServerId, null);
                if (mcpTools instanceof List<?> list) {
                    results.addAll(list);
                }
            } else {
                for (String toolName : toolNames) {
                    Object tool = resourceRegistry.tool().getMcpTool(toolName, mcpServerId, null);
                    if (exactMatch) {
                        results.add(tool);
                    } else if (tool != null) {
                        results.add(tool);
                    }
                }
            }
        }
        return results;
    }

    /**
     * 获取MCP工具信息
     *
     * @param name                 工具名称（可为null）
     * @param serverId             服务器ID（可为null）
     * @param serverName           服务器名称（可为null）
     * @param tags                 标签列表（可为null）
     * @param strategy             标签匹配策略
     * @param skipIfNotExists      是否跳过不存在的
     * @param ignoreException      是否忽略异常
     * @return ToolInfo列表
     */
    public List<ToolInfo> getMcpToolInfos(String name, String serverId, String serverName,
                                          List<String> tags, TagMatchStrategy strategy,
                                          boolean skipIfNotExists, boolean ignoreException) {
        Object[] found = innerGetServerIds(serverId, serverName, tags, strategy,
            skipIfNotExists, StatusCode.RESOURCE_MCP_TOOL_GET_ERROR);
        List<String> serverIds = (List<String>) found[0];
        boolean exactMatch = (Boolean) found[1];

        List<String> toolNames = name != null ? List.of(name) : null;
        List<ToolInfo> results = new ArrayList<>();

        for (String mcpServerId : serverIds) {
            try {
                resourceRegistry.tool().refreshToolServer(mcpServerId, true, false).join();
            } catch (Exception e) {
                if (!ignoreException) {
                    throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
                }
            }
            List<Object> toolIds = new ArrayList<>();
            if (toolNames == null) {
                Object allIds = resourceRegistry.tool().getMcpToolId(mcpServerId, null);
                if (allIds instanceof List<?> list) {
                    toolIds.addAll(list);
                }
            } else {
                for (String toolName : toolNames) {
                    Object toolId = resourceRegistry.tool().getMcpToolId(mcpServerId, toolName);
                    toolIds.add(toolId);
                }
            }
            for (Object toolIdObj : toolIds) {
                String toolId = toolIdObj != null ? toolIdObj.toString() : null;
                Object toolCard = toolId != null ? idToCard.get(toolId) : null;
                if (exactMatch) {
                    if (toolCard instanceof BaseCard bc) {
                        Object info = bc.toolInfo();
                        results.add(info instanceof ToolInfo ti ? ti : null);
                    } else {
                        results.add(null);
                    }
                } else if (toolCard instanceof BaseCard bc) {
                    Object info = bc.toolInfo();
                    if (info instanceof ToolInfo ti) {
                        results.add(ti);
                    }
                }
            }
        }
        return results;
    }

    // ==================== 标签操作 ====================

    /**
     * 列出所有标签
     *
     * @return 标签列表
     */
    public List<String> listTags() {
        return tagMgr.listTags();
    }

    /**
     * 检查标签是否存在
     *
     * @param tag 标签名称
     * @return 是否存在
     */
    public boolean hasTag(String tag) {
        innerValidateTag(tag);
        return tagMgr.hasTag(tag);
    }

    /**
     * 通过标签获取资源卡片
     *
     * @param tag 标签名称
     * @return 资源卡片列表
     */
    public List<Object> getResourceByTag(String tag) {
        innerValidateTag(tag);
        List<String> resourceIds = tagMgr.getTagResources(tag);
        if (resourceIds == null || resourceIds.isEmpty()) {
            return resourceIds != null ? Collections.emptyList() : null;
        }
        List<Object> result = new ArrayList<>();
        for (String id : resourceIds) {
            result.add(idToCard.get(id));
        }
        return result;
    }

    /**
     * 为资源添加标签
     *
     * @param resourceId 资源ID
     * @param tags       要添加的标签列表
     * @return Result对象，包含当前全部标签
     */
    public Result<?> addResourceTag(String resourceId, List<String> tags) {
        innerValidateResourceId(resourceId);
        innerValidateTag(tags);
        try {
            List<String> nowTags = tagMgr.tagResource(resourceId, tags);
            return new Ok<>(nowTags);
        } catch (Exception e) {
            return new Error<>(e);
        }
    }

    /**
     * 从资源移除指定标签
     *
     * @param resourceId       资源ID
     * @param tags             要移除的标签列表
     * @param skipIfNotExists  是否跳过不存在的标签
     * @return Result对象，包含剩余标签
     */
    public Result<?> removeResourceTag(String resourceId, List<String> tags, boolean skipIfNotExists) {
        innerValidateResourceId(resourceId);
        innerValidateTag(tags);
        try {
            List<String> remainTags = tagMgr.removeResourceTags(resourceId, tags, skipIfNotExists);
            return new Ok<>(remainTags);
        } catch (Exception e) {
            return new Error<>(e);
        }
    }

    /**
     * 替换资源的所有标签
     *
     * @param resourceId 资源ID
     * @param tags       新标签列表
     * @return Result对象，包含新标签列表
     */
    public Result<?> updateResourceTag(String resourceId, List<String> tags) {
        innerValidateResourceId(resourceId);
        innerValidateTag(tags);
        try {
            List<String> results = tagMgr.updateResourceTags(resourceId, tags, TagUpdateStrategy.REPLACE);
            return new Ok<>(results);
        } catch (Exception e) {
            return new Error<>(e);
        }
    }

    /**
     * 获取资源的标签列表
     *
     * @param resourceId 资源ID
     * @return 标签列表，不存在返回null
     */
    public List<String> getResourceTag(String resourceId) {
        List<String> resourceTag = tagMgr.getResourceTags(resourceId);
        return (resourceTag != null && !resourceTag.isEmpty()) ? resourceTag : null;
    }

    /**
     * 判断资源是否拥有指定标签
     *
     * @param resourceId 资源ID
     * @param tag        标签名称
     * @return 是否拥有
     */
    public boolean resourceHasTag(String resourceId, String tag) {
        innerValidateTag(tag);
        innerValidateResourceId(resourceId);
        return tagMgr.hasResourceTag(resourceId, tag);
    }

    /**
     * 删除标签并释放关联资源
     *
     * @param tags              标签列表
     * @param skipIfNotExists   是否跳过不存在的标签
     * @return 结果列表
     */
    public List<Result<?>> removeTag(List<String> tags, boolean skipIfNotExists) {
        innerValidateTag(tags);
        List<Result<?>> results = new ArrayList<>();
        for (String singleTag : tags) {
            List<String> resourcesToRemove = tagMgr.removeTag(singleTag, skipIfNotExists);
            for (String resourceId : resourcesToRemove) {
                resourceRegistry.removeById(resourceId);
            }
            logger.info("remove tag succeed, tag={}, release resource={}", singleTag, resourcesToRemove);
            results.add(new Ok<>(singleTag));
        }
        return results;
    }

    // ==================== MCP服务器验证 ====================

    /**
     * 验证MCP服务器配置（公共静态方法，用于外部验证）
     *
     * @param serverConfig 服务器配置
     * @throws BaseError 如果配置无效
     */
    public static void validateServerConfig(Object serverConfig) {
        if (serverConfig == null) {
            throw ErrorBuilder.build(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID,
                "server_config(s) is None or empty");
        }
    }

    /**
     * 验证过期时间
     *
     * @param expiryTime 过期时间
     * @throws BaseError 如果过期时间无效
     */
    public void validateExpiryTime(double expiryTime) {
        if (expiryTime <= 0) {
            throw ErrorBuilder.build(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID,
                "expire time <= 0");
        }
    }

    // ==================== Release ====================

    /**
     * 释放所有资源
     *
     * @return 完成的CompletableFuture
     */
    public CompletableFuture<Void> release() {
        return resourceRegistry.tool().release();
    }

    // ==================== 内部方法 ====================

    /**
     * 内部添加资源方法
     */
    @SuppressWarnings("unchecked")
    private Result<?> innerAddResource(String resourceId, Object resource, Object resourceCard,
                                       List<String> tags, String resourceType) {
        try {
            if (tagMgr.hasResource(resourceId)) {
                throw ErrorBuilder.build(StatusCode.RESOURCE_ADD_ERROR, "resource already exist");
            }
            // 添加到对应的管理器
            switch (resourceType) {
                case "agent" -> {
                    if (resource instanceof RemoteAgent remoteAgent) {
                        resourceRegistry.agent().addRemoteAgent(resourceId, remoteAgent);
                    } else {
                        resourceRegistry.agent().addAgent(resourceId, (Supplier) resource);
                    }
                }
                case "workflow" -> resourceRegistry.workflow().addWorkflow(resourceId, (Supplier) resource);
                case "group" -> resourceRegistry.agentGroup().addAgentGroup(resourceId, (Supplier) resource);
                case "model" -> resourceRegistry.model().addModel(resourceId, (Supplier) resource);
                case "tool" -> resourceRegistry.tool().addTool(resourceId, resource);
                case "prompt" -> resourceRegistry.prompt().addPrompt(resourceId, resource);
                case "sys_operation" -> resourceRegistry.sysOperation().addSysOperation(resourceId, resource);
                default -> { /* 未知类型，忽略 */ }
            }
            // 存储卡片信息
            if (resourceCard != null) {
                idToCard.put(resourceId, resourceCard);
            }
            // 标签
            tagMgr.tagResource(resourceId, tags != null ? (Object) tags : Tag.GLOBAL);
            if (resourceCard != null) {
                logger.info("add resource succeed, id={}, type={}, card={}", resourceId, resourceType, resourceCard);
            } else {
                logger.info("add resource succeed, id={}, type={}", resourceId, resourceType);
            }
            return new Ok<>(resourceCard != null ? resourceCard : resourceId);
        } catch (Exception e) {
            if (resourceCard != null) {
                logger.error("add resource failed, id={}, type={}, card={}, reason={}",
                    resourceId, resourceType, resourceCard, e.getMessage());
            } else {
                logger.info("add resource failed, id={}, type={}, reason={}", resourceId, resourceType, e.getMessage());
            }
            return new Error<>(e);
        }
    }

    /**
     * 内部查找资源ID（通过ID或标签）
     *
     * @return Object[]{List<String> ids, Boolean exactMatch}
     */
    private Object[] innerFindResourceIds(String resourceId, List<String> tags, TagMatchStrategy strategy) {
        List<String> idsToGet = null;
        boolean exactMatch = false;

        if (resourceId != null) {
            idsToGet = List.of(resourceId);
            exactMatch = true;
        }

        if (idsToGet == null || idsToGet.isEmpty()) {
            idsToGet = tagMgr.findResourcesByTags(
                tags != null ? (Object) tags : Tag.GLOBAL,
                strategy != null ? strategy : TagMatchStrategy.ALL,
                false
            );
            exactMatch = false;
        }

        return new Object[]{idsToGet, exactMatch};
    }

    /**
     * 通过Provider获取资源（用于Agent/Workflow/AgentGroup/Model）
     */
    private CompletableFuture<?> innerGetResourceByProvider(String resourceId, List<String> tags,
                                                            TagMatchStrategy strategy, String resourceType) {
        Object[] findResult = innerFindResourceIds(resourceId, tags, strategy);
        List<String> idsToGet = (List<String>) findResult[0];
        boolean exactMatch = (Boolean) findResult[1];

        if (idsToGet == null || idsToGet.isEmpty()) {
            return CompletableFuture.completedFuture(exactMatch ? null : new ArrayList<>());
        }

        List<Object> results = new ArrayList<>();
        for (String getId : idsToGet) {
            Object resource = null;
            try {
                if (tagMgr.hasResource(getId)) {
                    CompletableFuture<?> future = switch (resourceType) {
                        case "agent" -> resourceRegistry.agent().getAgent(getId);
                        case "workflow" -> resourceRegistry.workflow().getRawWorkflow(getId);
                        case "group" -> resourceRegistry.agentGroup().getAgentGroup(getId);
                        case "model" -> resourceRegistry.model().getRawModel(getId);
                        default -> CompletableFuture.completedFuture(null);
                    };
                    resource = future.join();
                }
            } catch (Exception e) {
                // 忽略异常
            }
            if (resource != null || exactMatch) {
                results.add(resource);
            }
        }

        if (results.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.completedFuture(
            results.size() == 1 && exactMatch ? results.get(0) : results
        );
    }

    /**
     * 直接获取资源（用于Tool/Prompt/SysOperation）
     */
    private Object innerGetResource(String resourceId, List<String> tags,
                                    TagMatchStrategy strategy, String resourceType) {
        Object[] findResult = innerFindResourceIds(resourceId, tags, strategy);
        List<String> idsToGet = (List<String>) findResult[0];
        boolean exactMatch = (Boolean) findResult[1];

        if (idsToGet == null || idsToGet.isEmpty()) {
            return null;
        }

        List<Object> results = new ArrayList<>();
        for (String getId : idsToGet) {
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
            } catch (Exception e) {
                // 忽略异常
            }
            if (resource != null || exactMatch) {
                results.add(resource);
            }
        }

        if (results.isEmpty()) {
            return null;
        }

        return results.size() == 1 && exactMatch ? results.get(0) : results;
    }

    /**
     * 内部移除资源方法
     */
    private List<Result<?>> innerRemoveResources(List<String> resourceIds, List<String> tags,
                                                  TagMatchStrategy strategy, boolean skipIfNotExists,
                                                  String resourceType) {
        List<String> idsToRemove = new ArrayList<>();
        boolean removeByTag = false;

        if (resourceIds != null && !resourceIds.isEmpty()) {
            idsToRemove.addAll(resourceIds);
        }

        if (idsToRemove.isEmpty()) {
            if (tags != null) {
                innerValidateTag(tags);
            }
            idsToRemove = tagMgr.findResourcesByTags(
                tags != null ? (Object) tags : Tag.GLOBAL,
                strategy != null ? strategy : TagMatchStrategy.ALL,
                skipIfNotExists
            );
            removeByTag = true;
            if (idsToRemove == null || idsToRemove.isEmpty()) {
                return new ArrayList<>();
            }
        }

        List<Result<?>> results = new ArrayList<>();
        for (String removeId : idsToRemove) {
            Exception error = null;
            try {
                tagMgr.removeResource(removeId);
                switch (resourceType) {
                    case "agent" -> resourceRegistry.agent().removeAgent(removeId);
                    case "workflow" -> resourceRegistry.workflow().removeWorkflow(removeId);
                    case "group" -> resourceRegistry.agentGroup().removeAgentGroup(removeId);
                    case "model" -> resourceRegistry.model().removeModel(removeId);
                    case "tool" -> resourceRegistry.tool().removeTool(removeId);
                    case "prompt" -> resourceRegistry.prompt().removePrompt(removeId);
                    case "sys_operation" -> resourceRegistry.sysOperation().removeSysOperation(removeId);
                    default -> { /* 未知类型，忽略 */ }
                }
            } catch (Exception e) {
                if (!removeByTag) {
                    error = e;
                }
            }

            Object removedCard = idToCard.remove(removeId);

            if (error != null) {
                logger.error("remove resource error, id={}, type={}, card={}, reason={}",
                    removeId, resourceType, removedCard, error.getMessage());
                results.add(new Error<>(error));
            } else if ("tool".equals(resourceType) || "prompt".equals(resourceType)) {
                results.add(new Ok<>(removeId));
            } else {
                if (removedCard != null || !removeByTag) {
                    results.add(new Ok<>(removedCard));
                }
            }

            if (error == null) {
                if (removedCard != null) {
                    logger.info("remove resource succeed, id={}, type={}, card={}", removeId, resourceType, removedCard);
                } else {
                    logger.info("remove resource succeed, id={}, type={}", removeId, resourceType);
                }
            }
        }

        return results;
    }

    /**
     * 内部获取MCP服务器ID列表
     *
     * @return Object[]{List<String> serverIds, Boolean exactMatch}
     */
    private Object[] innerGetServerIds(String serverId, String serverName,
                                       List<String> tags, TagMatchStrategy strategy,
                                       boolean skipIfNotExists, StatusCode errorCode) {
        List<String> serverIds = new ArrayList<>();
        boolean exactMatch = false;

        if (serverId != null) {
            if (serverId.isEmpty()) {
                throw ErrorBuilder.build(errorCode, "server_id is empty");
            }
            serverIds.add(serverId);
            exactMatch = true;
        } else if (serverName != null) {
            if (serverName.isEmpty()) {
                throw ErrorBuilder.build(errorCode, "server_name is empty");
            }
            List<String> names = List.of(serverName);
            for (String sName : names) {
                serverIds.addAll(resourceRegistry.tool().getMcpServerIds(sName));
            }
        } else {
            serverIds.addAll(tagMgr.findResourcesByTags(
                tags != null ? (Object) tags : Tag.GLOBAL,
                strategy != null ? strategy : TagMatchStrategy.ALL,
                skipIfNotExists
            ));
        }

        return new Object[]{serverIds, exactMatch};
    }

    // ==================== 验证方法 ====================

    /**
     * 验证资源卡片ID
     */
    private static void innerValidateResourceCard(String cardId) {
        if (cardId == null) {
            throw ErrorBuilder.build(StatusCode.RESOURCE_CARD_VALUE_INVALID, "card is None");
        }
        if (cardId.isEmpty()) {
            throw ErrorBuilder.build(StatusCode.RESOURCE_CARD_VALUE_INVALID, "card id value is empty value");
        }
    }

    /**
     * 验证Provider
     */
    private static void innerValidateProvider(Object card, Supplier<?> provider) {
        if (provider == null) {
            throw ErrorBuilder.build(StatusCode.RESOURCE_PROVIDER_INVALID, "provider is None");
        }
    }

    /**
     * 验证资源ID
     */
    private static void innerValidateResourceId(String resourceId) {
        if (resourceId == null || resourceId.isEmpty()) {
            throw ErrorBuilder.build(StatusCode.RESOURCE_ID_VALUE_INVALID, "is None or empty");
        }
    }

    /**
     * 验证单个标签
     */
    private static void innerValidateTag(String tag) {
        if (tag == null || tag.isEmpty()) {
            throw ErrorBuilder.build(StatusCode.RESOURCE_TAG_VALUE_INVALID, "is None or empty value");
        }
    }

    /**
     * 验证标签列表
     */
    private static void innerValidateTag(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            throw ErrorBuilder.build(StatusCode.RESOURCE_TAG_VALUE_INVALID, "is None or empty value");
        }
        if (tags.contains(Tag.GLOBAL) && tags.size() > 1) {
            throw ErrorBuilder.build(StatusCode.RESOURCE_TAG_VALUE_INVALID,
                "The GLOBAL tag already exists and cannot be assigned additional tags.");
        }
        Set<String> seen = new HashSet<>();
        for (String tag : tags) {
            if (tag == null || tag.isEmpty()) {
                throw ErrorBuilder.build(StatusCode.RESOURCE_TAG_VALUE_INVALID, "has None or empty value");
            }
            if (!seen.add(tag)) {
                throw ErrorBuilder.build(StatusCode.RESOURCE_TAG_VALUE_INVALID,
                    "has duplicate tag '" + tag + "' item");
            }
        }
    }

    /**
     * 验证MCP服务器配置（内部方法，单个配置）
     */
    private static void innerValidateServerConfig(McpServerConfig serverConfig) {
        if (serverConfig == null) {
            throw ErrorBuilder.build(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID,
                "server_config(s) is None or empty");
        }
        if (serverConfig.getServerId() == null || serverConfig.getServerId().isEmpty()) {
            throw ErrorBuilder.build(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID,
                "server_config's server_id is None or empty");
        }
    }

    /**
     * 验证MCP服务器配置列表
     */
    private static void innerValidateServerConfigs(List<McpServerConfig> serverConfigs) {
        if (serverConfigs == null || serverConfigs.isEmpty()) {
            throw ErrorBuilder.build(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID,
                "server_config(s) is None or empty");
        }
        Set<String> ids = new HashSet<>();
        for (McpServerConfig config : serverConfigs) {
            if (config == null) {
                throw ErrorBuilder.build(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID,
                    "server config list has invalid item");
            }
            if (config.getServerId() == null || config.getServerId().isEmpty()) {
                throw ErrorBuilder.build(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID,
                    "server_config's server_id is None or empty");
            }
            if (!ids.add(config.getServerId())) {
                throw ErrorBuilder.build(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID,
                    "server config list has duplicate server_id");
            }
        }
    }

    /**
     * 获取卡片类型字符串
     *
     * @param card 卡片对象
     * @return 类型字符串，未知返回null
     */
    private static String getCardType(Object card) {
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
            case "SysOperationCard" -> "sys_operation";
            default -> null;
        };
    }

    // ==================== 内部类 ====================

    /**
     * Agent批量添加条目
     */
    public static class AgentEntry {
        private final String id;
        private final Supplier<?> provider;

        public AgentEntry(String id, Supplier<?> provider) {
            this.id = id;
            this.provider = provider;
        }

        public String getId() {
            return id;
        }

        public Supplier<?> getProvider() {
            return provider;
        }
    }

    /**
     * Workflow批量添加条目
     */
    public static class WorkflowEntry {
        private final String id;
        private final Supplier<?> provider;

        public WorkflowEntry(String id, Supplier<?> provider) {
            this.id = id;
            this.provider = provider;
        }

        public String getId() {
            return id;
        }

        public Supplier<?> getProvider() {
            return provider;
        }
    }

    /**
     * Model批量添加条目
     */
    public static class ModelEntry {
        private final String modelId;
        private final Supplier<?> provider;

        public ModelEntry(String modelId, Supplier<?> provider) {
            this.modelId = modelId;
            this.provider = provider;
        }

        public String getModelId() {
            return modelId;
        }

        public Supplier<?> getProvider() {
            return provider;
        }
    }

    /**
     * Prompt批量添加条目
     */
    public static class PromptEntry {
        private final String promptId;
        private final Object template;

        public PromptEntry(String promptId, Object template) {
            this.promptId = promptId;
            this.template = template;
        }

        public String getPromptId() {
            return promptId;
        }

        public Object getTemplate() {
            return template;
        }
    }
}
