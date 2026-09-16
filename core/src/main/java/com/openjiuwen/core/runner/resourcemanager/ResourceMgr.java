/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.common.exception.BaseError;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Resource Manager facade for Model, Workflow, Prompt, Tool, Agent, AgentGroup, SysOperation.
 * <p>
 * Mirrors Python's {@code ResourceMgr} in {@code resources_manager/resource_manager.py}.
 * 
 * @since 0.1.7
 */
public class ResourceMgr {
    private static final Logger logger = LoggerFactory.getLogger(ResourceMgr.class);

    /**
     * ResourceRegistry.
     * 
     * @since 0.1.7
     */
    private final ResourceRegistry resourceRegistry = new ResourceRegistry();

    /**
     * TagMgr.
     * 
     * @since 0.1.7
     */
    private final TagMgr tagMgr = new TagMgr();

    /**
     * ConcurrentHashMap<>: written from agent execution paths
     * (contextReloader registration, harness tool registration) that run
     * concurrently under task-level parallelism; iteration must be weakly
     * consistent instead of fail-fast.
     *
     * @since 0.1.7
     */
    private final ConcurrentHashMap<String, BaseCard> idToCard = new ConcurrentHashMap<>();

    /**
     * ReentrantLock serializing the cross-structure write sequences
     * (hasResource probe, registry write, idToCard put, tagResource) of
     * innerAddResource and innerRemoveResources so registration and removal
     * sequences never interleave in each other's intermediate states. Read
     * paths never take this lock (weakly consistent container reads); the
     * MCP read-time refresh path falls under the ToolMgr per-server slot
     * lock instead and may briefly block during a registration, bounded
     * by the discovery RPC.
     *
     * @since 0.1.16
     */
    private final ReentrantLock structureLock = new ReentrantLock();

    /**
     * Ownership table: resource id to the set of owner tokens that claimed
     * it. Entries with equivalent ability declarations are shared, and a
     * resource entry is removed only when its last owner releases it.
     *
     * @since 0.1.16
     */
    private final ConcurrentHashMap<String, Set<String>> idToOwners = new ConcurrentHashMap<>();

    // ========== Agent Group ==========

    /**
     * addAgentGroup.
     * 
     * @param card card
     * @param agentGroup agentGroup
     * @param tag tag
     * @return the result
     * @since 0.1.7
     */
    public Result<GroupCard> addAgentGroup(GroupCard card, Supplier<Object> agentGroup, Object tag) {
        validateResourceCard(card, "group", GroupCard.class);
        validateResourceId(card.getId(), "group");
        validateProvider(agentGroup, "group");
        if (tag != null) {
            validateTag(tag);
        }
        return innerAddResource(new ResourceRegistration(card.getId(), agentGroup, card, tag, "group"));
    }

    /**
     * removeAgentGroup.
     * 
     * @param groupId groupId
     * @param tag tag
     * @param tagMatchStrategy tagMatchStrategy
     * @param shouldSkipMissingTag shouldSkipMissingTag
     * @return the result
     * @since 0.1.7
     */
    public List<Result<GroupCard>> removeAgentGroup(Object groupId, Object tag, TagMatchStrategy tagMatchStrategy,
            boolean shouldSkipMissingTag) {
        return innerRemoveResources(groupId, tag, tagMatchStrategy, shouldSkipMissingTag, "group");
    }

    /**
     * getAgentGroup.
     * 
     * @param groupId groupId
     * @param tag tag
     * @param tagMatchStrategy tagMatchStrategy
     * @return the result
     * @since 0.1.7
     */
    public Object getAgentGroup(String groupId, Object tag, TagMatchStrategy tagMatchStrategy) {
        return innerGetResourcesByProvider(groupId, tag, tagMatchStrategy, "group");
    }

    /**
     * addAgent.
     * 
     * @param card card
     * @param agent agent
     * @param tag tag
     * @return the result
     * @since 0.1.7
     */
    public Result<AgentCard> addAgent(AgentCard card, Supplier<Object> agent, Object tag) {
        validateResourceCard(card, "agent", AgentCard.class);
        validateResourceId(card.getId(), "agent");
        validateProvider(agent, "agent");
        if (tag != null) {
            validateTag(tag);
        }
        return innerAddResource(new ResourceRegistration(card.getId(), agent, card, tag, "agent"));
    }

    /**
     * addAgents.
     * 
     * @param agents agents
     * @param tag tag
     * @return the result
     * @since 0.1.7
     */
    public List<Result<AgentCard>> addAgents(List<AgentEntry> agents, Object tag) {
        validateAgentEntries(agents);
        if (tag != null) {
            validateTag(tag);
        }
        List<Result<AgentCard>> results = new ArrayList<>();
        for (AgentEntry entry : agents) {
            results.add(innerAddResource(
                    new ResourceRegistration(entry.card().getId(), entry.provider(), entry.card(), tag,
                            "agent")));
        }
        return results;
    }

    /**
     * removeAgent.
     * 
     * @param agentId agentId
     * @param tag tag
     * @param tagMatchStrategy tagMatchStrategy
     * @param shouldSkipMissingTag shouldSkipMissingTag
     * @return the result
     * @since 0.1.7
     */
    public Object removeAgent(Object agentId, Object tag, TagMatchStrategy tagMatchStrategy,
            boolean shouldSkipMissingTag) {
        return innerRemoveResources(agentId, tag, tagMatchStrategy, shouldSkipMissingTag, "agent");
    }

    /**
     * getAgent.
     * 
     * @param agentId agentId
     * @param tag tag
     * @param tagMatchStrategy tagMatchStrategy
     * @return the result
     * @since 0.1.7
     */
    public Object getAgent(String agentId, Object tag, TagMatchStrategy tagMatchStrategy) {
        return innerGetResourcesByProvider(agentId, tag, tagMatchStrategy, "agent");
    }

    /**
     * getAgent.
     * 
     * @param agentId agentId
     * @return the result
     * @since 0.1.7
     */
    public Object getAgent(String agentId) {
        return innerGetResourcesByProvider(agentId, null, TagMatchStrategy.ALL, "agent");
    }

    /**
     * addWorkflow.
     * 
     * @param card card
     * @param workflow workflow
     * @param tag tag
     * @return the result
     * @since 0.1.7
     */
    public Result<WorkflowCard> addWorkflow(WorkflowCard card, Supplier<Workflow> workflow, Object tag) {
        validateResourceCard(card, "workflow", WorkflowCard.class);
        validateResourceId(card.getId(), "workflow");
        validateProvider(workflow, "workflow");
        if (tag != null) {
            validateTag(tag);
        }
        return innerAddResource(new ResourceRegistration(card.getId(), workflow, card, tag, "workflow"));
    }

    /**
     * addWorkflows.
     * 
     * @param workflows workflows
     * @param tag tag
     * @return the result
     * @since 0.1.7
     */
    public List<Result<WorkflowCard>> addWorkflows(List<WorkflowEntry> workflows, Object tag) {
        validateWorkflowEntries(workflows);
        if (tag != null) {
            validateTag(tag);
        }
        List<Result<WorkflowCard>> results = new ArrayList<>();
        for (WorkflowEntry entry : workflows) {
            results.add(innerAddResource(
                    new ResourceRegistration(entry.card().getId(), entry.provider(), entry.card(), tag,
                            "workflow")));
        }
        return results;
    }

    /**
     * removeWorkflow.
     * 
     * @param workflowId workflowId
     * @param tag tag
     * @param tagMatchStrategy tagMatchStrategy
     * @param shouldSkipMissingTag shouldSkipMissingTag
     * @return the result
     * @since 0.1.7
     */
    public Object removeWorkflow(Object workflowId, Object tag, TagMatchStrategy tagMatchStrategy,
            boolean shouldSkipMissingTag) {
        return innerRemoveResources(workflowId, tag, tagMatchStrategy, shouldSkipMissingTag, "workflow");
    }

    /**
     * getWorkflow.
     * 
     * @param workflowId workflowId
     * @param tag tag
     * @param tagMatchStrategy tagMatchStrategy
     * @return the result
     * @since 0.1.7
     */
    public Object getWorkflow(String workflowId, Object tag, TagMatchStrategy tagMatchStrategy) {
        Object workflow = innerGetResourcesByProvider(workflowId, tag, tagMatchStrategy, "workflow");
        if (workflow != null) {
            return workflow;
        }
        return findWorkflowByAlternateId(workflowId);
    }

