/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_teams.AgentTeamI18n;
import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.messager.Messagers;
import com.openjiuwen.agent_teams.schema.GraphMutationResult;
import com.openjiuwen.agent_teams.schema.NewTaskSpec;
import com.openjiuwen.agent_teams.schema.TaskDetail;
import com.openjiuwen.agent_teams.schema.TaskOpResult;
import com.openjiuwen.agent_teams.schema.TeamTopic;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.spawn.SharedResources;
import com.openjiuwen.agent_teams.tools.InMemoryTeamDatabase;
import com.openjiuwen.agent_teams.tools.Team;
import com.openjiuwen.agent_teams.tools.TeamMember;
import com.openjiuwen.agent_teams.tools.TeamMessage;
import com.openjiuwen.agent_teams.tools.TeamMessageManager;
import com.openjiuwen.agent_teams.tools.TeamTask;
import com.openjiuwen.agent_teams.tools.TeamTaskDependency;
import com.openjiuwen.agent_teams.tools.TeamTaskManager;
import com.openjiuwen.agent_teams.tools.database.DatabaseConfig;
import com.openjiuwen.agent_teams.tools.database.MemberDao;
import com.openjiuwen.agent_teams.tools.database.MessageDao;
import com.openjiuwen.agent_teams.tools.database.TaskDao;
import com.openjiuwen.agent_teams.tools.database.TeamDao;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Attach an external agent to a running team via shared db and messager.
 *
 * <p>Mirrors Python's {@code ExternalTeamClient} in
 * {@code openjiuwen/agent_teams/external/client.py}.</p>
 */
public class ExternalTeamClient implements AutoCloseable {

    public static final String BROADCAST_TARGET = "*";

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final TeamJoinDescriptor descriptor;
    private final InMemoryTeamDatabase injectedDatabase;
    private final Messager injectedMessager;

    private Messager messager;
    private TeamTaskManager tasks;
    private TeamMessageManager messages;
    private ClientDatabaseView databaseView;
    private AgentTeamsContext.SessionIdToken sessionToken;
    private boolean connected;

    public ExternalTeamClient(TeamJoinDescriptor descriptor) {
        this(descriptor, null, null);
    }

