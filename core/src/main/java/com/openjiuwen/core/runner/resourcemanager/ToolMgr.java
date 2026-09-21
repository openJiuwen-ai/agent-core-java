/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.utils.IsolatedActions;
import com.openjiuwen.core.common.utils.IsolatedActions.IsolatedOutcome;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpClientFactory;
import com.openjiuwen.core.foundation.tool.mcp.McpServerAlreadyRegisteredError;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfigConflictError;
import com.openjiuwen.core.foundation.tool.mcp.McpTool;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manager for Tool instances, MCP servers, and SysOperation-related tools.
 * <p>
 * Provides registration, lookup, and lifecycle management for tools and MCP servers.
 * MCP client creation is delegated to {@link McpClientFactory} for SPI-based transport selection.
 * MCP server writes (add/remove/refresh) are serialized per server id by a
 * slot lock, so no writer can interleave with another writer's placeholder
 * window.
 * <p>
 * Mirrors Python's {@code ToolMgr} in {@code resources_manager/tool_manager.py}.
 *
 * @since 0.1.7
 */
public class ToolMgr {
    private static final Logger logger = LoggerFactory.getLogger(ToolMgr.class);

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final ConcurrentHashMap<String, Tool> tools = new ConcurrentHashMap<>();

    /**
     * ConcurrentHashMap with CopyOnWriteArrayList values: the
     * inner list is appended/removed by concurrent server register/remove
     * paths, so both the bucket structure and the value list must be
     * thread-safe; writes are rare relative to reads.
     *
     * @since 0.1.7
     */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<String>> mcpServerNameToIds =
            new ConcurrentHashMap<>();

    /**
     * ConcurrentHashMap.
     *
     * @since 0.1.7
     */
    private final ConcurrentHashMap<String, McpServerResource> mcpServerResources = new ConcurrentHashMap<>();

    /**
     * ConcurrentHashMap.
     *
     * @since 0.1.7
     */
    private final ConcurrentHashMap<String, SysOpToolResource> sysOpResources = new ConcurrentHashMap<>();

    /**
     * Per-server-id slot locks: one lock serializes the whole
     * add/remove/refresh write sequence of a single MCP server id, so no
     * writer can interleave with another writer's placeholder window. The
     * map only grows with distinct server ids — an entry is never removed,
     * because deleting a lock while waiters exist would let a freshly
     * created lock break mutual exclusion.
     *
     * @since 0.1.16
     */
    private final ConcurrentHashMap<String, ReentrantLock> mcpServerLocks = new ConcurrentHashMap<>();

    /**
     * Server ids whose registration is currently mid-flight: the
     * mark distinguishes an entry others may build on (established) from a
     * placeholder that may still roll back. Writers and the read-time
     * refresh path treat the mark as a wait signal: both hold the
     * per-server slot lock and block, bounded by the in-flight discovery
     * RPC, until the registration reaches a terminal state. Pure reads
     * that do not refresh keep observing the placeholder (transient
     * zero-tool window).
     *
     * @since 0.1.16
     */
    private final Set<String> registeringServers = ConcurrentHashMap.newKeySet();

    /**
     * Registers a tool with the given identifier.
     * 
     * @param toolId the unique identifier for the tool
     * @param tool the tool instance to register
     * @since 0.1.7
     */
    public void addTool(String toolId, Tool tool) {
        // Atomic check-and-insert: the former
        // containsKey + put compound let two concurrent registrations of the
        // same toolId both pass the guard, the second put silently replacing
        // the first — the same concurrent-write-loss class fixed elsewhere.
        if (tools.putIfAbsent(toolId, tool) != null) {
            throw new IllegalArgumentException("already exist tool " + toolId);
        }
    }

    /**
     * Retrieves a tool by its identifier.
     * 
     * @param toolId the unique identifier of the tool
     * @return the tool instance, or {@code null} if not found
     * @since 0.1.7
     */
    public Tool getTool(String toolId) {
        return tools.get(toolId);
    }

    /**
     * Retrieves an MCP tool by name and server identifier.
     * 
     * @param toolName the name of the MCP tool
     * @param serverId the identifier of the MCP server
     * @return the tool instance, or {@code null} if the server or tool is not found
     * @since 0.1.7
     */
    public Tool getMcpTool(String toolName, String serverId) {
        return findMcpServerResource(serverId)
                .map(resource -> getTool(generateMcpToolId(serverId, resource.config().getServerName(), toolName)))
                .orElse(null);
    }