    /**
     * getWorkflow.
     * 
     * @param workflowId workflowId
     * @return the result
     * @since 0.1.7
     */
    public Object getWorkflow(String workflowId) {
        Object workflow = innerGetResourcesByProvider(workflowId, null, TagMatchStrategy.ALL, "workflow");
        if (workflow != null) {
            return workflow;
        }
        return findWorkflowByAlternateId(workflowId);
    }

    /**
     * addTool.
     *
     * <p>Delegates to {@link #addTool(Tool, Object, Object)} with owner
     * defaulting to the tag, which preserves the legacy single-agent
     * semantics.</p>
     *
     * @param tool tool
     * @param tag tag
     * @return the result
     * @since 0.1.7
     */
    public Result<ToolCard> addTool(Tool tool, Object tag) {
        return addTool(tool, tag, tag);
    }

    /**
     * addTool.
     *
     * <p>Idempotent three-state contract: a new id is registered with the
     * caller as its only owner; an existing id with an equivalent ability
     * declaration is reused and the owner is added to the entry's owner set
     * (same-owner re-registration is a no-op); an existing id with a
     * different definition fails with RESOURCE_ADD_ERROR (definition
     * conflict). A null owner degrades to the tag so legacy callers keep
     * working; new callers should always pass a non-null owner token.</p>
     *
     * <p>Single agent instances are not thread-safe for concurrent
     * execution; ownership claiming happens under the manager's structure
     * lock and is safe across concurrently initializing instances.</p>
     *
     * @param tool tool
     * @param tag tag
     * @param owner owner token claiming the entry (null degrades to tag)
     * @return the result
     * @since 0.1.16
     */
    public Result<ToolCard> addTool(Tool tool, Object tag, Object owner) {
        validateTool(tool);
        if (tag != null) {
            validateTag(tag);
        }
        Object ownerKey = owner != null ? owner : tag;
        return innerAddResource(new ResourceRegistration(tool.getCard().getId(), tool, tool.getCard(), tag, "tool",
                    ownerKey));
    }

    /**
     * addTool.
     *
     * <p>Refresh variant: optionally removes an existing entry first, then
     * registers with owner defaulting to the tag (see
     * {@link #addTool(Tool, Object, Object)}).</p>
     *
     * @param tool tool
     * @param tag tag
     * @param refresh refresh
     * @return the result
     * @since 0.1.7
     */
    public Result<ToolCard> addTool(Tool tool, Object tag, boolean refresh) {
        validateTool(tool);
        if (tag != null) {
            validateTag(tag);
        }
        if (refresh) {
            refreshExistingResourceIfNeeded(tool.getCard().getId(), tag);
        }
        return addTool(tool, tag, tag);
    }

    /**
     * addTools.
     *
     * @param tools tools
     * @param tag tag
     * @return the result
     * @since 0.1.7
     */
    public List<Result<ToolCard>> addTools(List<Tool> tools, Object tag) {
        return addTools(tools, tag, tag);
    }

    /**
     * addTools with explicit owner tokens (see
     * {@link #addTool(Tool, Object, Object)} for the ownership contract).
     *
     * @param tools tools
     * @param tag tag
     * @param owner owner token claiming each entry (null degrades to tag)
     * @return the result
     * @since 0.1.16
     */
    public List<Result<ToolCard>> addTools(List<Tool> tools, Object tag, Object owner) {
        validateTools(tools);
        if (tag != null) {
            validateTag(tag);
        }
        Object ownerKey = owner != null ? owner : tag;
        List<Result<ToolCard>> results = new ArrayList<>();
        for (Tool tool : tools) {
            results.add(innerAddResource(
                    new ResourceRegistration(tool.getCard().getId(), tool, tool.getCard(), tag, "tool", ownerKey)));
        }
        return results;
    }

    /**
     * addTools.
     *
     * @param tools tools
     * @param tag tag
     * @param refresh refresh
     * @return the result
     * @since 0.1.7
     */
    public List<Result<ToolCard>> addTools(List<Tool> tools, Object tag, boolean refresh) {
        validateTools(tools);
        if (tag != null) {
            validateTag(tag);
        }
        List<Result<ToolCard>> results = new ArrayList<>();
        for (Tool tool : tools) {
            if (refresh) {
                refreshExistingResourceIfNeeded(tool.getCard().getId(), tag);
            }
            results.add(innerAddResource(
                    new ResourceRegistration(tool.getCard().getId(), tool, tool.getCard(), tag, "tool", tag)));
        }
        return results;
    }

    /**
     * getTool.
     * 
     * @param toolId toolId
     * @param tag tag
     * @param tagMatchStrategy tagMatchStrategy
     * @return the result
     * @since 0.1.7
     */
    public Object getTool(String toolId, Object tag, TagMatchStrategy tagMatchStrategy) {
        return innerGetResources(toolId, tag, tagMatchStrategy, "tool");
    }

    /**
     * getTool.
     * 
     * @param toolId toolId
     * @return the result
     * @since 0.1.7
     */
    public Object getTool(String toolId) {
        return innerGetResources(toolId, null, TagMatchStrategy.ALL, "tool");
    }

    /**
     * removeTool.
     * 
     * @param toolId toolId
     * @param tag tag
     * @param tagMatchStrategy tagMatchStrategy
     * @param shouldSkipMissingTag shouldSkipMissingTag
     * @return the result
     * @since 0.1.7
     */
    public Object removeTool(Object toolId, Object tag, TagMatchStrategy tagMatchStrategy,
            boolean shouldSkipMissingTag) {
        return innerRemoveResources(toolId, tag, tagMatchStrategy, shouldSkipMissingTag, "tool");
    }

    /**
     * addModel.
     * 
     * @param modelId modelId
     * @param model model
     * @param tag tag
     * @return the result
     * @since 0.1.7
     */
    public Result<String> addModel(String modelId, Supplier<Model> model, Object tag) {
        validateResourceId(modelId, "model");
        validateProvider(model, "model");
        if (tag != null) {
            validateTag(tag);
        }
        return innerAddResource(new ResourceRegistration(modelId, model, null, tag, "model"));
    }

