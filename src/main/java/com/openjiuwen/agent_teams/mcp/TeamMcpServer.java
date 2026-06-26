/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_teams.AgentTeamI18n;
import com.openjiuwen.agent_teams.AgentTeamTimefmt;
import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.external.ExternalFormat;
import com.openjiuwen.agent_teams.external.ExternalTeamClient;
import com.openjiuwen.agent_teams.external.InboxView;
import com.openjiuwen.agent_teams.external.TeamJoinDescriptor;
import com.openjiuwen.agent_teams.schema.TaskDetail;
import com.openjiuwen.agent_teams.schema.TaskOpResult;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.tools.TeamMember;
import com.openjiuwen.agent_teams.tools.TeamMessage;
import com.openjiuwen.agent_teams.tools.TeamTask;
import com.openjiuwen.agent_teams.tools.database.TeamDatabase;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Role-scoped team-member MCP tool service.
 *
 * <p>Mirrors Python's {@code build_server}, tool registry, and module constants in
 * {@code openjiuwen/agent_teams/mcp/server.py}.</p>
 */
public final class TeamMcpServer {

    public static final String SERVER_NAME = "openjiuwen-team-member";

    public static final String INSTRUCTIONS = """
            You are a member of an OpenJiuWen agent team. Collaborate with the leader and
            teammates over a shared task board and mailbox using these tools. The team
            does not see your free text, your reasoning, or your local file edits - only
            the tool calls you make here are visible to the team.

            Workflow for an assigned task - do every step, in order:
            1. read_inbox / get_task - read the full requirement and the task_id.
            2. claim_task(task_id) - take ownership.
            3. Do the actual work (e.g. write the requested file).
            4. complete_task(task_id) - MANDATORY. The task stays OPEN until you call it;
               writing a file or replying in plain text does NOT complete the task.
            5. send_message to the leader - MANDATORY. Report what you did.

            A turn is finished only after BOTH complete_task AND send_message have been
            called. Do not stop after merely claiming or after doing the work - that
            leaves the task unfinished and the leader waiting. Refer to members by name.
            An empty inbox is normal; reading it marks messages read.
            """;

    public static final String TOOL_READ_INBOX = "read_inbox";
    public static final String TOOL_SEND_MESSAGE = "send_message";
    public static final String TOOL_LIST_TASKS = "list_tasks";
    public static final String TOOL_CLAIMABLE_TASKS = "claimable_tasks";
    public static final String TOOL_GET_TASK = "get_task";
    public static final String TOOL_CLAIM_TASK = "claim_task";
    public static final String TOOL_COMPLETE_TASK = "complete_task";
    public static final String TOOL_UPDATE_TASK = "update_task";
    public static final String TOOL_LIST_MEMBERS = "list_members";

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final TypeReference<LinkedHashMap<String, Object>> STRING_OBJECT_MAP =
            new TypeReference<>() {
            };

    private static final Set<String> READ_AND_MESSAGE_TOOLS = Set.of(
            TOOL_READ_INBOX,
            TOOL_SEND_MESSAGE,
            TOOL_LIST_TASKS,
            TOOL_CLAIMABLE_TASKS,
            TOOL_GET_TASK,
            TOOL_LIST_MEMBERS
    );
    private static final Set<String> TASK_LIFECYCLE_TOOLS = Set.of(
            TOOL_CLAIM_TASK,
            TOOL_COMPLETE_TASK,
            TOOL_UPDATE_TASK
    );
    private static final Set<String> ALL_TOOLS = Set.copyOf(union(READ_AND_MESSAGE_TOOLS, TASK_LIFECYCLE_TOOLS));

    private final ClientHolder holder;
    private final Set<String> allowedTools;
    private final LinkedHashMap<String, ToolHandler> toolRegistry;

    private TeamMcpServer(ClientFactory clientFactory, String role) {
        this.holder = new ClientHolder(clientFactory);
        this.allowedTools = allowedToolsForRole(role);
        this.toolRegistry = buildRegistry();
        TEAM_LOGGER.info("team MCP server tools for role={}: {}", role, new ArrayList<>(allowedTools));
    }

    public static TeamMcpServer buildServer() {
        return buildServer(TeamMcpServer::connectFromEnv);
    }

    public static TeamMcpServer buildServer(ClientFactory clientFactory) {
        return buildServer(clientFactory, (String) null);
    }

    public static TeamMcpServer buildServer(ClientFactory clientFactory, String role) {
        String resolvedRole = role == null ? TeamJoinDescriptor.fromEnv().getRole() : role;
        return new TeamMcpServer(clientFactory, resolvedRole);
    }

