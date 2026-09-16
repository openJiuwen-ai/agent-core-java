/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manager for Tool instances, MCP servers, and SysOperation-related tools.
 * <p>
 * Provides registration, lookup, and lifecycle management for tools and MCP servers.
 * MCP client creation is delegated to {@link McpClientFactory} for SPI-based transport selection.
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
        // Fast path (former containsKey guard): an established
        // entry means this call lost before building any connection; the
        // dispatch keeps equivalent and conflicting attempts separable.
        McpServerResource established = mcpServerResources.get(serverConfig.getServerId());
        if (established != null) {
            throw registrationError(established.config(), serverConfig);
        }
        McpClient client = createClient(serverConfig);
        connectClient(client, serverConfig);
        // Placeholder claim: connecting is the slow step and stays outside
        // the claim, then exactly one registration wins the server slot.
        McpServerResource placeholder = new McpServerResource(serverConfig, client, List.of(),
                System.currentTimeMillis(), expiryTime);
        McpServerResource prev = mcpServerResources.putIfAbsent(serverConfig.getServerId(), placeholder);
        if (prev != null) {
            // Loser: recycle own connection, then dispatch by connection
            // equivalence — the facade re-tags equivalents into the
            // winner's entry, conflicts stay visibly reportable.
            disconnectQuietly(client, serverConfig.getServerId());
            throw registrationError(prev.config(), serverConfig);
        }
        try {
            List<McpToolCard> results = innerRefreshMcpTools(client, serverConfig, expiryTime);
            indexServerName(serverConfig);
            return results;
        } catch (Exception e) {
            // Winner failure rollback: drop the placeholder (only
            // while the slot still holds it) and recycle the connection so
            // a retry starts clean; the surfaced failure keeps its cause.
            rollbackFailedWinner(placeholder, client, serverConfig.getServerId());
            throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_ADD_ERROR, null, null, e,
                    Map.of("server_config", serverConfig.toMaskedDescription(), "reason",
                            e.getMessage() != null ? e.getMessage() : "add tool server failed"));
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
        boolean isConnected;
        try {
            isConnected = client.connect(1, connectTimeoutSec);
        } catch (Exception e) {
            disconnectQuietly(client, serverConfig.getServerId());
            throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_ADD_ERROR, null, null, e,
                    Map.of("server_config", serverConfig.toMaskedDescription(), "reason",
                            e.getMessage() != null ? e.getMessage() : "connect failed"));
        }
        if (!isConnected) {
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
        try {
            client.disconnect();
        } catch (Exception e) {
            logger.warn("failed to disconnect MCP client during registration cleanup, server_id={}", serverId, e);
        }
    }

    /**
     * Rolls back a winner whose discovery failed: the placeholder is
     * removed only while the slot still maps to it — a concurrent refresh
     * may legitimately have replaced it with a live resource on the same
     * client, and that entry keeps the connection. The connection is
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
     * Removes an MCP tool server and disconnects its client, cleaning up all associated tools.
     * 
     * @param serverId the identifier of the MCP server to remove
     * @param ignoreNotExist whether to silently ignore a non-existent server
     * @return a list of tool identifiers that were removed
     * @throws Exception if the server does not exist and {@code ignoreNotExist} is {@code false}
     * @since 0.1.7
     */
    public List<String> removeToolServer(String serverId, boolean ignoreNotExist) throws Exception {
        Optional<McpServerResource> removed = removeMcpServerResource(serverId);
        if (removed.isEmpty()) {
            if (!ignoreNotExist) {
                throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_REMOVE_ERROR, "server_id", serverId,
                        "reason", "server is not exist");
            }
            return Collections.emptyList();
        }
        McpServerResource resource = removed.get();
        try {
            resource.client().disconnect();
        } catch (Exception e) {
            logger.warn("remove tool server disconnect {}, server_id={}", e.getMessage(), serverId);
        } finally {
            innerRemoveMcpTools(resource.toolIds());
            removeServerIdFromNameIndex(resource, serverId);
        }
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
        Optional<McpServerResource> resource = findMcpServerResource(serverId);
        if (resource.isEmpty()) {
            if (!skipNotExist) {
                throw ErrorHelper.buildError(StatusCode.RESOURCE_MCP_SERVER_REFRESH_ERROR, "server_id", serverId,
                        "reason", "server is not exist");
            }
            return Collections.emptyList();
        }
        McpServerResource mcpResource = resource.get();
        boolean needRefresh = force;
        if (!force && mcpResource.expiryTime() != null) {
            if (System.currentTimeMillis() - mcpResource.lastUpdateTime() >= mcpResource.expiryTime()) {
                needRefresh = true;
            }
        }
        if (needRefresh) {
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
        for (McpServerResource resource : mcpServerResources.values()) {
            try {
                resource.client().disconnect();
            } catch (Exception e) {
                logger.warn("Failed to disconnect MCP server: {}", e.getMessage());
            }
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
            addTool(toolId, new McpTool(client, card, executionCallTimeout(serverConfig)));
            mcpIds.add(toolId);
        }
        // Placeholder update: replace the entry only while it
        // still maps to this client — the registration winner refreshes
        // its own placeholder and the read path refreshes the entry it
        // read; a slot owned by another registration is never overwritten.
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