    /**
     * addModels.
     * 
     * @param models models
     * @param tag tag
     * @return the result
     * @since 0.1.7
     */
    public List<Result<String>> addModels(List<ModelEntry> models, Object tag) {
        if (models == null || models.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_PROVIDER_INVALID, "resource_type", "model", "reason",
                    "cannot be empty");
        }
        if (tag != null) {
            validateTag(tag);
        }
        List<Result<String>> results = new ArrayList<>();
        for (ModelEntry entry : models) {
            results.add(innerAddResource(new ResourceRegistration(entry.id(), entry.provider(), null, tag, "model")));
        }
        return results;
    }

    /**
     * removeModel.
     * 
     * @param modelId modelId
     * @param tag tag
     * @param tagMatchStrategy tagMatchStrategy
     * @param shouldSkipMissingTag shouldSkipMissingTag
     * @return the result
     * @since 0.1.7
     */
    public Object removeModel(Object modelId, Object tag, TagMatchStrategy tagMatchStrategy,
            boolean shouldSkipMissingTag) {
        return innerRemoveResources(modelId, tag, tagMatchStrategy, shouldSkipMissingTag, "model");
    }

    /**
     * getModel.
     * 
     * @param modelId modelId
     * @param tag tag
     * @param tagMatchStrategy tagMatchStrategy
     * @return the result
     * @since 0.1.7
     */
    public Object getModel(String modelId, Object tag, TagMatchStrategy tagMatchStrategy) {
        return innerGetResourcesByProvider(modelId, tag, tagMatchStrategy, "model");
    }

    /**
     * getModel.
     * 
     * @param modelId modelId
     * @return the result
     * @since 0.1.7
     */
    public Object getModel(String modelId) {
        return innerGetResourcesByProvider(modelId, null, TagMatchStrategy.ALL, "model");
    }

    /**
     * addPrompt.
     * 
     * @param promptId promptId
     * @param template template
     * @param tag tag
     * @return the result
     * @since 0.1.7
     */
    public Result<String> addPrompt(String promptId, PromptTemplate template, Object tag) {
        validateResourceId(promptId, "prompt");
        validateResource(template, "prompt", PromptTemplate.class);
        if (tag != null) {
            validateTag(tag);
        }
        return innerAddResource(new ResourceRegistration(promptId, template, null, tag, "prompt"));
    }

    /**
     * addPrompts.
     * 
     * @param prompts prompts
     * @param tag tag
     * @return the result
     * @since 0.1.7
     */
    public List<Result<String>> addPrompts(List<PromptEntry> prompts, Object tag) {
        if (prompts == null || prompts.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_VALUE_INVALID, "resource_type", "prompt", "reason",
                    "prompt list cannot be empty");
        }
        if (tag != null) {
            validateTag(tag);
        }
        List<Result<String>> results = new ArrayList<>();
        for (PromptEntry entry : prompts) {
            results.add(innerAddResource(
                    new ResourceRegistration(entry.id(), entry.template(), null, tag, "prompt")));
        }
        return results;
    }

    /**
     * removePrompt.
     * 
     * @param promptId promptId
     * @param tag tag
     * @param tagMatchStrategy tagMatchStrategy
     * @param shouldSkipMissingTag shouldSkipMissingTag
     * @return the result
     * @since 0.1.7
     */
    public Object removePrompt(Object promptId, Object tag, TagMatchStrategy tagMatchStrategy,
            boolean shouldSkipMissingTag) {
        return innerRemoveResources(promptId, tag, tagMatchStrategy, shouldSkipMissingTag, "prompt");
    }

    /**
     * getPrompt.
     * 
     * @param promptId promptId
     * @param tag tag
     * @param tagMatchStrategy tagMatchStrategy
     * @return the result
     * @since 0.1.7
     */
    public Object getPrompt(String promptId, Object tag, TagMatchStrategy tagMatchStrategy) {
        return innerGetResources(promptId, tag, tagMatchStrategy, "prompt");
    }

    /**
     * getPrompt.
     * 
     * @param promptId promptId
     * @return the result
     * @since 0.1.7
     */
    public Object getPrompt(String promptId) {
        return innerGetResources(promptId, null, TagMatchStrategy.ALL, "prompt");
    }

    /**
     * addSysOperation.
     *
     * <p>Delegates to {@link #addSysOperation(SysOperationCard, Object, Object)}
     * with owner defaulting to the tag.</p>
     *
     * @param card card
     * @param tag tag
     * @return the result
     * @since 0.1.7
     */
    public Result<SysOperationCard> addSysOperation(SysOperationCard card, Object tag) {
        return addSysOperation(card, tag, tag);
    }

    /**
     * addSysOperation with an explicit owner token.
     *
     * <p>Follows the same idempotent three-state contract as
     * {@link #addTool(Tool, Object, Object)}: equivalent declarations are
     * reused with the owner added to the entry's owner set (resource-bound
     * fields such as the work directory do not participate in the
     * equivalence check), while conflicting definitions fail visibly. On
     * reuse the existing instance stays registered and its bound tools are
     * not re-registered, but the joining owner claims the existing bound
     * tools so per-owner release stays residue-free.</p>
     *
     * @param card card
     * @param tag tag
     * @param owner owner token claiming the entry (null degrades to tag)
     * @return the result
     * @since 0.1.16
     */
    public Result<SysOperationCard> addSysOperation(SysOperationCard card, Object tag, Object owner) {
        validateResourceCard(card, "sys_operation", SysOperationCard.class);
        if (tag != null) {
            validateTag(tag);
        }
        SysOperation instance = new SysOperation(card);
        Object ownerKey = owner != null ? owner : tag;
        // One structureLock domain covers the entry registration and the
        // bound-tool registration/claim (reentrant nesting): a concurrent
        // equivalent registration must never observe the entry with the
        // tool association still empty, claim nothing, and later lose the
        // bound tools when the first registrant releases.
        structureLock.lock();
        try {
            Result<SysOperationCard> res = innerAddResource(
                    new ResourceRegistration(card.getId(), instance, card, tag, "sys_operation", ownerKey));
            if (res.isOk() && res.getValue() == card) {
                registerSysOperationTools(card, instance, tag, ownerKey);
            } else if (res.isOk()) {
                claimSysOperationTools(card.getId(), ownerKey);
            }
            return res;
        } finally {
            structureLock.unlock();
        }
    }

    /**
     * removeSysOperation.
     * 
     * @param sysOperationId sysOperationId
     * @param tag tag
     * @param tagMatchStrategy tagMatchStrategy
     * @param shouldSkipMissingTag shouldSkipMissingTag
     * @return the result
     * @since 0.1.7
     */
    public Object removeSysOperation(Object sysOperationId, Object tag, TagMatchStrategy tagMatchStrategy,
            boolean shouldSkipMissingTag) {
        Object results =
            innerRemoveResources(sysOperationId, tag, tagMatchStrategy, shouldSkipMissingTag, "sys_operation");

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
     * removeToolOwnedBy.
     *
     * <p>Releases the owner's claim on the tool entry. The entry stays
     * registered while other owners still hold claims and is removed only
     * when the last owner releases it, so a destroying instance cannot
     * break another instance's tool resolution. The release runs inside
     * {@link #structureLock} (mirroring the sys_operation release): the
     * empty-set decision must not interleave with a concurrent equivalent
     * registration merging a fresh claim, otherwise the stale decision
     * wipes an entry another owner just claimed.</p>
     *
     * @param toolId toolId
     * @param owner owner token recorded at registration
     * @return whether the entry was actually removed
     * @since 0.1.16
     */
    public boolean removeToolOwnedBy(String toolId, String owner) {
        structureLock.lock();
        try {
            return removeOwnedResource(toolId, owner, "tool");
        } finally {
            structureLock.unlock();
        }
    }

    /**
     * removeSysOperationOwnedBy.
     *
     * <p>Releases the owner's claim on the system operation entry (see
     * {@link #removeToolOwnedBy(String, String)}), plus this owner's
     * claims on the bound tools the sys_operation registered (every
     * joining owner claims them on add, so the first owner's release
     * leaves no ghost ownership while survivors keep the tools). When the
     * release removes the entry, the remaining bound tools are removed in
     * the same locked sequence, mirroring
     * {@link #removeSysOperation(Object, Object, TagMatchStrategy, boolean)}.</p>
     *
     * @param sysOperationId sysOperationId
     * @param owner owner token recorded at registration
     * @return whether the entry was actually removed
     * @since 0.1.16
     */
    public boolean removeSysOperationOwnedBy(String sysOperationId, String owner) {
        structureLock.lock();
        try {
            List<String> toolIds = resourceRegistry.tool().getSysOperationToolIds(sysOperationId);
            for (String toolId : toolIds) {
                removeOwnedResource(toolId, owner, "tool");
            }
            if (!removeOwnedResource(sysOperationId, owner, "sys_operation")) {
                return false;
            }
            List<String> boundToolIds = resourceRegistry.tool().removeSysOperationTools(sysOperationId);
            if (!boundToolIds.isEmpty()) {
                innerRemoveResources(boundToolIds, null, TagMatchStrategy.ALL, true, "tool");
            }
            return true;
        } finally {
            structureLock.unlock();
        }
    }

    /**
     * removeOwnedResource.
     *
     * <p>Must be called while holding {@link #structureLock}: drops the
     * owner from the entry's owner set and removes the entry (tag, registry,
     * card, owner records in one sequence) when the set becomes empty.</p>
     *
     * @param resourceId resourceId
     * @param owner owner
     * @param resourceType resourceType
     * @return whether the entry was removed
     * @since 0.1.16
     */
    private boolean removeOwnedResource(String resourceId, String owner, String resourceType) {
        if (resourceId == null || owner == null) {
            return false;
        }
        Set<String> owners = idToOwners.get(resourceId);
        if (owners == null) {
            return false;
        }
        owners.remove(owner);
        if (!owners.isEmpty()) {
            return false;
        }
        innerRemoveResources(resourceId, null, TagMatchStrategy.ALL, true, resourceType);
        return true;
    }

    /**
     * getSysOperation.
     * 
     * @param sysOperationId sysOperationId
     * @param tag tag
     * @param tagMatchStrategy tagMatchStrategy
     * @return the result
     * @since 0.1.7
     */
    public Object getSysOperation(String sysOperationId, Object tag, TagMatchStrategy tagMatchStrategy) {
        return innerGetResources(sysOperationId, tag, tagMatchStrategy, "sys_operation");
    }

    /**
     * getSysOpToolCards.
     * 
     * @param sysOperationId sysOperationId
     * @param operationName operationName
     * @param toolName toolName
     * @return the result
     * @since 0.1.7
     */
    public Object getSysOpToolCards(String sysOperationId, Object operationName, Object toolName) {
        if (operationName instanceof List && toolName != null) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_VALUE_INVALID, "resource_type", "sys_operation", "reason",
                    "tool_name cannot be specified when operation_name is a list");
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
     * getToolInfos.
     * 
     * @param toolId toolId
     * @param toolType toolType
     * @param tag tag
     * @param tagMatchStrategy tagMatchStrategy
     * @return the result
     * @since 0.1.7
     */
    public List<ToolInfo> getToolInfos(Object toolId, Object toolType, Object tag, TagMatchStrategy tagMatchStrategy) {
        FindResult findResult = innerFindResourceIds(toolId, tag, tagMatchStrategy, true, "tool");
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
     * addMcpServer.
     * 
     * @param serverConfig serverConfig
     * @param tag tag
     * @param expiryTime expiryTime
     * @return the result
     * @since 0.1.7
     */
    public List<Result<String>> addMcpServer(Object serverConfig, Object tag, Double expiryTime) {
        if (tag != null) {
            validateTag(tag);
        }
        if (expiryTime != null && expiryTime <= 0) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID, "param", "expire_time", "reason",
                    "expire time <= 0");
        }
        List<McpServerConfig> configs = normalizeServerConfigs(serverConfig);
        List<Result<String>> addResults = new ArrayList<>();

        for (McpServerConfig config : configs) {
            try {
                config.normalizeServerId();
                // Validate client type
                String clientType = config.getClientType();
                if ("streamable-http".equals(clientType)) {
                    clientType = "streamable_http";
                    config.setClientType(clientType);
                }
                if (!"sse".equals(clientType) && !"stdio".equals(clientType)
                        && !"streamable_http".equals(clientType)) {
                    throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID, "param", "client_type",
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
     * Looks up a registered MCP server config by server id.
     *
     * @param serverId MCP server identifier
     * @return the config, or {@code null} when not registered / blank id
     * @since 0.1.14
     */
    public McpServerConfig getMcpServerConfig(String serverId) {
        return resourceRegistry.tool().getMcpServerConfig(serverId);
    }

    /**
     * Returns whether the MCP server slot holds a committed (established)
     * entry — a mid-flight registration placeholder may still roll back and
     * must not be treated as final by writers.
     *
     * @param serverId MCP server identifier
     * @return {@code true} only when the entry is established
     * @since 0.1.16
     */
    public boolean isMcpServerEstablished(String serverId) {
        return resourceRegistry.tool().isMcpServerEstablished(serverId);
    }

    /**
     * Lists tool resource ids exposed by the given MCP server.
     *
     * @param serverId MCP server identifier
     * @return tool ids; empty when the server has none or is unknown
     * @since 0.1.14
     */
    public List<String> getMcpToolIds(String serverId) {
        Object idsObj = resourceRegistry.tool().getMcpToolId(serverId, null);
        if (!(idsObj instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    /**
     * Lists MCP server configs tagged with {@code tag} (defaults to {@link Tag#GLOBAL}).
     *
     * @param tag resource tag; {@code null} is treated as global
     * @return matching server configs (unknown ids are skipped)
     * @since 0.1.14
     */
    public List<McpServerConfig> listMcpServers(Object tag) {
        List<String> serverIds = tagMgr.findResourcesByTags(tag != null ? tag : Tag.GLOBAL, TagMatchStrategy.ALL, true);
        List<McpServerConfig> result = new ArrayList<>();
        for (String id : serverIds) {
            McpServerConfig config = resourceRegistry.tool().getMcpServerConfig(id);
            if (config != null) {
                result.add(config);
            }
        }
        return result;
    }

    /**
     * removeMcpServer.
     * 
     * @param serverId serverId
     * @param serverName serverName
     * @param tag tag
     * @param tagMatchStrategy tagMatchStrategy
     * @param shouldSkipMissingTag shouldSkipMissingTag
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public List<Result<String>> removeMcpServer(Object serverId, Object serverName, Object tag,
            TagMatchStrategy tagMatchStrategy, boolean shouldSkipMissingTag) throws Exception {
        List<String> serverIdsToRemove = innerGetServerIds(serverId, serverName, tag, tagMatchStrategy,
                shouldSkipMissingTag, StatusCode.RESOURCE_MCP_SERVER_REMOVE_ERROR);
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
     * getMcpTool.
     * 
     * @param name name
     * @param serverId serverId
     * @param serverName serverName
     * @param tag tag
     * @param tagMatchStrategy tagMatchStrategy
     * @param shouldSkipMissingTag shouldSkipMissingTag
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public Object getMcpTool(Object name, Object serverId, Object serverName, Object tag,
            TagMatchStrategy tagMatchStrategy, boolean shouldSkipMissingTag) throws Exception {
        List<String> serverIdsToGet = innerGetServerIds(serverId, serverName, tag, tagMatchStrategy,
                shouldSkipMissingTag, StatusCode.RESOURCE_MCP_TOOL_GET_ERROR);
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
     * listMcpResources.
     * 
     * @param serverId serverId
     * @param serverName serverName
     * @param tag tag
     * @param tagMatchStrategy tagMatchStrategy
     * @param shouldSkipMissingTag shouldSkipMissingTag
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public List<?> listMcpResources(Object serverId, Object serverName, Object tag, TagMatchStrategy tagMatchStrategy,
            boolean shouldSkipMissingTag) throws Exception {
        List<String> serverIdsToGet = innerGetServerIds(serverId, serverName, tag, tagMatchStrategy,
                shouldSkipMissingTag, StatusCode.RESOURCE_MCP_TOOL_GET_ERROR);
        List<Object> results = new ArrayList<>();
        for (String mcpServerId : serverIdsToGet) {
            results.addAll(resourceRegistry.tool().listMcpResources(mcpServerId));
        }
        return results;
    }

    /**
     * readMcpResource.
     * 
     * @param resourceServerId resourceServerId
     * @param uri uri
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
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
     * @param serverId MCP server ID(s) to refresh
     * @param serverName MCP server name(s) to refresh
     * @param tag Optional tag to filter servers
     * @param tagMatchStrategy Strategy for matching tags
     * @param shouldIgnoreException If true, continue refreshing other servers on error
     * @param shouldSkipMissingTag If true, skip non-existent tags
     * @return List of Result with server IDs or errors
     * @since 0.1.7
     */
    public List<Result<String>> refreshMcpServer(Object serverId, Object serverName, Object tag,
            TagMatchStrategy tagMatchStrategy, boolean shouldIgnoreException, boolean shouldSkipMissingTag) {
        return Collections.emptyList();
    }

    /**
     * Get MCP tool information/metadata by name and server.
     * 
     * @param name MCP tool name(s) to get info for (null returns all)
     * @param serverId MCP server ID(s) containing the tools
     * @param serverName MCP server name(s) containing the tools
     * @param tag Optional tag to filter servers/tools
     * @param tagMatchStrategy Strategy for matching tags
     * @param shouldSkipMissingTag If true, skip non-existent tags
     * @param shouldIgnoreException If true, ignore refresh exceptions
     * @return List of ToolInfo for matching MCP tools
     * @throws Exception Exception
     * @since 0.1.7
     */
    public List<ToolInfo> getMcpToolInfos(Object name, Object serverId, Object serverName, Object tag,
            TagMatchStrategy tagMatchStrategy, boolean shouldSkipMissingTag, boolean shouldIgnoreException)
            throws Exception {
        List<String> serverIdsToGet = innerGetServerIds(serverId, serverName, tag, tagMatchStrategy,
                shouldSkipMissingTag, StatusCode.RESOURCE_MCP_TOOL_GET_ERROR);
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
     * getResourceByTag.
     * 
     * @param tag tag
     * @return the result
     * @since 0.1.7
     */
    public List<BaseCard> getResourceByTag(String tag) {
        validateTag(tag);
        List<String> resourceIds = tagMgr.getTagResources(tag);
        if (resourceIds == null || resourceIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<BaseCard> cards = new ArrayList<>();
        for (String resourceId : resourceIds) {
            BaseCard card = idToCard.get(resourceId);
            if (card != null) {
                cards.add(card);
            }
        }
        return cards;
    }

    /**
     * listTags.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> listTags() {
        return tagMgr.listTags();
    }

    /**
     * hasTag.
     * 
     * @param tag tag
     * @return the result
     * @since 0.1.7
     */
    public boolean hasTag(String tag) {
        validateTag(tag);
        return tagMgr.hasTag(tag);
    }

    /**
     * removeTag.
     * 
     * @param tag tag
     * @param shouldSkipMissingTag shouldSkipMissingTag
     * @return the result
     * @since 0.1.7
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
     * updateResourceTag.
     * 
     * @param resourceId resourceId
     * @param tag tag
     * @return the result
     * @since 0.1.7
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
     * addResourceTag.
     * 
     * @param resourceId resourceId
     * @param tag tag
     * @return the result
     * @since 0.1.7
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
     * removeResourceTag.
     * 
     * @param resourceId resourceId
     * @param tag tag
     * @param shouldSkipMissingTag shouldSkipMissingTag
     * @return the result
     * @since 0.1.7
     */
    public Result<List<String>> removeResourceTag(String resourceId, Object tag, boolean shouldSkipMissingTag) {
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
     * getResourceTag.
     * 
     * @param resourceId resourceId
     * @return the result
     * @since 0.1.7
     */
    public List<String> getResourceTag(String resourceId) {
        return tagMgr.getResourcesTags(resourceId);
    }

    /**
     * hasResourceTag.
     * 
     * @param resourceId resourceId
     * @param tag tag
     * @return the result
     * @since 0.1.7
     */
    public boolean hasResourceTag(String resourceId, String tag) {
        validateTag(tag);
        validateResourceId(resourceId);
        return tagMgr.hasResourceTag(resourceId, tag);
    }

    /**
     * resourceHasTag.
     * 
     * @param resourceId resourceId
     * @param tag tag
     * @return the result
     * @since 0.1.7
     */
    public boolean resourceHasTag(String resourceId, String tag) {
        return hasResourceTag(resourceId, tag);
    }

    /**
     * release.
     * 
     * @since 0.1.7
     */
    public void release() {
        resourceRegistry.clearAll();
        tagMgr.clear();
        idToCard.clear();
    }

    // ========== Internal Methods ==========

    /**
     * refreshExistingResourceIfNeeded.
     *
     * <p>Replaces the resource instance under the id while preserving the
     * entry's ownership: the refresh removes the entry and re-seeds every
     * other owner's claim, so the caller's re-add keeps the entry shared
     * instead of silently wiping the owner set (the multi-owner contract
     * applies to the id, not the instance). Without residual owners the
     * removal is a plain refresh.</p>
     *
     * @param resourceId resourceId
     * @param tag tag
     * @since 0.1.7
     */
    private void refreshExistingResourceIfNeeded(String resourceId, Object tag) {
        if (!tagMgr.hasResource(resourceId)) {
            return;
        }
        structureLock.lock();
        try {
            Set<String> previousOwners = new HashSet<>(idToOwners.getOrDefault(resourceId, Set.of()));
            innerRemoveResources(resourceId, tag, TagMatchStrategy.ALL, true, "tool");
            String refreshOwner = String.valueOf(tag);
            for (String previousOwner : previousOwners) {
                if (!previousOwner.equals(refreshOwner)) {
                    claimOwnership(resourceId, previousOwner);
                }
            }
        } finally {
            structureLock.unlock();
        }
        logger.info("refreshed existing resource, id={}", resourceId);
    }

    /**
     * innerAddResource with the owner token claiming the entry.
     *
     * <p>Runs the full registration sequence (duplicate probe, registry
     * write, card index, tag index, owner claim) under
     * {@link #structureLock} so registration and removal sequences never
     * interleave. An owner-aware caller that hits an existing id with an
     * equivalent ability declaration reuses the entry: the owner joins the
     * entry's owner set and the existing card is returned. A conflicting
     * definition fails with RESOURCE_ADD_ERROR. A null owner keeps the
     * legacy duplicate failure.</p>
     *
     * @param registration the registration request (value object)
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    private <C> Result<C> innerAddResource(ResourceRegistration registration) {
        String resourceId = registration.resourceId();
        try {
            structureLock.lock();
            try {
                if (tagMgr.hasResource(resourceId)) {
                    return resolveExistingResource(resourceId, registration.resourceCard(),
                            registration.resourceType(), registration.owner());
                }
                Object resource = registration.resource();
                switch (registration.resourceType()) {
                    case "workflow" ->
                        resourceRegistry.workflow().addWorkflow(resourceId, (Supplier<Workflow>) resource);
                    case "agent" -> resourceRegistry.agent().addAgent(resourceId, (Supplier<Object>) resource);
                    case "group" ->
                        resourceRegistry.agentGroup().addAgentGroup(resourceId, (Supplier<Object>) resource);
                    case "tool" -> resourceRegistry.tool().addTool(resourceId, (Tool) resource);
                    case "prompt" -> resourceRegistry.prompt().addPrompt(resourceId, (PromptTemplate) resource);
                    case "model" -> resourceRegistry.model().addModel(resourceId, (Supplier<Model>) resource);
                    case "sys_operation" ->
                        resourceRegistry.sysOperation().addSysOperation(resourceId, (SysOperation) resource);
                    default -> {/* no-op */}
                }
                BaseCard resourceCard = registration.resourceCard();
                if (resourceCard != null) {
                    idToCard.put(resourceId, resourceCard);
                }
                tagMgr.tagResource(resourceId, registration.tag() != null ? registration.tag() : Tag.GLOBAL);
                Object owner = registration.owner();
                if (owner != null) {
                    claimOwnership(resourceId, owner);
                }
                logger.info("add resource succeed, id={}, type={}", resourceId, registration.resourceType());
                return new Ok<>((C) (resourceCard != null ? resourceCard : resourceId));
            } finally {
                structureLock.unlock();
            }
        } catch (Exception e) {
            logger.error("add resource failed, id={}, type={}", resourceId, registration.resourceType(), e);
            return new Error<>(e);
        }
    }

    /**
     * resolveExistingResource.
     *
     * <p>Must be called while holding {@link #structureLock}: decides
     * between equivalent reuse (owner joins the entry's owner set, the
     * existing card is returned) and the duplicate failure.</p>
     *
     * @param resourceId resourceId
     * @param resourceCard resourceCard
     * @param resourceType resourceType
     * @param owner owner
     * @return the result
     * @since 0.1.16
     */
    @SuppressWarnings("unchecked")
    private <C> Result<C> resolveExistingResource(String resourceId, BaseCard resourceCard, String resourceType,
            Object owner) {
        BaseCard existingCard = idToCard.get(resourceId);
        if (owner != null && existingCard != null && resourceCard != null
                && sameAbilityDeclaration(existingCard, resourceCard)) {
            claimOwnership(resourceId, owner);
            logger.info("reuse existing resource, id={}, type={}", resourceId, resourceType);
            return new Ok<>((C) existingCard);
        }
        String card = resourceCard != null ? resourceCard.toString() : resourceId;
        String reason = owner != null
                ? "definition conflict with existing resource"
                : "resource already exist";
        throw ErrorHelper.buildError(StatusCode.RESOURCE_ADD_ERROR, "card", card, "reason", reason);
    }

    /**
     * claimOwnership.
     *
     * <p>Must be called while holding {@link #structureLock}.</p>
     *
     * @param resourceId resourceId
     * @param owner owner
     * @since 0.1.16
     */
    private void claimOwnership(String resourceId, Object owner) {
        Set<String> owners = idToOwners.computeIfAbsent(resourceId, key -> ConcurrentHashMap.newKeySet());
        owners.add(String.valueOf(owner));
    }

    /**
     * innerRemoveResources.
     * 
     * <p>Runs the whole removal sequence (id or by-tag resolution, then the
     * per-entry removals) under {@link #structureLock}: the by-tag reverse
     * lookup takes a consistent snapshot, and removal sequences never
     * interleave with registration sequences.</p>
     *
     * @param resourceId resourceId
     * @param tag tag
     * @param tagMatchStrategy tagMatchStrategy
     * @param shouldSkipMissingTag shouldSkipMissingTag
     * @param resourceType resourceType
     * @return the result
     * @since 0.1.7
     */
    private <C> List<Result<C>> innerRemoveResources(Object resourceId, Object tag, TagMatchStrategy tagMatchStrategy,
            boolean shouldSkipMissingTag, String resourceType) {
        structureLock.lock();
        try {
            List<String> idsToRemove;
            boolean isRemoveByTag = false;
            if (resourceId != null) {
                validateResourceIds(resourceId, resourceType);
                idsToRemove = normalizeIds(resourceId);
            } else {
                validateTag(tag);
                idsToRemove = tagMgr.findResourcesByTags(tag,
                        tagMatchStrategy != null ? tagMatchStrategy : TagMatchStrategy.ALL, shouldSkipMissingTag);
                isRemoveByTag = true;
                if (idsToRemove.isEmpty()) {
                    return Collections.emptyList();
                }
            }

            List<Result<C>> results = new ArrayList<>();
            for (String removeId : idsToRemove) {
                Optional<Result<C>> entry = removeResourceEntry(removeId, resourceType, isRemoveByTag);
                entry.ifPresent(results::add);
            }
            return results;
        } finally {
            structureLock.unlock();
        }
    }

    /**
     * removeResourceEntry.
     *
     * <p>Removes one entry's tag, registry, card, and owner records; must
     * be called while holding {@link #structureLock}. Returns empty for a
     * by-tag removal of a card-less entry, preserving the legacy skip
     * semantics.</p>
     *
     * @param removeId removeId
     * @param resourceType resourceType
     * @param isRemoveByTag isRemoveByTag
     * @return the result
     * @since 0.1.16
     */
    @SuppressWarnings("unchecked")
    private <C> Optional<Result<C>> removeResourceEntry(String removeId, String resourceType, boolean isRemoveByTag) {
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
                default -> {/* no-op */}
            }
        } catch (Exception e) {
            if (!isRemoveByTag) {
                error = e;
            } else {
                logger.warn("remove resource entry failed during by-tag removal, id={}, type={}",
                        removeId, resourceType, e);
            }
        }
        BaseCard removedCard = idToCard.remove(removeId);
        idToOwners.remove(removeId);
        if (error != null) {
            logger.error("remove resource failed, id={}, type={}", removeId, resourceType, error);
            return Optional.of(new Error<>(error));
        }
        if ("tool".equals(resourceType) || "prompt".equals(resourceType)) {
            return Optional.of(new Ok<>((C) removeId));
        }
        if (removedCard != null || !isRemoveByTag) {
            return Optional.of(new Ok<>((C) removedCard));
        }
        return Optional.empty();
    }

    /**
     * sameAbilityDeclaration.
     *
     * <p>Equivalence check for the reuse decision. {@link ToolCard}
     * delegates to the card's value equality (id, name, description,
     * inputParams, properties). {@link SysOperationCard} compares the
     * declaration fields (name, description, mode, gatewayConfig) and
     * deliberately excludes {@code workConfig}: the work directory is a
     * resource-bound field that legitimately differs per instance while the
     * registered ability stays the same (the first registrant's binding is
     * reused). Other card types fall back to {@link Object#equals}; cards
     * of different types never compare equal.</p>
     *
     * @param existing existing
     * @param incoming incoming
     * @return the result
     * @since 0.1.16
     */
    private boolean sameAbilityDeclaration(BaseCard existing, BaseCard incoming) {
        if (existing instanceof ToolCard existingTool && incoming instanceof ToolCard incomingTool) {
            return existingTool.equals(incomingTool);
        }
        if (existing instanceof SysOperationCard existingOp && incoming instanceof SysOperationCard incomingOp) {
            return Objects.equals(existingOp.getName(), incomingOp.getName())
                    && Objects.equals(existingOp.getDescription(), incomingOp.getDescription())
                    && Objects.equals(existingOp.getMode(), incomingOp.getMode())
                    && Objects.equals(existingOp.getGatewayConfig(), incomingOp.getGatewayConfig());
        }
        return existing.equals(incoming);
    }

    /**
     * innerFindResourceIds.
     * 
     * @param resourceId resourceId
     * @param tag tag
     * @param tagMatchStrategy tagMatchStrategy
     * @param shouldSkipMissingTag shouldSkipMissingTag
     * @param resourceType resourceType
     * @return the result
     * @since 0.1.7
     */
    private FindResult innerFindResourceIds(Object resourceId, Object tag, TagMatchStrategy tagMatchStrategy,
            boolean shouldSkipMissingTag, String resourceType) {
        if (resourceId != null) {
            validateResourceIds(resourceId, resourceType);
            return new FindResult(normalizeIds(resourceId), true);
        }
        List<String> ids = tagMgr.findResourcesByTags(tag != null ? tag : Tag.GLOBAL,
                tagMatchStrategy != null ? tagMatchStrategy : TagMatchStrategy.ALL, shouldSkipMissingTag);
        return new FindResult(ids, false);
    }

    /**
     * innerGetResources.
     * 
     * @param resourceId resourceId
     * @param tag tag
     * @param tagMatchStrategy tagMatchStrategy
     * @param resourceType resourceType
     * @return the result
     * @since 0.1.7
     */
    private Object innerGetResources(Object resourceId, Object tag, TagMatchStrategy tagMatchStrategy,
            String resourceType) {
        FindResult findResult = innerFindResourceIds(resourceId, tag, tagMatchStrategy, true, resourceType);
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

    /**
     * innerGetResourcesByProvider.
     * 
     * @param resourceId resourceId
     * @param tag tag
     * @param tagMatchStrategy tagMatchStrategy
     * @param resourceType resourceType
     * @return the result
     * @since 0.1.7
     */
    private Object innerGetResourcesByProvider(Object resourceId, Object tag, TagMatchStrategy tagMatchStrategy,
            String resourceType) {
        FindResult findResult = innerFindResourceIds(resourceId, tag, tagMatchStrategy, true, resourceType);
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

    /**
     * registerSysOperationTools.
     * 
     * @param card card
     * @param instance instance
     * @param tag tag
     * @param owner owner token claiming the bound tools
     * @since 0.1.7
     */
    private void registerSysOperationTools(SysOperationCard card, SysOperation instance, Object tag, Object owner) {
        List<SysOperationToolAdapter.ToolEntry> tools = SysOperationToolAdapter.extractTools(card, instance);
        List<String> toolIds = new ArrayList<>();
        for (SysOperationToolAdapter.ToolEntry entry : tools) {
            innerAddResource(new ResourceRegistration(entry.toolId(), entry.localFunction(),
                    entry.localFunction().getCard(), tag, "tool", owner));
            toolIds.add(entry.toolId());
        }
        resourceRegistry.tool().addSysOperationTools(card.getId(), toolIds);
    }

    /**
     * claimSysOperationTools.
     *
     * <p>Claims the existing bound tools of a reused sys_operation entry
     * for the joining owner, so the release of any single owner (first
     * registrant included) never leaves ghost ownership behind while
     * other owners still resolve the tools. Runs inside the caller's
     * {@link #structureLock} domain (reentrant), which keeps the claim
     * atomic against both the release path and the first registrant's
     * deferred tool registration.</p>
     *
     * @param sysOpId sysOpId whose bound tools are claimed
     * @param owner owner token claiming the bound tools
     * @since 0.1.16
     */
    private void claimSysOperationTools(String sysOpId, Object owner) {
        if (owner == null) {
            return;
        }
        structureLock.lock();
        try {
            for (String toolId : resourceRegistry.tool().getSysOperationToolIds(sysOpId)) {
                claimOwnership(toolId, owner);
            }
        } finally {
            structureLock.unlock();
        }
    }

    /**
     * innerGetServerIds.
     * 
     * @param serverId serverId
     * @param serverName serverName
     * @param tag tag
     * @param tagMatchStrategy tagMatchStrategy
     * @param shouldSkipMissingTag shouldSkipMissingTag
     * @param errorCode errorCode
     * @return the result
     * @since 0.1.7
     */
    private List<String> innerGetServerIds(Object serverId, Object serverName, Object tag,
            TagMatchStrategy tagMatchStrategy, boolean shouldSkipMissingTag, StatusCode errorCode) {
        List<String> ids = new ArrayList<>();
        if (serverId != null) {
            if (serverId instanceof String s) {
                if (s.isEmpty()) {
                    throw ErrorHelper.buildError(errorCode, "server_config", String.valueOf(serverId), "reason",
                            "server_id is empty");
                }
                ids.add(s);
            }
        } else if (serverName == null) {
            ids.addAll(tagMgr.findResourcesByTags(tag != null ? tag : Tag.GLOBAL,
                    tagMatchStrategy != null ? tagMatchStrategy : TagMatchStrategy.ALL, shouldSkipMissingTag));
        } else {
            List<String> serverNames = normalizeStringList(serverName);
            if (serverNames.isEmpty()) {
                throw ErrorHelper.buildError(errorCode, "server_id", String.valueOf(serverId), "reason",
                        "server_name is empty");
            }
            if (serverNames.stream().anyMatch(String::isEmpty)) {
                throw ErrorHelper.buildError(errorCode, "server_id", String.valueOf(serverId), "reason",
                        "server_name is empty");
            }
            for (String sName : serverNames) {
                ids.addAll(resourceRegistry.tool().getMcpServerIds(sName));
            }
        }
        return ids;
    }

    /**
     * validateTag.
     * 
     * @param tag tag
     * @since 0.1.7
     */
    private static void validateTag(Object tag) {
        if (tag == null) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_TAG_VALUE_INVALID, "tag", "null", "reason",
                    "is None or empty value");
        }
        if (tag instanceof String s && s.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_TAG_VALUE_INVALID, "tag", "''", "reason",
                    "is None or empty value");
        }
        if (tag instanceof List<?> list) {
            if (list.contains(Tag.GLOBAL) && list.size() > 1) {
                throw ErrorHelper.buildError(StatusCode.RESOURCE_TAG_VALUE_INVALID, "tag", tag.toString(), "reason",
                        "The GLOBAL tag already exists and cannot be assigned additional tags.");
            }
            Set<Object> seen = new HashSet<>();
            for (Object item : list) {
                if (item == null || (item instanceof String s && s.isEmpty())) {
                    throw ErrorHelper.buildError(StatusCode.RESOURCE_TAG_VALUE_INVALID, "tag", tag.toString(), "reason",
                            "has None or empty value");
                }
                if (!seen.add(item)) {
                    throw ErrorHelper.buildError(StatusCode.RESOURCE_TAG_VALUE_INVALID, "tag", tag.toString(), "reason",
                            "has duplicate tag '" + item + "' item");
                }
            }
        }
    }

    /**
     * Validates agent provider entries before any resource is registered.
     *
     * @param agents agent provider entries
     * @since 0.1.14
     */
    private static void validateAgentEntries(List<AgentEntry> agents) {
        validateProviderListNotEmpty(agents, "agent");
        for (int index = 0; index < agents.size(); index++) {
            Object item = agents.get(index);
            if (!(item instanceof AgentEntry entry)) {
                throw buildInvalidProviderFormat("agent", index, item, AgentCard.class);
            }
            validateProviderEntry(entry.card(), entry.provider(), "agent", AgentCard.class, index);
        }
    }

    /**
     * Validates workflow provider entries before any resource is registered.
     *
     * @param workflows workflow provider entries
     * @since 0.1.14
     */
    private static void validateWorkflowEntries(List<WorkflowEntry> workflows) {
        validateProviderListNotEmpty(workflows, "workflow");
        for (int index = 0; index < workflows.size(); index++) {
            Object item = workflows.get(index);
            if (!(item instanceof WorkflowEntry entry)) {
                throw buildInvalidProviderFormat("workflow", index, item, WorkflowCard.class);
            }
            validateProviderEntry(entry.card(), entry.provider(), "workflow", WorkflowCard.class, index);
        }
    }

    /**
     * Validates that a provider entry list is present and non-empty.
     *
     * @param entries provider entries
     * @param resourceType resource type
     * @since 0.1.14
     */
    private static void validateProviderListNotEmpty(List<?> entries, String resourceType) {
        if (entries == null || entries.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_PROVIDER_INVALID, "resource_type", resourceType,
                    "reason", "cannot be empty");
        }
    }

    /**
     * Validates one card/provider pair from a bulk registration request.
     *
     * @param card resource card
     * @param provider resource provider
     * @param resourceType resource type
     * @param cardClassType expected card type
     * @param index entry index
     * @since 0.1.14
     */
    private static void validateProviderEntry(Object card, Object provider, String resourceType,
            Class<? extends BaseCard> cardClassType, int index) {
        if (card == null) {
            throw buildProviderError(resourceType, "invalid card at idx " + index
                    + ": card cannot be None, must be an instance of " + cardClassType.getSimpleName());
        }
        if (!(card instanceof BaseCard resourceCard) || !cardClassType.isInstance(resourceCard)) {
            throw buildProviderError(resourceType, "invalid " + resourceType + " card type at idx " + index
                    + ": expected " + cardClassType.getSimpleName() + ", got " + getPythonTypeName(card));
        }
        try {
            validateResourceId(resourceCard.getId(), resourceType);
        } catch (BaseError error) {
            String reason = "invalid " + resourceType + " id at idx " + index + ": " + error.getMessage();
            throw buildValidationError(StatusCode.RESOURCE_PROVIDER_INVALID, resourceType, reason, error);
        }
        if (provider == null) {
            throw buildProviderError(resourceType, "invalid provider at idx " + index
                    + ": provider cannot be None, must be a callable function");
        }
        if (!(provider instanceof Supplier<?>)) {
            throw buildProviderError(resourceType, "invalid " + resourceType + " provider type at idx " + index
                    + ": expected callable, got " + getPythonTypeName(provider));
        }
    }

    /**
     * Builds the Python-compatible malformed provider entry error.
     *
     * @param resourceType resource type
     * @param index entry index
     * @param item malformed entry
     * @param cardClassType expected card type
     * @return provider validation error
     * @since 0.1.14
     */
    private static BaseError buildInvalidProviderFormat(String resourceType, int index, Object item,
            Class<? extends BaseCard> cardClassType) {
        String length = item instanceof List<?> list ? String.valueOf(list.size()) : "N/A";
        String reason = "invalid provider format at idx " + index + ": expected tuple["
                + cardClassType.getSimpleName() + ", Callable], got " + getPythonTypeName(item)
                + " (length=" + length + ")";
        return buildProviderError(resourceType, reason);
    }

    /**
     * Builds a provider validation error.
     *
     * @param resourceType resource type
     * @param reason validation failure reason
     * @return provider validation error
     * @since 0.1.14
     */
    private static BaseError buildProviderError(String resourceType, String reason) {
        return ErrorHelper.buildError(StatusCode.RESOURCE_PROVIDER_INVALID, "resource_type", resourceType, "reason",
                reason);
    }

    /**
     * Validates one tool and its card.
     *
     * @param tool tool instance
     * @since 0.1.14
     */
    private static void validateTool(Tool tool) {
        validateResource(tool, "tool", Tool.class);
        try {
            validateResourceCard(tool.getCard(), "tool", ToolCard.class);
            validateResourceId(tool.getCard().getId(), "tool");
        } catch (BaseError error) {
            throw buildValidationError(StatusCode.RESOURCE_VALUE_INVALID, "tool",
                    "tool has invalid card: " + error.getMessage(), error);
        }
    }

    /**
     * Validates every tool before bulk registration begins.
     *
     * @param tools tool instances
     * @since 0.1.14
     */
    private static void validateTools(List<Tool> tools) {
        if (tools == null || tools.isEmpty()) {
            throw buildResourceError("tool", "tool list cannot be empty");
        }
        for (int index = 0; index < tools.size(); index++) {
            Object item = tools.get(index);
            if (!(item instanceof Tool tool)) {
                throw buildResourceError("tool", "invalid tool type at index " + index
                        + ": expected Tool, got " + getPythonTypeName(item));
            }
            try {
                validateResourceCard(tool.getCard(), "tool", ToolCard.class);
                validateResourceId(tool.getCard().getId(), "tool");
            } catch (BaseError error) {
                String reason = "tool at index " + index + " has invalid card: " + error.getMessage();
                throw buildValidationError(StatusCode.RESOURCE_VALUE_INVALID, "tool", reason, error);
            }
        }
    }

    /**
     * Builds a resource value validation error.
     *
     * @param resourceType resource type
     * @param reason validation failure reason
     * @return resource validation error
     * @since 0.1.14
     */
    private static BaseError buildResourceError(String resourceType, String reason) {
        return ErrorHelper.buildError(StatusCode.RESOURCE_VALUE_INVALID, "resource_type", resourceType, "reason",
                reason);
    }

    /**
     * validateResourceCard.
     * 
     * @param card card
     * @param resourceType resourceType
     * @param cardClassType cardClassType
     * @since 0.1.7
     */
    private static void validateResourceCard(BaseCard card, String resourceType, Class<?> cardClassType) {
        if (card == null || !cardClassType.isInstance(card)) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_CARD_VALUE_INVALID, "resource_type", resourceType,
                    "reason", "cannot be None, must be an instance of " + cardClassType.getSimpleName());
        }
    }

    /**
     * validateResourceId.
     * 
     * @param resourceId resourceId
     * @since 0.1.7
     */
    private static void validateResourceId(String resourceId) {
        validateResourceId(resourceId, "resource");
    }

    /**
     * validateResourceId.
     * 
     * @param resourceId resourceId
     * @param resourceType resourceType
     * @since 0.1.7
     */
    private static void validateResourceId(String resourceId, String resourceType) {
        if (resourceId == null || resourceId.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_ID_VALUE_INVALID, "resource_type", resourceType, "reason",
                    "cannot be empty or None");
        }
        if (resourceId.isBlank()) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_ID_VALUE_INVALID, "resource_type", resourceType, "reason",
                    "string id cannot be empty or whitespace only");
        }
    }

    /**
     * Validates a single resource ID or a list of resource IDs.
     *
     * @param resourceId resource ID input
     * @param resourceType resource type
     * @since 0.1.14
     */
    private static void validateResourceIds(Object resourceId, String resourceType) {
        if (resourceId instanceof String id) {
            if (id.isEmpty()) {
                throw buildResourceIdError(resourceType, resourceType + " id list cannot be empty or None");
            }
            validateResourceId(id, resourceType);
            return;
        }
        if (!(resourceId instanceof List<?> ids)) {
            throw buildResourceIdError(resourceType, "invalid id type: expected string or list, got "
                    + getPythonTypeName(resourceId));
        }
        if (ids.isEmpty()) {
            throw buildResourceIdError(resourceType, resourceType + " id list cannot be empty or None");
        }
        Set<String> seenIds = new HashSet<>();
        for (int index = 0; index < ids.size(); index++) {
            Object item = ids.get(index);
            if (!(item instanceof String id)) {
                String itemError = resourceType + " id is invalid, reason='invalid id type: expected string, got "
                        + getPythonTypeName(item) + "'";
                throw buildResourceIdError(resourceType,
                        "invalid " + resourceType + " id at idx " + index + ": " + itemError);
            }
            try {
                validateResourceId(id, resourceType);
            } catch (BaseError error) {
                String reason = "invalid " + resourceType + " id at idx " + index + ": " + error.getMessage();
                throw buildValidationError(StatusCode.RESOURCE_ID_VALUE_INVALID, resourceType, reason, error);
            }
            if (!seenIds.add(id)) {
                throw buildResourceIdError(resourceType, "duplicate " + resourceType + " id found: '" + id
                        + "' appears multiple times in the list");
            }
        }
    }

    /**
     * Builds a resource ID validation error.
     *
     * @param resourceType resource type
     * @param reason validation failure reason
     * @return resource ID validation error
     * @since 0.1.14
     */
    private static BaseError buildResourceIdError(String resourceType, String reason) {
        return ErrorHelper.buildError(StatusCode.RESOURCE_ID_VALUE_INVALID, "resource_type", resourceType, "reason",
                reason);
    }

    /**
     * Builds a validation error while preserving the lower-level cause.
     *
     * @param status validation status
     * @param resourceType resource type
     * @param reason validation failure reason
     * @param cause lower-level validation failure
     * @return validation error with its cause attached
     * @since 0.1.14
     */
    private static BaseError buildValidationError(StatusCode status, String resourceType, String reason,
            Throwable cause) {
        return ErrorHelper.buildError(status, null, null, cause,
                Map.of("resource_type", resourceType, "reason", reason));
    }

    /**
     * Returns the Python-style type name used by parity validation messages.
     *
     * @param value input value
     * @return Python-style type name
     * @since 0.1.14
     */
    private static String getPythonTypeName(Object value) {
        if (value == null) {
            return "NoneType";
        }
        if (value instanceof String) {
            return "str";
        }
        if (value instanceof List<?>) {
            return "list";
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return "int";
        }
        if (value instanceof Float || value instanceof Double) {
            return "float";
        }
        return value.getClass().getSimpleName();
    }

    /**
     * validateProvider.
     * 
     * @param provider provider
     * @param resourceType resourceType
     * @since 0.1.7
     */
    private static void validateProvider(Object provider, String resourceType) {
        if (provider == null) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_PROVIDER_INVALID, "resource_type", resourceType, "reason",
                    "provider cannot be None, must be a callable function");
        }
    }

    /**
     * validateResource.
     * 
     * @param instance instance
     * @param resourceType resourceType
     * @param resourceClassType resourceClassType
     * @since 0.1.7
     */
    private static void validateResource(Object instance, String resourceType, Class<?> resourceClassType) {
        if (instance == null) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_VALUE_INVALID, "resource_type", resourceType, "reason",
                    resourceType + " cannot be None: expected an instance of " + resourceClassType.getSimpleName());
        }
        if (!resourceClassType.isInstance(instance)) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_VALUE_INVALID, "resource_type", resourceType, "reason",
                    "invalid " + resourceType + " type: expected " + resourceClassType.getSimpleName() + ", got "
                            + instance.getClass().getSimpleName());
        }
    }

    /**
     * getCardType.
     * 
     * @param card card
     * @return the result
     * @since 0.1.7
     */
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

    @SuppressWarnings("unchecked")
    /**
     * normalizeIds.
     * 
     * @param id id
     * @return the result
     * @since 0.1.7
     */
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
    /**
     * normalizeStringList.
     * 
     * @param obj obj
     * @return the result
     * @since 0.1.7
     */
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
    /**
     * normalizeServerConfigs.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    private static List<McpServerConfig> normalizeServerConfigs(Object config) {
        if (config == null) {
            throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID, "server_config",
                    String.valueOf(config), "reason", "MCP server configuration cannot be empty or None");
        }
        if (config instanceof McpServerConfig sc) {
            return List.of(sc);
        }
        if (config instanceof List<?> list) {
            if (list.isEmpty()) {
                throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID, "server_config",
                        String.valueOf(config), "reason", "server_config list is empty");
            }
            List<McpServerConfig> result = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (item == null) {
                    throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID, "server_config",
                            String.valueOf(config), "reason",
                            "Invalid MCP server configuration at index " + i + ": configuration cannot be null");
                }
                if (!(item instanceof McpServerConfig)) {
                    throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID, "server_config",
                            String.valueOf(config), "reason",
                            "Invalid MCP server configuration type at index " + i + ": expected McpServerConfig");
                }
                result.add((McpServerConfig) item);
            }
            return result;
        }
        throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_PARAM_INVALID, "server_config",
                String.valueOf(config), "reason", "Invalid MCP server configuration type");
    }

    /**
     * findWorkflowByAlternateId.
     * 
     * @param workflowId workflowId
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * deriveWorkflowBaseId.
     * 
     * @param registeredId registeredId
     * @param version version
     * @return the result
     * @since 0.1.7
     */
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
     * @since 0.1.7
     */
    public record AgentEntry(AgentCard card, Supplier<Object> provider) {
    }

    /**
     * Public record WorkflowEntry used by the Java parity implementation.
     * 
     * @since 0.1.7
     */
    public record WorkflowEntry(WorkflowCard card, Supplier<Workflow> provider) {
    }

    /**
     * Public record ModelEntry used by the Java parity implementation.
     * 
     * @since 0.1.7
     */
    public record ModelEntry(String id, Supplier<Model> provider) {
    }

    /**
     * Public record PromptEntry used by the Java parity implementation.
     *
     * @since 0.1.7
     */
    public record PromptEntry(String id, PromptTemplate template) {
    }

    /**
     * Registration request for {@link #innerAddResource}: one
     * value object replaces the six-parameter signature — the id, the
     * runtime instance, the identity card, the tag, the typed-registry
     * discriminator, and the optional owner token. The convenience
     * constructor keeps the legacy owner-less registration shape.
     *
     * @param resourceId resource id used as the registry key
     * @param resource runtime instance stored by the typed registry
     * @param resourceCard identity card indexed for lookups, or {@code null}
     * @param tag tag attached to the entry ({@code null} defaults to global)
     * @param resourceType typed-registry discriminator
     * @param owner owner token claiming the entry ({@code null} keeps legacy semantics)
     * @since 0.1.16
     */
    private record ResourceRegistration(String resourceId, Object resource, BaseCard resourceCard, Object tag,
            String resourceType, Object owner) {

        ResourceRegistration(String resourceId, Object resource, BaseCard resourceCard, Object tag,
                String resourceType) {
            this(resourceId, resource, resourceCard, tag, resourceType, null);
        }
    }

    /**
     * FindResult.
     *
     * @param ids ids
     * @param isExactMatch isExactMatch
     * @since 0.1.7
     */
    private record FindResult(List<String> ids, boolean isExactMatch) {
    }
}