    /**
     * Retrieves all tools belonging to the specified MCP server.
     * 
     * @param serverId the identifier of the MCP server
     * @return a list of tools for the server, or {@code null} if the server is not found
     * @since 0.1.7
     */
    public List<Tool> getMcpTools(String serverId) {
        List<Tool> results = new ArrayList<>();
        findMcpServerResource(serverId).ifPresent(resource -> {
            for (String toolId : resource.toolIds()) {
                Tool tool = getTool(toolId);
                if (tool != null) {
                    results.add(tool);
                }
            }
        });
        return results;
    }

    /**
     * Lists resources exposed by the specified MCP server.
     * 
     * @param serverId the identifier of the MCP server
     * @return a list of resources from the server
     * @throws Exception if listing resources from the server fails
     * @since 0.1.7
     */
    public List<Object> listMcpResources(String serverId) throws Exception {
        McpServerResource resource = findMcpServerResource(serverId)
                .orElseThrow(() -> new IllegalArgumentException("MCP server not found: " + serverId));
        return resource.client().listResources();
    }

    /**
     * Reads a resource from the specified MCP server by URI.
     * 
     * @param serverId the identifier of the MCP server
     * @param uri the URI of the resource to read
     * @return a list of resource contents
     * @throws Exception if reading the resource from the server fails
     * @since 0.1.7
     */
    public List<Object> readMcpResource(String serverId, String uri) throws Exception {
        McpServerResource resource = findMcpServerResource(serverId)
                .orElseThrow(() -> new IllegalArgumentException("MCP server not found: " + serverId));
        return resource.client().readResource(uri);
    }

    /**
     * Retrieves the MCP tool identifier for a given server and tool name.
     * If {@code toolName} is {@code null}, returns all tool identifiers for the server.
     * 
     * @param serverId the identifier of the MCP server
     * @param toolName the name of the tool, or {@code null} to retrieve all tool identifiers
     * @return the generated tool identifier, a list of all tool identifiers,
     *         or {@code null} if the server is not found
     * @since 0.1.7
     */
    public Object getMcpToolId(String serverId, String toolName) {
        return findMcpServerResource(serverId).<Object>map(resource -> {
            if (toolName == null) {
                // Snapshot — callers must not observe (or mutate)
                // the resource's live id list, consistent with
                // getMcpServerIds/getSysOperationToolIds.
                return new ArrayList<>(resource.toolIds());
            }
            return generateMcpToolId(serverId, resource.config().getServerName(), toolName);
        }).orElse(null);
    }

    /**
     * Removes and returns the tool associated with the given identifier.
     * 
     * @param toolId the unique identifier of the tool to remove
     * @return the removed tool instance, or {@code null} if no tool was found
     * @since 0.1.7
     */
    public Tool removeTool(String toolId) {
        return tools.remove(toolId);
    }

    /**
     * Generates a composite identifier for an MCP tool based on server and tool information.
     * 
     * @param serverId the identifier of the MCP server
     * @param serverName the name of the MCP server
     * @param toolName the name of the tool
     * @return the generated composite tool identifier
     * @since 0.1.7
     */
    public static String generateMcpToolId(String serverId, String serverName, String toolName) {
        return serverId + "." + serverName + "." + toolName;
    }