    ExternalTeamClient(TeamJoinDescriptor descriptor, InMemoryTeamDatabase database, Messager messager) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.injectedDatabase = database;
        this.injectedMessager = messager;
    }

    public String getSessionId() {
        return descriptor.getSessionId();
    }

    public String getLanguage() {
        return descriptor.getLanguage();
    }

    public String getMemberName() {
        return descriptor.getMemberName();
    }

    public String getTeamName() {
        return descriptor.getTeamName();
    }

    public boolean isLeader() {
        return "leader".equals(descriptor.getRole());
    }

    public CompletionStage<Void> connect() {
        return supplyStage(() -> {
            if (connected) {
                return null;
            }

            AgentTeamI18n.setLanguage(descriptor.getLanguage());
            sessionToken = AgentTeamsContext.setSessionId(descriptor.getSessionId());
            databaseView = injectedDatabase == null
                    ? openSharedDatabase(descriptor.getDbConfig())
                    : ClientDatabaseView.from(injectedDatabase);
            messager = injectedMessager == null
                    ? Messagers.createMessager(descriptor.getTransportConfig())
                    : injectedMessager;
            join(messager.start());

            tasks = databaseView.createTaskManager(getTeamName(), getMemberName(), messager);
            messages = databaseView.createMessageManager(getTeamName(), getMemberName(), messager);
            connected = true;
            TEAM_LOGGER.info("ExternalTeamClient connected: team=%s member=%s", getTeamName(), getMemberName());
            return null;
        });
    }

    public CompletionStage<Void> closeAsync() {
        return supplyStage(() -> {
            if (messager != null) {
                join(messager.stop());
                messager = null;
            }
            if (sessionToken != null) {
                AgentTeamsContext.resetSessionId(sessionToken);
                sessionToken = null;
            }
            tasks = null;
            messages = null;
            databaseView = null;
            connected = false;
            return null;
        });
    }

    @Override
    public void close() {
        closeAsync().toCompletableFuture().join();
    }

    public CompletionStage<String> sendMessage(String to, String content) {
        TeamMessageManager manager = requireMessages();
        if (BROADCAST_TARGET.equals(to)) {
            return manager.broadcastMessage(content);
        }
        return manager.sendMessage(content, to);
    }

    public CompletionStage<List<TeamTask>> listTasks() {
        return listTasks(null);
    }

    public CompletionStage<List<TeamTask>> listTasks(String status) {
        return requireTasks().listTasks(status);
    }

    public CompletionStage<List<TeamTask>> claimableTasks() {
        return requireTasks().getClaimableTasks();
    }

    public CompletionStage<TaskDetail> getTask(String taskId) {
        return requireTasks().getTaskDetail(taskId);
    }

    public CompletionStage<TaskOpResult> claimTask(String taskId) {
        return requireTasks().claim(taskId);
    }

    public CompletionStage<TaskOpResult> completeTask(String taskId) {
        return requireTasks().complete(taskId);
    }

    public CompletionStage<TaskOpResult> updateTask(String taskId, String title, String content) {
        return requireTasks().updateTask(taskId, title, content);
    }

    public CompletionStage<List<TeamMember>> listMembers() {
        return requireDatabase().listMembers(getTeamName());
    }

    public CompletionStage<InboxView> fetchInbox() {
        return fetchInbox(true);
    }

    public CompletionStage<InboxView> fetchInbox(boolean markRead) {
        TeamMessageManager manager = requireMessages();
        return manager.getMessages(getMemberName(), true)
                .thenCompose(direct -> manager.getBroadcastMessages(getMemberName(), true)
                        .thenCompose(broadcast -> {
                            List<TeamMessage> unread = new ArrayList<>();
                            unread.addAll(direct);
                            unread.addAll(broadcast);
                            CompletionStage<Void> markStage = CompletableFuture.completedFuture(null);
                            if (markRead) {
                                for (TeamMessage message : unread) {
                                    markStage = markStage.thenCompose(ignored ->
                                            manager.markMessageRead(message.getMessageId(), getMemberName())
                                                    .thenApply(read -> null));
                                }
                            }
                            return markStage.thenCompose(ignored -> listTasks())
                                    .thenApply(board -> new InboxView(unread, board));
                        }));
    }

    public CompletionStage<Void> watch(InboxObserver observer) {
        Objects.requireNonNull(observer, "observer");
        Messager activeMessager = requireMessager();
        String messageTopic = TeamTopic.MESSAGE.build(getSessionId(), getTeamName());
        String taskTopic = TeamTopic.TASK.build(getSessionId(), getTeamName());

        java.util.function.Function<EventMessage, CompletionStage<Void>> onEvent = event ->
                fetchInbox(true).thenCompose(view -> view.isEmpty()
                        ? CompletableFuture.completedFuture(null)
                        : observer.observe(view));

        join(activeMessager.subscribe(messageTopic, onEvent::apply));
        join(activeMessager.subscribe(taskTopic, onEvent::apply));

        CompletableFuture<Void> waitForever = new CompletableFuture<>();
        waitForever.whenComplete((ignored, error) -> {
            activeMessager.unsubscribe(messageTopic).toCompletableFuture().join();
            activeMessager.unsubscribe(taskTopic).toCompletableFuture().join();
        });
        return waitForever;
    }

    private TeamTaskManager requireTasks() {
        if (tasks == null) {
            raiseNotConnected();
        }
        return tasks;
    }

    private TeamMessageManager requireMessages() {
        if (messages == null) {
            raiseNotConnected();
        }
        return messages;
    }

    private Messager requireMessager() {
        if (messager == null) {
            raiseNotConnected();
        }
        return messager;
    }

    private ClientDatabaseView requireDatabase() {
        if (databaseView == null) {
            raiseNotConnected();
        }
        return databaseView;
    }

    private static void raiseNotConnected() {
        ErrorHelper.raiseError(
                StatusCode.AGENT_TEAM_STATE_INVALID,
                null,
                null,
                null,
                Map.of("reason", "ExternalTeamClient is not connected; call connect() first")
        );
    }

    private static ClientDatabaseView openSharedDatabase(Map<String, Object> dbConfig) {
        SharedResources.SharedDatabase sharedDb;
        String rawDbType = stringValue(dbConfig == null ? null : dbConfig.get("db_type"));
        if ("memory".equals(rawDbType)) {
            sharedDb = SharedResources.getSharedDb(SharedResources.SharedDbConfig.memory());
        } else {
            DatabaseConfig config = OBJECT_MAPPER.convertValue(
                    dbConfig == null ? Map.of() : dbConfig,
                    DatabaseConfig.class);
            sharedDb = SharedResources.getSharedDb(config);
        }
        if (sharedDb instanceof SharedResources.SharedTeamDatabase teamDatabase) {
            teamDatabase.engine().initialize().join();
            teamDatabase.engine().createCurrentSessionTables().join();
            return ClientDatabaseView.from(teamDatabase);
        }
        ErrorHelper.raiseError(
                StatusCode.AGENT_TEAM_STATE_INVALID,
                null,
                null,
                null,
                Map.of("reason", "SharedResources returned unsupported database type: " + sharedDb.getClass().getName())
        );
        throw new IllegalStateException("unreachable");
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static <T> CompletionStage<T> supplyStage(Supplier<T> supplier) {
        try {
            return CompletableFuture.completedFuture(supplier.get());
        } catch (Throwable throwable) {
            CompletableFuture<T> failed = new CompletableFuture<>();
            failed.completeExceptionally(throwable);
            return failed;
        }
    }

    private static <T> T join(CompletionStage<T> stage) {
        try {
            return stage.toCompletableFuture().join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw exception;
        }
    }

    private interface ClientDatabaseView {
        TeamTaskManager createTaskManager(String teamName, String memberName, Messager messager);

        TeamMessageManager createMessageManager(String teamName, String memberName, Messager messager);

        CompletionStage<List<TeamMember>> listMembers(String teamName);

        static ClientDatabaseView from(InMemoryTeamDatabase database) {
            return new ClientDatabaseView() {
                @Override
                public TeamTaskManager createTaskManager(String teamName, String memberName, Messager messager) {
                    return new TeamTaskManager(teamName, memberName, database, messager);
                }

                @Override
                public TeamMessageManager createMessageManager(String teamName, String memberName, Messager messager) {
                    return new TeamMessageManager(teamName, memberName, database, messager);
                }

                @Override
                public CompletionStage<List<TeamMember>> listMembers(String teamName) {
                    return database.getTeamMembers(teamName, null);
                }
            };
        }

        static ClientDatabaseView from(SharedResources.SharedTeamDatabase database) {
            return new ClientDatabaseView() {
                @Override
                public TeamTaskManager createTaskManager(String teamName, String memberName, Messager messager) {
                    return new TeamTaskManager(
                            teamName,
                            memberName,
                            new SharedTaskDatabase(database),
                            messager,
                            null,
                            null,
                            null,
                            null
                    );
                }

                @Override
                public TeamMessageManager createMessageManager(String teamName, String memberName, Messager messager) {
                    return new TeamMessageManager(
                            teamName,
                            memberName,
                            new SharedMessageStore(database),
                            messager
                    );
                }

                @Override
                public CompletionStage<List<TeamMember>> listMembers(String teamName) {
                    return database.member().getTeamMembers(teamName, null);
                }
            };
        }
    }

    private static final class SharedMessageStore implements TeamMessageManager.MessageStore {
        private final MessageDao messageDao;

        private SharedMessageStore(SharedResources.SharedTeamDatabase database) {
            this.messageDao = database.message();
        }

        @Override
        public CompletionStage<Boolean> createMessage(
                String messageId,
                String teamName,
                String fromMemberName,
                String content,
                String toMemberName,
                boolean broadcast,
                boolean isRead) {
            return messageDao.createMessage(messageId, teamName, fromMemberName, content, toMemberName, broadcast, isRead);
        }

        @Override
        public CompletionStage<List<TeamMessage>> getMessages(
                String teamName,
                String toMemberName,
                boolean unreadOnly,
                String fromMemberName) {
            return messageDao.getMessages(teamName, toMemberName, unreadOnly, fromMemberName);
        }

        @Override
        public CompletionStage<List<TeamMessage>> getBroadcastMessages(
                String teamName,
                String memberName,
                boolean unreadOnly,
                String fromMemberName) {
            return messageDao.getBroadcastMessages(teamName, memberName, unreadOnly, fromMemberName);
        }

        @Override
        public CompletionStage<List<TeamMessage>> getTeamMessages(String teamName, Boolean broadcast) {
            return messageDao.getTeamMessages(teamName, broadcast);
        }

        @Override
        public CompletionStage<Boolean> hasUnreadMessages(String teamName, boolean includeBroadcast) {
            return messageDao.hasUnreadMessages(teamName, includeBroadcast);
        }

        @Override
        public CompletionStage<Boolean> markMessageRead(String messageId, String memberName) {
            return messageDao.markMessageRead(messageId, memberName);
        }
    }

    private static final class SharedTaskDatabase implements TeamTaskManager.TeamTaskDatabase {
        private final TeamDao teamDao;
        private final MemberDao memberDao;
        private final TaskDao taskDao;

        private SharedTaskDatabase(SharedResources.SharedTeamDatabase database) {
            this.teamDao = database.team();
            this.memberDao = database.member();
            this.taskDao = database.task();
        }

        @Override
        public CompletionStage<Boolean> createTask(String taskId, String teamName, String title, String content, String status) {
            return taskDao.createTask(taskId, teamName, title, content, status);
        }

        @Override
        public CompletionStage<GraphMutationResult> mutateDependencyGraph(
                String teamName,
                List<NewTaskSpec> newTasks,
                List<TaskDao.DependencyEdge> addEdges) {
            return taskDao.mutateDependencyGraph(teamName, newTasks, addEdges);
        }

        @Override
        public CompletionStage<Boolean> addTaskWithBidirectionalDependencies(
                String taskId,
                String teamName,
                String title,
                String content,
                String status,
                List<String> dependencies,
                List<String> dependentTaskIds) {
            return taskDao.addTaskWithBidirectionalDependencies(
                    taskId,
                    teamName,
                    title,
                    content,
                    status,
                    dependencies,
                    dependentTaskIds
            );
        }

        @Override
        public CompletionStage<Optional<TeamTask>> getTask(String taskId) {
            return taskDao.getTask(taskId);
        }

        @Override
        public CompletionStage<Boolean> claimTask(String taskId, String memberName) {
            return taskDao.claimTask(taskId, memberName);
        }

        @Override
        public CompletionStage<Optional<TeamTask>> resetTask(String taskId) {
            return taskDao.resetTask(taskId);
        }

        @Override
        public CompletionStage<Optional<TeamTask>> approvePlanTask(String taskId) {
            return taskDao.approvePlanTask(taskId);
        }

        @Override
        public CompletionStage<Boolean> updateTask(String taskId, String title, String content) {
            return taskDao.updateTask(taskId, title, content);
        }

        @Override
        public CompletionStage<Optional<TaskDao.TaskTerminationResult>> completeTask(String taskId) {
            return taskDao.completeTask(taskId);
        }

        @Override
        public CompletionStage<Optional<TaskDao.TaskTerminationResult>> cancelTask(String taskId) {
            return taskDao.cancelTask(taskId);
        }

        @Override
        public CompletionStage<TaskDao.TaskBulkCancellationResult> cancelAllTasks(String teamName, Set<String> skipAssignees) {
            return taskDao.cancelAllTasks(teamName, skipAssignees);
        }

        @Override
        public CompletionStage<List<TeamTask>> getTeamTasks(String teamName, String status) {
            return taskDao.getTeamTasks(teamName, status);
        }

        @Override
        public CompletionStage<List<TeamTaskDependency>> getTaskDependencies(String taskId) {
            return taskDao.getTaskDependencies(taskId);
        }

        @Override
        public CompletionStage<List<TeamTask>> getTasksByAssignee(String teamName, String memberName, String status) {
            return taskDao.getTasksByAssignee(teamName, memberName, status);
        }

        @Override
        public CompletionStage<List<TeamTask>> getTasksDependingOn(String dependsOnTaskId) {
            return taskDao.getTasksDependingOn(dependsOnTaskId);
        }

        @Override
        public CompletionStage<Optional<TeamMember>> getMember(String memberName, String teamName) {
            return memberDao.getMember(memberName, teamName);
        }

        @Override
        public CompletionStage<Optional<Team>> getTeam(String teamName) {
            return teamDao.getTeam(teamName);
        }
    }
}