    public static TeamMcpServer buildServerFromEnv(ClientFactory clientFactory, Map<String, String> env) {
        return new TeamMcpServer(clientFactory, TeamJoinDescriptor.fromEnv(env).getRole());
    }

    public static CompletionStage<TeamClientFacade> connectFromEnv() {
        ExternalTeamClient client = new ExternalTeamClient(TeamJoinDescriptor.fromEnv());
        return client.connect().thenApply(ignored -> new ExternalClientFacade(client));
    }

    public static Set<String> readAndMessageTools() {
        return READ_AND_MESSAGE_TOOLS;
    }

    public static Set<String> taskLifecycleTools() {
        return TASK_LIFECYCLE_TOOLS;
    }

    public static Set<String> allTools() {
        return ALL_TOOLS;
    }

    public static Set<String> allowedToolsForRole(String role) {
        String normalized = role == null ? "" : role.toLowerCase(Locale.ROOT);
        try {
            TeamRole resolved = TeamRole.fromValue(normalized);
            if (resolved == TeamRole.LEADER || resolved == TeamRole.TEAMMATE) {
                return ALL_TOOLS;
            }
            return READ_AND_MESSAGE_TOOLS;
        } catch (IllegalArgumentException exception) {
            TEAM_LOGGER.warning(
                    "unknown member role {} for team MCP server; exposing read/messaging tools only",
                    role
            );
            return READ_AND_MESSAGE_TOOLS;
        }
    }

    public List<TeamMcpTool> listTools() {
        List<TeamMcpTool> tools = new ArrayList<>();
        for (String name : toolRegistry.keySet()) {
            if (allowedTools.contains(name)) {
                tools.add(new TeamMcpTool(name));
            }
        }
        return tools;
    }

    public CompletionStage<Object> callTool(String toolName, Map<String, Object> arguments) {
        if (!allowedTools.contains(toolName)) {
            return failedStage(new IllegalArgumentException("Tool is not allowed for this role: " + toolName));
        }
        ToolHandler handler = toolRegistry.get(toolName);
        if (handler == null) {
            return failedStage(new IllegalArgumentException("Unknown MCP tool: " + toolName));
        }
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        return handler.call(safeArguments);
    }

    public Map<String, Object> run(String transport) {
        String resolvedTransport = transport == null || transport.isBlank() ? "stdio" : transport;
        if (!"stdio".equals(resolvedTransport)) {
            throw new IllegalArgumentException("Unsupported transport: " + resolvedTransport);
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("server", SERVER_NAME);
        metadata.put("transport", resolvedTransport);
        metadata.put("instructions", INSTRUCTIONS);
        metadata.put("tools", listTools().stream().map(TeamMcpTool::name).toList());
        return metadata;
    }

    public static void main(String[] args) {
        buildServer().run("stdio");
    }

    private LinkedHashMap<String, ToolHandler> buildRegistry() {
        LinkedHashMap<String, ToolHandler> registry = new LinkedHashMap<>();
        registry.put(TOOL_READ_INBOX, ignored -> readInbox().thenApply(value -> value));
        registry.put(TOOL_SEND_MESSAGE, args -> sendMessage(stringArg(args, "to"), stringArg(args, "content"))
                .thenApply(value -> value));
        registry.put(TOOL_LIST_TASKS, args -> listTasks(stringArg(args, "status")).thenApply(value -> value));
        registry.put(TOOL_CLAIMABLE_TASKS, ignored -> claimableTasks().thenApply(value -> value));
        registry.put(TOOL_GET_TASK, args -> getTask(stringArg(args, "task_id")).thenApply(value -> value));
        registry.put(TOOL_CLAIM_TASK, args -> claimTask(stringArg(args, "task_id")).thenApply(value -> value));
        registry.put(TOOL_COMPLETE_TASK, args -> completeTask(stringArg(args, "task_id")).thenApply(value -> value));
        registry.put(TOOL_UPDATE_TASK, args -> updateTask(
                stringArg(args, "task_id"),
                stringArg(args, "title"),
                stringArg(args, "content")
        ).thenApply(value -> value));
        registry.put(TOOL_LIST_MEMBERS, ignored -> listMembers().thenApply(value -> value));
        return registry;
    }

    private CompletionStage<Map<String, Object>> readInbox() {
        return holder.get().thenCompose(client -> client.fetchInbox(true)
                .thenApply(view -> renderInbox(client, view)));
    }

    private CompletionStage<Map<String, Object>> sendMessage(String to, String content) {
        return holder.get().thenCompose(client -> client.sendMessage(to, content)
                .thenApply(messageId -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("ok", messageId != null);
                    result.put("message_id", messageId);
                    return result;
                }));
    }