    /**
     * Adds an MCP tool server and connects to it, registering all discovered tools.
     * The registration sequence (claim, discovery, commit or rollback) runs
     * serialized under the server's slot lock; the losing
     * connection recycles outside the lock so a blocked disconnect never
     * stalls same-server writers.
     *
     * @param serverConfig the configuration for the MCP server
     * @param expiryTime the time in seconds after which the tool list should be refreshed,
     * @return a list of tool cards discovered from the server
     * @throws Exception if the server already exists, connection fails, or tool discovery fails
     *             or {@code null} for no expiry
     * @since 0.1.7
     */
    public List<McpToolCard> addToolServer(McpServerConfig serverConfig, Double expiryTime) throws Exception {
        // ConcurrentHashMap rejects null keys; normalizeServerId() fills a
        // blank server_id from server_name (or a UUID) exactly like the
        // ResourceMgr/DeepAgent entry points already do, so a config that
        // skipped normalization stays registerable.
        serverConfig.normalizeServerId();
        // Fast path (former containsKey guard): only an
        // ESTABLISHED entry dispatches without building a connection — a
        // mid-flight placeholder may still roll back, so an
        // observer falls through and waits for the slot lock instead.
        McpServerResource established = mcpServerResources.get(serverConfig.getServerId());
        if (established != null && !registeringServers.contains(serverConfig.getServerId())) {
            throw registrationError(established.config(), serverConfig);
        }
        McpClient client = createClient(serverConfig);
        connectClient(client, serverConfig);
        // Slot serialization: connecting is the slow step and
        // stays outside the lock; the claim, discovery, and commit (or
        // rollback) run as one unit under the server's slot lock, out of
        // reach of removal and refresh interleavings.
        ReentrantLock slotLock = serverSlotLock(serverConfig.getServerId());
        slotLock.lock();
        List<McpToolCard> results;
        BaseError loserDispatch = null;
        try {
            results = registerServerSlot(client, serverConfig, expiryTime);
        } catch (McpServerAlreadyRegisteredError | McpServerConfigConflictError dispatch) {
            loserDispatch = dispatch;
            results = null;
        } finally {
            slotLock.unlock();
        }
        if (loserDispatch != null) {
            // The loser's own connection recycles OUTSIDE the slot lock: a
            // blocked disconnect must never stall same-server writers.
            disconnectQuietly(client, serverConfig.getServerId());
            throw loserDispatch;
        }
        return results;
    }

    /**
     * Returns the slot lock of one MCP server id. Entries are
     * never removed: deleting a lock while waiters exist would let a newly
     * created lock break mutual exclusion, and the map only grows with the
     * distinct server ids ever registered.
     *
     * @param serverId the MCP server identifier, never {@code null}
     * @return the per-server slot lock
     * @since 0.1.16
     */
    private ReentrantLock serverSlotLock(String serverId) {
        return mcpServerLocks.computeIfAbsent(serverId, key -> new ReentrantLock());
    }

    /**
     * Registration sequence under the server slot lock: the
     * authoritative occupancy check, the placeholder claim, discovery, and
     * the commit (or rollback) run as one serialized unit — removal and
     * refresh can no longer interleave with the placeholder window. A
     * loser dispatch propagates to the caller, which recycles the losing
     * connection outside the lock; a winner failure rolls the placeholder
     * back and recycles before the wrapped error surfaces. The registering
     * mark distinguishes the mid-flight placeholder from an established
     * entry for the fast path and the facade pre-query.
     *
     * @param client the connected client of this registration attempt
     * @param serverConfig the config whose server slot is claimed
     * @param expiryTime the tool-list expiry, or {@code null} for none
     * @return the discovered tool cards
     * @since 0.1.16
     */
    private List<McpToolCard> registerServerSlot(McpClient client, McpServerConfig serverConfig,
            Double expiryTime) {
        // Authoritative dispatch: under the slot lock the observed entry is
        // a final state — never another registration's mid-flight
        // placeholder, whose writer still holds the lock.
        McpServerResource existing = mcpServerResources.get(serverConfig.getServerId());
        if (existing != null) {
            throw registrationError(existing.config(), serverConfig);
        }
        McpServerResource placeholder = new McpServerResource(serverConfig, client, List.of(),
                System.currentTimeMillis(), expiryTime);
        McpServerResource prev = mcpServerResources.putIfAbsent(serverConfig.getServerId(), placeholder);
        if (prev != null) {
            throw registrationError(prev.config(), serverConfig);
        }
        registeringServers.add(serverConfig.getServerId());
        try {
            IsolatedOutcome<List<McpToolCard>> outcome = IsolatedActions.callIsolated(() -> {
                List<McpToolCard> results = innerRefreshMcpTools(client, serverConfig, expiryTime);
                indexServerName(serverConfig);
                return results;
            });
            if (outcome.hasFailure()) {
                Throwable failure = outcome.failure();
                // Winner failure rollback: drop the placeholder (only
                // while the slot still holds it) and recycle the connection so
                // a retry starts clean; the surfaced failure keeps its cause.
                rollbackFailedWinner(placeholder, client, serverConfig.getServerId());
                throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_ADD_ERROR, null, null, failure,
                        Map.of("server_config", serverConfig.toMaskedDescription(), "reason",
                                failure.getMessage() != null ? failure.getMessage() : "add tool server failed"));
            }
            return outcome.value();
        } finally {
            registeringServers.remove(serverConfig.getServerId());
        }
    }

    /**
     * Connects the freshly created client with the baseline timeout guard
     * (configured positive value or the 30s fallback). A failed handshake
     * recycles the partially-connected client before surfacing the
     * cause-preserving failure, so no transport state leaks.
     *
     * @param client the client created for this registration attempt
     * @param serverConfig the config used for error context and the timeout
     * @throws Exception the wrapped add failure or the connection error
     * @since 0.1.16
     */
    private void connectClient(McpClient client, McpServerConfig serverConfig) throws Exception {
        Double configured = serverConfig.getConnectTimeoutSeconds();
        float connectTimeoutSec = configured != null && configured > 0 ? configured.floatValue() : 30f;
        IsolatedOutcome<Boolean> outcome =
                IsolatedActions.callIsolated(() -> client.connect(1, connectTimeoutSec));
        if (outcome.hasFailure()) {
            Throwable failure = outcome.failure();
            disconnectQuietly(client, serverConfig.getServerId());
            throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_ADD_ERROR, null, null, failure,
                    Map.of("server_config", serverConfig.toMaskedDescription(), "reason",
                            failure.getMessage() != null ? failure.getMessage() : "connect failed"));
        }
        if (!outcome.value()) {
            disconnectQuietly(client, serverConfig.getServerId());
            throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_CONNECTION_ERROR, "server_config",
                    serverConfig.toMaskedDescription(), "reason", "");
        }
    }

    /**
     * Dispatches the recognizable registration error for an occupied server
     * slot: equivalent connection semantics yield the already-registered
     * error the facade converts into a re-tag reuse; anything else is a
     * visible config conflict that never silently merges two servers.
     *
     * @param existing the config held by the registration that won
     * @param attempted the config of the losing attempt
     * @return the error to throw for the losing attempt
     * @since 0.1.16
     */
    private static BaseError registrationError(McpServerConfig existing, McpServerConfig attempted) {
        if (existing.sameConnectionAs(attempted)) {
            return new McpServerAlreadyRegisteredError(attempted.getServerId(), existing);
        }
        return new McpServerConfigConflictError(attempted.getServerId(), existing, attempted);
    }

    /**
     * Best-effort connection recycle on registration failure paths: the
     * primary error (loser dispatch or winner rollback) must not be masked
     * by a failing disconnect, so cleanup failures only log.
     *
     * @param client the client whose connection is recycled
     * @param serverId the server id used for log context
     * @since 0.1.16
     */
    private void disconnectQuietly(McpClient client, String serverId) {
        IsolatedActions.runIsolated(() -> {
            client.disconnect();
            return null;
        }).ifPresent(e -> logger.warn(
                "failed to disconnect MCP client during registration cleanup, server_id={}", serverId, e));
    }

    /**
     * Rolls back a winner whose discovery failed: the placeholder is
     * removed only while the slot still maps to it. Under the slot lock
     * no refresh can displace the placeholder anymore, so the
     * identity guard stays as defense in depth; the connection is
     * recycled exactly when the placeholder itself is dropped.
     *
     * @param placeholder the placeholder this registration claimed
     * @param client the client created for this registration attempt
     * @param serverId the claimed server id
     * @since 0.1.16
     */
    private void rollbackFailedWinner(McpServerResource placeholder, McpClient client, String serverId) {
        if (!mcpServerResources.remove(serverId, placeholder)) {
            return;
        }
        disconnectQuietly(client, serverId);
    }

    /**
     * Adds the server id to the name index. A config without server_name
     * (possible from user YAML) keeps the legacy HashMap-era behavior:
     * registration succeeds, the server is addressable by server_id, and
     * the name index simply does not track it — ConcurrentHashMap rejects
     * null keys, so guard here.
     *
     * @param serverConfig the successfully registered server config
     * @since 0.1.16
     */
    private void indexServerName(McpServerConfig serverConfig) {
        if (serverConfig.getServerName() != null) {
            mcpServerNameToIds.computeIfAbsent(serverConfig.getServerName(), k -> new CopyOnWriteArrayList<>())
                    .add(serverConfig.getServerId());
        }
    }

    /**
     * createClient. Protected for testability: tests substitute a mock
     * client to exercise server register/remove paths without a live
     * MCP endpoint.
     *
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    protected McpClient createClient(McpServerConfig config) {
        return McpClientFactory.create(config);
    }

    /**
     * Null-tolerant MCP server lookup preserving the former HashMap
     * semantics: a {@code null} serverId means "not found" instead of a
     * {@code ConcurrentHashMap} NullPointerException.
     *
     * @param serverId the MCP server identifier
     * @return the registered resource, or {@link Optional#empty()} when absent or the id is {@code null}
     */
    private Optional<McpServerResource> findMcpServerResource(String serverId) {
        return serverId == null ? Optional.empty() : Optional.ofNullable(mcpServerResources.get(serverId));
    }

    /**
     * Null-tolerant MCP server removal: a {@code null} serverId removes
     * nothing, matching the "server is not exist" branch of the callers.
     *
     * @param serverId the MCP server identifier
     * @return the removed resource, or {@link Optional#empty()} when absent or the id is {@code null}
     */
    private Optional<McpServerResource> removeMcpServerResource(String serverId) {
        return serverId == null ? Optional.empty() : Optional.ofNullable(mcpServerResources.remove(serverId));
    }

    /**
     * Null-tolerant system-operation lookup: a {@code null} sysOpId means
     * "not found" instead of a {@code ConcurrentHashMap} NullPointerException.
     *
     * @param sysOpId the system operation identifier
     * @return the registered resource, or {@link Optional#empty()} when absent or the id is {@code null}
     */
    private Optional<SysOpToolResource> findSysOpResource(String sysOpId) {
        return sysOpId == null ? Optional.empty() : Optional.ofNullable(sysOpResources.get(sysOpId));
    }

    /**
     * Null-tolerant system-operation removal: a {@code null} sysOpId removes
     * nothing and reports "not found" to the caller.
     *
     * @param sysOpId the system operation identifier
     * @return the removed resource, or {@link Optional#empty()} when absent or the id is {@code null}
     */
    private Optional<SysOpToolResource> removeSysOpResource(String sysOpId) {
        return sysOpId == null ? Optional.empty() : Optional.ofNullable(sysOpResources.remove(sysOpId));
    }

    /**
     * Retrieves all server identifiers associated with the given server name.
     * 
     * @param serverName the name of the MCP server
     * @return a list of server identifiers, or an empty list if none are found
     * @since 0.1.7
     */
    public List<String> getMcpServerIds(String serverName) {
        // ConcurrentHashMap rejects null keys; a null lookup returns empty,
        // matching the former HashMap behavior for unindexed names.
        if (serverName == null) {
            return Collections.emptyList();
        }
        // Return a snapshot — the internal value is a live
        // CopyOnWriteArrayList that concurrent register/remove paths mutate,
        // and callers must not observe (or mutate) internal state.
        List<String> ids = mcpServerNameToIds.get(serverName);
        return ids != null ? new ArrayList<>(ids) : Collections.emptyList();
    }

    /**
     * Returns the config for a registered MCP server, or {@code null} if unknown / blank id.
     *
     * @param serverId MCP server identifier
     * @return server config, or {@code null}
     * @since 0.1.14
     */
    public McpServerConfig getMcpServerConfig(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return null;
        }
        return findMcpServerResource(serverId).map(McpServerResource::config).orElse(null);
    }

    /**
     * Lists all registered MCP server identifiers.
     *
     * @return a new list of server ids (may be empty)
     * @since 0.1.14
     */
    public List<String> listMcpServerIds() {
        return new ArrayList<>(mcpServerResources.keySet());
    }

    /**
     * Returns whether the server slot holds a committed (established)
     * entry. A registration whose discovery is still in flight publishes a
     * placeholder: pure reads may observe it as a transient zero-tool
     * window, while writers and the read-time refresh path hold the
     * per-server slot lock and wait for the registration's terminal
     * state — the placeholder may still roll back.
     *
     * @param serverId the MCP server identifier
     * @return {@code true} only when the slot holds an established entry
     * @since 0.1.16
     */
    public boolean isMcpServerEstablished(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return false;
        }
        return mcpServerResources.containsKey(serverId) && !registeringServers.contains(serverId);
    }

    /**
     * Removes an MCP tool server and disconnects its client, cleaning up all associated tools.
     * The removal sequence runs serialized under the server's slot lock,
     * so it can never pass a mid-flight registration placeholder
     * and leave the winner's committed tools orphaned.
     * 
     * @param serverId the identifier of the MCP server to remove
     * @param ignoreNotExist whether to silently ignore a non-existent server
     * @return a list of tool identifiers that were removed
     * @throws Exception if the server does not exist and {@code ignoreNotExist} is {@code false}
     * @since 0.1.7
     */
    public List<String> removeToolServer(String serverId, boolean ignoreNotExist) throws Exception {
        // A null id removes nothing and never takes the lock.
        if (serverId == null) {
            return removeServerSlot(serverId, ignoreNotExist);
        }
        ReentrantLock slotLock = serverSlotLock(serverId);
        slotLock.lock();
        try {
            return removeServerSlot(serverId, ignoreNotExist);
        } finally {
            slotLock.unlock();
        }
    }

    /**
     * Removal sequence under the server slot lock: the entry
     * detach, the connection recycle, and the tool/index cleanup run as one
     * unit, so a concurrent registration can never commit into a slot the
     * removal already emptied.
     *
     * @param serverId the identifier of the MCP server to remove
     * @param shouldIgnoreMissing whether to silently ignore a non-existent server
     * @return a list of tool identifiers that were removed
     * @throws Exception if the server does not exist and {@code shouldIgnoreMissing} is {@code false}
     * @since 0.1.16
     */
    private List<String> removeServerSlot(String serverId, boolean shouldIgnoreMissing) throws Exception {
        Optional<McpServerResource> removed = removeMcpServerResource(serverId);
        if (removed.isEmpty()) {
            if (!shouldIgnoreMissing) {
                throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_REMOVE_ERROR, "server_id", serverId,
                        "reason", "server is not exist");
            }
            return Collections.emptyList();
        }
        McpServerResource resource = removed.get();
        disconnectQuietly(resource.client(), serverId);
        innerRemoveMcpTools(resource.toolIds());
        removeServerIdFromNameIndex(resource, serverId);
        // Snapshot for consistency with the other list-returning
        // accessors; the resource is already detached from the registry.
        return new ArrayList<>(resource.toolIds());
    }

    /**
     * Drops the server id from the name index. The former
     * get -> remove -> isEmpty -> remove(key) sequence was a non-atomic
     * compound over a plain HashMap; this update is atomic per server name,
     * so concurrent unregister calls serialize instead of racing. Servers
     * registered without a name are not tracked by the index.
     *
     * @param resource the removed MCP server resource
     * @param serverId the removed MCP server identifier
     */
    private void removeServerIdFromNameIndex(McpServerResource resource, String serverId) {
        String serverName = resource.config().getServerName();
        if (serverName == null) {
            return;
        }
        List<String> ids = mcpServerNameToIds.get(serverName);
        if (ids == null) {
            return;
        }
        // COW list mutation under the CHM bin lock held by the compute:
        // concurrent removers of the same name serialize here, and the key
        // is dropped exactly once when the id list drains.
        mcpServerNameToIds.computeIfPresent(serverName, (name, currentIds) -> {
            currentIds.remove(serverId);
            return currentIds.isEmpty() ? null : currentIds;
        });
    }

    /**
     * Removes an MCP tool server, silently ignoring if it does not exist.
     * 
     * @param serverId the identifier of the MCP server to remove
     * @return a list of tool identifiers that were removed
     * @throws Exception if disconnecting from the server fails
     * @since 0.1.7
     */
    public List<String> removeToolServer(String serverId) throws Exception {
        return removeToolServer(serverId, true);
    }

    /**
     * Associates a list of tool identifiers with a system operation.
     * 
     * @param sysOpId the identifier of the system operation
     * @param toolIds the list of tool identifiers to associate
     * @since 0.1.7
     */
    public void addSysOperationTools(String sysOpId, List<String> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return;
        }
        sysOpResources.put(sysOpId,
                new SysOpToolResource(sysOpId, new ArrayList<>(toolIds), System.currentTimeMillis()));
    }

    /**
     * Removes the system operation and returns its associated tool identifiers.
     * 
     * @param sysOpId the identifier of the system operation to remove
     * @return a list of tool identifiers that were associated, or an empty list if not found
     * @since 0.1.7
     */
    public List<String> removeSysOperationTools(String sysOpId) {
        // Snapshot for consistency with the other list-returning
        // accessors, even though the resource is already detached from the
        // registry at this point.
        return removeSysOpResource(sysOpId)
                .<List<String>>map(resource -> new ArrayList<>(resource.toolIds()))
                .orElseGet(Collections::emptyList);
    }

    /**
     * Retrieves the tool identifiers associated with the given system operation.
     * 
     * @param sysOpId the identifier of the system operation
     * @return a list of tool identifiers, or an empty list if the operation is not found
     * @since 0.1.7
     */
    public List<String> getSysOperationToolIds(String sysOpId) {
        // Return a snapshot of the resource's id list so callers
        // never hold a reference to mutable internal state.
        return findSysOpResource(sysOpId)
                .<List<String>>map(resource -> new ArrayList<>(resource.toolIds()))
                .orElseGet(Collections::emptyList);
    }

    /**
     * Refreshes the tool list for the specified MCP server if expired or forced.
     * 
     * @param serverId the identifier of the MCP server to refresh
     * @param skipNotExist whether to silently skip if the server does not exist
     * @param force whether to force a refresh regardless of expiry
     * @return a list of refreshed tool cards, or an empty list if no refresh was needed
     * @throws Exception if the server does not exist and {@code skipNotExist} is {@code false}, or if refresh fails
     * @since 0.1.7
     */
    public List<McpToolCard> refreshToolServer(String serverId, boolean skipNotExist, boolean force) throws Exception {
        // Slot serialization: a refresh observing a mid-flight
        // placeholder adopted a client whose registration could still roll
        // back — the refresh now waits for the registration's terminal
        // state. A null id finds nothing and never takes the lock.
        if (serverId == null) {
            return refreshServerSlot(serverId, skipNotExist, force);
        }
        ReentrantLock slotLock = serverSlotLock(serverId);
        slotLock.lock();
        try {
            return refreshServerSlot(serverId, skipNotExist, force);
        } finally {
            slotLock.unlock();
        }
    }

    /**
     * Refresh sequence under the server slot lock: the lookup,
     * the expiry decision, and the re-discovery run against an entry that
     * cannot change underneath them — never against a placeholder.
     *
     * @param serverId the identifier of the MCP server to refresh
     * @param shouldSkipMissing whether to silently skip if the server does not exist
     * @param shouldForce whether to force a refresh regardless of expiry
     * @return a list of refreshed tool cards, or an empty list if no refresh was needed
     * @throws Exception if the server does not exist and
     *         {@code shouldSkipMissing} is {@code false}, or if refresh fails
     * @since 0.1.16
     */
    private List<McpToolCard> refreshServerSlot(String serverId, boolean shouldSkipMissing, boolean shouldForce)
            throws Exception {
        Optional<McpServerResource> resource = findMcpServerResource(serverId);
        if (resource.isEmpty()) {
            if (!shouldSkipMissing) {
                throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_REFRESH_ERROR, "server_id", serverId,
                        "reason", "server is not exist");
            }
            return Collections.emptyList();
        }
        McpServerResource mcpResource = resource.get();
        boolean shouldRefresh = shouldForce;
        if (!shouldForce && mcpResource.expiryTime() != null) {
            if (System.currentTimeMillis() - mcpResource.lastUpdateTime() >= mcpResource.expiryTime()) {
                shouldRefresh = true;
            }
        }
        if (shouldRefresh) {
            return innerRefreshMcpTools(mcpResource.client(), mcpResource.config(), mcpResource.expiryTime());
        }
        return Collections.emptyList();
    }

    /**
     * Releases all resources by disconnecting MCP servers and clearing all managed collections.
     * 
     * @since 0.1.7
     */
    public void release() {
        // The slot locks and the registering marks are
        // intentionally not cleared: releasing lock entries while waiters
        // exist would let freshly created locks break mutual exclusion, and
        // a stale registering mark only disables the fast path — the next
        // registration of the same id re-establishes it under the lock.
        for (McpServerResource resource : mcpServerResources.values()) {
            disconnectQuietly(resource.client(), resource.config().getServerId());
        }
        mcpServerResources.clear();
        mcpServerNameToIds.clear();
        tools.clear();
        sysOpResources.clear();
    }

    /**
     * innerRefreshMcpTools.
     * 
     * @param client client
     * @param serverConfig serverConfig
     * @param expiryTime expiryTime
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    private List<McpToolCard> innerRefreshMcpTools(McpClient client, McpServerConfig serverConfig, Double expiryTime)
            throws Exception {
        // Bounded discovery RPC: callTimeoutSeconds when
        // positive, else the 30s fallback — the NO_TIMEOUT sentinel never
        // reaches the client on the registration/refresh paths.
        List<Object> rawCards = client.listTools(discoveryTimeout(serverConfig));
        List<McpToolCard> mcpCards = new ArrayList<>();
        if (rawCards != null) {
            for (Object raw : rawCards) {
                if (raw instanceof McpToolCard card) {
                    mcpCards.add(card);
                }
            }
        }
        List<String> mcpIds = new ArrayList<>();
        for (McpToolCard card : mcpCards) {
            String toolId = generateMcpToolId(serverConfig.getServerId(), serverConfig.getServerName(), card.getName());
            card.setId(toolId);
            // Slot-owned id namespace: a generated MCP tool id
            // embeds this server's id and name, so re-discovery replaces
            // the entry this server already registered instead of
            // colliding with its own earlier registration — a refresh
            // after commit stays idempotent. Only a foreign tool squatted
            // on the generated id stays a visible error.
            Tool occupied = tools.get(toolId);
            if (occupied != null && !(occupied instanceof McpTool)) {
                throw new IllegalArgumentException("already exist tool " + toolId);
            }
            tools.put(toolId, new McpTool(client, card, executionCallTimeout(serverConfig)));
            mcpIds.add(toolId);
        }
        // Placeholder update: replace the entry only while it
        // still maps to this client — the registration winner refreshes
        // its own placeholder and the read path refreshes the entry it
        // read. Under the per-server slot lock no writer
        // interleave remains; the client guard stays as defense in depth.
        McpServerResource refreshed = new McpServerResource(serverConfig, client, new ArrayList<>(mcpIds),
                System.currentTimeMillis(), expiryTime);
        mcpServerResources.computeIfPresent(serverConfig.getServerId(),
                (serverId, current) -> current.client() == client ? refreshed : current);
        return mcpCards;
    }

    /**
     * Resolves the bounded timeout for the discovery RPC: the configured
     * positive callTimeoutSeconds or the 30s fallback.
     *
     * @param serverConfig the config whose call timeout applies
     * @return the timeout in seconds passed to the client
     * @since 0.1.16
     */
    private static float discoveryTimeout(McpServerConfig serverConfig) {
        Double callTimeout = serverConfig.getCallTimeoutSeconds();
        return callTimeout != null && callTimeout > 0 ? callTimeout.floatValue() : 30f;
    }

    /**
     * Resolves the configured execution timeout for MCP tool calls: a
     * positive callTimeoutSeconds is passed through so long-running
     * tools honor the server configuration; any other value keeps the
     * {@link McpServerConfig#NO_TIMEOUT} sentinel, which preserves the
     * baseline unbounded execution semantics (only the discovery RPC is bounded; see {@link #discoveryTimeout}).
     *
     * @param serverConfig the config whose call timeout applies
     * @return the timeout in seconds passed to the client
     * @since 0.1.16
     */
    private static float executionCallTimeout(McpServerConfig serverConfig) {
        Double callTimeout = serverConfig.getCallTimeoutSeconds();
        return callTimeout != null && callTimeout > 0 ? callTimeout.floatValue() : McpServerConfig.NO_TIMEOUT;
    }

    /**
     * innerRemoveMcpTools.
     * 
     * @param toolIds toolIds
     * @since 0.1.7
     */
    private void innerRemoveMcpTools(List<String> toolIds) {
        if (toolIds == null) {
            return;
        }
        for (String toolId : toolIds) {
            tools.remove(toolId);
        }
    }

    // ========== Inner record types ==========

    /**
     * Public record McpServerResource used by the Java parity implementation.
     * 
     * @since 0.1.7
     */
    public record McpServerResource(McpServerConfig config, McpClient client, List<String> toolIds, long lastUpdateTime,
            Double expiryTime) {
    }

    /**
     * Public record SysOpToolResource used by the Java parity implementation.
     * 
     * @since 0.1.7
     */
    public record SysOpToolResource(String sysOpId, List<String> toolIds, long lastUpdateTime) {
    }
}