    private CompletionStage<List<Map<String, Object>>> listTasks(String status) {
        return holder.get().thenCompose(client -> client.listTasks(status)
                .thenApply(tasks -> {
                    long nowMs = TeamDatabase.getCurrentTime();
                    List<Map<String, Object>> result = new ArrayList<>();
                    for (TeamTask task : tasks) {
                        result.add(taskEntry(task, nowMs));
                    }
                    return result;
                }));
    }

    private CompletionStage<List<Map<String, Object>>> claimableTasks() {
        return holder.get().thenCompose(client -> client.claimableTasks()
                .thenApply(tasks -> {
                    List<Map<String, Object>> result = new ArrayList<>();
                    for (TeamTask task : tasks) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("task_id", task.getTaskId());
                        item.put("title", task.getTitle());
                        result.add(item);
                    }
                    return result;
                }));
    }

    private CompletionStage<Map<String, Object>> getTask(String taskId) {
        return holder.get().thenCompose(client -> client.getTask(taskId)
                .thenApply(detail -> detail == null ? null : OBJECT_MAPPER.convertValue(detail, STRING_OBJECT_MAP)));
    }

    private CompletionStage<Map<String, Object>> claimTask(String taskId) {
        return holder.get().thenCompose(client -> client.claimTask(taskId).thenApply(TeamMcpServer::opResult));
    }

    private CompletionStage<Map<String, Object>> completeTask(String taskId) {
        return holder.get().thenCompose(client -> client.completeTask(taskId).thenApply(TeamMcpServer::opResult));
    }

    private CompletionStage<Map<String, Object>> updateTask(String taskId, String title, String content) {
        return holder.get().thenCompose(client -> client.updateTask(taskId, title, content)
                .thenApply(TeamMcpServer::opResult));
    }

    private CompletionStage<List<Map<String, Object>>> listMembers() {
        return holder.get().thenCompose(client -> client.listMembers()
                .thenApply(members -> {
                    List<Map<String, Object>> result = new ArrayList<>();
                    for (TeamMember member : members) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("member_name", member.getMemberName());
                        item.put("role", member.getRole());
                        item.put("status", member.getStatus());
                        result.add(item);
                    }
                    return result;
                }));
    }

    private static Map<String, Object> renderInbox(TeamClientFacade client, InboxView view) {
        long nowMs = TeamDatabase.getCurrentTime();
        List<Map<String, Object>> messages = new ArrayList<>();
        for (TeamMessage message : view.messages()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("message_id", message.getMessageId());
            item.put("from", message.getFromMemberName());
            item.put("content", message.getContent());
            item.put("broadcast", Boolean.TRUE.equals(message.getBroadcast()));
            item.put("time", AgentTeamTimefmt.formatTimeContext(message.getTimestamp(), nowMs));
            messages.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("messages", messages);
        result.put("task_board", ExternalFormat.renderTaskBoard(
                view.tasks().stream().map(TaskAdapter::new).toList(),
                client.isLeader(),
                nowMs
        ));
        return result;
    }

    private static Map<String, Object> taskEntry(TeamTask task, long nowMs) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("task_id", task.getTaskId());
        item.put("title", task.getTitle());
        item.put("status", task.getStatus());
        item.put("assignee", task.getAssignee());
        item.put("time", AgentTeamTimefmt.formatTimeContext(task.getUpdatedAt(), nowMs));
        return item;
    }

    private static Map<String, Object> opResult(TaskOpResult result) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("ok", result.ok());
        item.put("reason", result.reason());
        return item;
    }

    private static String stringArg(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static Set<String> union(Set<String> left, Set<String> right) {
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>(left);
        result.addAll(right);
        return result;
    }

    private static void bindSessionContext(TeamClientFacade client) {
        AgentTeamsContext.setSessionId(client.sessionId());
        AgentTeamI18n.setLanguage(client.language());
    }

    private static <T> CompletionStage<T> failedStage(Throwable throwable) {
        CompletableFuture<T> failed = new CompletableFuture<>();
        failed.completeExceptionally(throwable);
        return failed;
    }

    /**
     * Async callable that returns a connected team client.
     *
     * <p>Mirrors Python's {@code ClientFactory} in
     * {@code openjiuwen/agent_teams/mcp/server.py}.</p>
     */
    @FunctionalInterface
    public interface ClientFactory {
        CompletionStage<TeamClientFacade> connect();
    }

    /**
     * Minimal client surface consumed by the MCP tool handlers.
     *
     * <p>Mirrors Python's {@code ExternalTeamClient} usage in
     * {@code openjiuwen/agent_teams/mcp/server.py}.</p>
     */
    public interface TeamClientFacade {
        String sessionId();

        String language();

        boolean isLeader();

        CompletionStage<InboxView> fetchInbox(boolean markRead);

        CompletionStage<String> sendMessage(String to, String content);

        CompletionStage<List<TeamTask>> listTasks(String status);

        CompletionStage<List<TeamTask>> claimableTasks();

        CompletionStage<TaskDetail> getTask(String taskId);

        CompletionStage<TaskOpResult> claimTask(String taskId);

        CompletionStage<TaskOpResult> completeTask(String taskId);

        CompletionStage<TaskOpResult> updateTask(String taskId, String title, String content);

        CompletionStage<List<TeamMember>> listMembers();
    }

    /**
     * Listed MCP tool metadata.
     *
     * <p>Mirrors Python's FastMCP tool listing in
     * {@code openjiuwen/agent_teams/mcp/server.py}.</p>
     */
    public record TeamMcpTool(String name) {
        public TeamMcpTool {
            Objects.requireNonNull(name, "name");
        }
    }

    /**
     * Tool callback used by the role-scoped registry.
     *
     * <p>Mirrors Python's {@code tool_registry} callables in
     * {@code openjiuwen/agent_teams/mcp/server.py}.</p>
     */
    @FunctionalInterface
    private interface ToolHandler {
        CompletionStage<Object> call(Map<String, Object> arguments);
    }

    /**
     * Lazily connects the client once and re-binds per-call session context.
     *
     * <p>Mirrors Python's {@code _ClientHolder} in
     * {@code openjiuwen/agent_teams/mcp/server.py}.</p>
     */
    private static final class ClientHolder {
        private final ClientFactory factory;
        private CompletionStage<TeamClientFacade> clientStage;

        private ClientHolder(ClientFactory factory) {
            this.factory = Objects.requireNonNull(factory, "factory");
        }

        private CompletionStage<TeamClientFacade> get() {
            if (clientStage == null) {
                clientStage = factory.connect();
            }
            return clientStage.thenApply(client -> {
                bindSessionContext(client);
                return client;
            });
        }
    }

    /**
     * Production facade over {@link ExternalTeamClient}.
     *
     * <p>Mirrors Python's {@code ExternalTeamClient} binding in
     * {@code openjiuwen/agent_teams/mcp/server.py}.</p>
     */
    private record ExternalClientFacade(ExternalTeamClient client) implements TeamClientFacade {
        private ExternalClientFacade {
            Objects.requireNonNull(client, "client");
        }

        @Override
        public String sessionId() {
            return client.getSessionId();
        }

        @Override
        public String language() {
            return client.getLanguage();
        }

        @Override
        public boolean isLeader() {
            return client.isLeader();
        }

        @Override
        public CompletionStage<InboxView> fetchInbox(boolean markRead) {
            return client.fetchInbox(markRead);
        }

        @Override
        public CompletionStage<String> sendMessage(String to, String content) {
            return client.sendMessage(to, content);
        }

        @Override
        public CompletionStage<List<TeamTask>> listTasks(String status) {
            return client.listTasks(status);
        }

        @Override
        public CompletionStage<List<TeamTask>> claimableTasks() {
            return client.claimableTasks();
        }

        @Override
        public CompletionStage<TaskDetail> getTask(String taskId) {
            return client.getTask(taskId);
        }

        @Override
        public CompletionStage<TaskOpResult> claimTask(String taskId) {
            return client.claimTask(taskId);
        }

        @Override
        public CompletionStage<TaskOpResult> completeTask(String taskId) {
            return client.completeTask(taskId);
        }

        @Override
        public CompletionStage<TaskOpResult> updateTask(String taskId, String title, String content) {
            return client.updateTask(taskId, title, content);
        }

        @Override
        public CompletionStage<List<TeamMember>> listMembers() {
            return client.listMembers();
        }
    }

    /**
     * Adapter from existing team task model to external formatter protocol.
     *
     * <p>Mirrors Python's task-board rendering input in
     * {@code openjiuwen/agent_teams/mcp/server.py}.</p>
     */
    private record TaskAdapter(TeamTask task) implements ExternalFormat.TaskLike {
        private TaskAdapter {
            Objects.requireNonNull(task, "task");
        }

        @Override
        public String taskId() {
            return task.getTaskId();
        }

        @Override
        public String title() {
            return task.getTitle();
        }

        @Override
        public String content() {
            return task.getContent();
        }

        @Override
        public String status() {
            return task.getStatus();
        }

        @Override
        public String assignee() {
            return task.getAssignee();
        }

        @Override
        public Long updatedAt() {
            return task.getUpdatedAt();
        }
    }
}
