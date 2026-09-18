/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.skill;

import com.openjiuwen.agent_teams.external.ExternalFormat;
import com.openjiuwen.agent_teams.external.ExternalTeamClient;
import com.openjiuwen.agent_teams.external.InboxObserver;
import com.openjiuwen.agent_teams.external.InboxView;
import com.openjiuwen.agent_teams.external.TeamJoinDescriptor;
import com.openjiuwen.agent_teams.schema.TaskDetail;
import com.openjiuwen.agent_teams.schema.TaskOpResult;
import com.openjiuwen.agent_teams.tools.TeamMember;
import com.openjiuwen.agent_teams.tools.TeamMessage;
import com.openjiuwen.agent_teams.tools.TeamTask;
import com.openjiuwen.agent_teams.tools.database.TeamDatabase;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * Non-interactive CLI for external team members.
 *
 * <p>Mirrors Python's {@code build_parser}, {@code _dispatch}, {@code main}, and
 * {@code run} in {@code openjiuwen/agent_teams/skill/cli.py}.</p>
 */
public final class TeamMemberCli {

    public static final String PROG = "team-member";
    public static final String BROADCAST_TARGET = ExternalTeamClient.BROADCAST_TARGET;

    private TeamMemberCli() {
    }

    public static void main(String[] argv) {
        System.exit(run(argv));
    }

    public static int run(String... argv) {
        return execute(argv, TeamMemberCli::createExternalClient, System.out, System.err);
    }

    public static int execute(
            String[] argv,
            ClientFactory clientFactory,
            PrintStream out,
            PrintStream err
    ) {
        Objects.requireNonNull(clientFactory, "clientFactory");
        PrintStream safeOut = out == null ? System.out : out;
        PrintStream safeErr = err == null ? System.err : err;
        CliArgs args;
        try {
            args = parse(argv);
        } catch (IllegalArgumentException exception) {
            safeErr.println(exception.getMessage());
            return 2;
        }

        TeamMemberClient client = null;
        try {
            TeamJoinDescriptor descriptor = loadDescriptor(args);
            client = join(clientFactory.create(descriptor));
            join(client.connect());
            return dispatch(client, args, safeOut, safeErr);
        } catch (Exception exception) {
            safeErr.println(exception.getMessage() == null ? exception.toString() : exception.getMessage());
            return 1;
        } finally {
            if (client != null) {
                try {
                    join(client.close());
                } catch (Exception ignored) {
                    // Mirrors Python context-manager cleanup best effort.
                }
            }
        }
    }

    public static CliArgs parse(String... argv) {
        List<String> tokens = new ArrayList<>(Arrays.asList(argv == null ? new String[0] : argv));
        CliArgs args = new CliArgs();
        List<String> commandTokens = new ArrayList<>();
        for (int index = 0; index < tokens.size(); index++) {
            String token = tokens.get(index);
            if ("--descriptor-json".equals(token)) {
                args.descriptorJson = requireValue(tokens, ++index, token);
            } else if ("--descriptor-file".equals(token)) {
                args.descriptorFile = requireValue(tokens, ++index, token);
            } else {
                commandTokens.add(token);
            }
        }
        if (commandTokens.isEmpty()) {
            throw new IllegalArgumentException(PROG + ": missing required subcommand");
        }
        parseCommand(args, commandTokens);
        return args;
    }

    private static void parseCommand(CliArgs args, List<String> tokens) {
        args.command = tokens.get(0);
        switch (args.command) {
            case "inbox" -> parseInbox(args, tokens);
            case "send" -> {
                requireSize(tokens, 3, "send requires: <to> <content>");
                args.to = tokens.get(1);
                args.content = tokens.get(2);
            }
            case "broadcast" -> {
                requireSize(tokens, 2, "broadcast requires: <content>");
                args.content = tokens.get(1);
            }
            case "task" -> parseTask(args, tokens);
            case "claim", "complete" -> {
                requireSize(tokens, 2, args.command + " requires: <task_id>");
                args.taskId = tokens.get(1);
            }
            case "update" -> parseUpdate(args, tokens);
            case "members" -> requireSize(tokens, 1, "members takes no arguments");
            default -> throw new IllegalArgumentException("Unknown command: " + args.command);
        }
    }

    private static void parseInbox(CliArgs args, List<String> tokens) {
        for (int index = 1; index < tokens.size(); index++) {
            if ("--watch".equals(tokens.get(index))) {
                args.watch = true;
            } else {
                throw new IllegalArgumentException("inbox: unknown argument: " + tokens.get(index));
            }
        }
    }

    private static void parseTask(CliArgs args, List<String> tokens) {
        if (tokens.size() < 2) {
            throw new IllegalArgumentException("task requires an action");
        }
        args.taskAction = tokens.get(1);
        switch (args.taskAction) {
            case "list" -> {
                for (int index = 2; index < tokens.size(); index++) {
                    if ("--status".equals(tokens.get(index))) {
                        args.status = requireValue(tokens, ++index, "--status");
                    } else {
                        throw new IllegalArgumentException("task list: unknown argument: " + tokens.get(index));
                    }
                }
            }
            case "claimable" -> requireSize(tokens, 2, "task claimable takes no arguments");
            case "get" -> {
                requireSize(tokens, 3, "task get requires: <task_id>");
                args.taskId = tokens.get(2);
            }
            default -> throw new IllegalArgumentException("Unknown task action: " + args.taskAction);
        }
    }

    private static void parseUpdate(CliArgs args, List<String> tokens) {
        if (tokens.size() < 2) {
            throw new IllegalArgumentException("update requires: <task_id>");
        }
        args.taskId = tokens.get(1);
        for (int index = 2; index < tokens.size(); index++) {
            String token = tokens.get(index);
            if ("--title".equals(token)) {
                args.title = requireValue(tokens, ++index, token);
            } else if ("--content".equals(token)) {
                args.content = requireValue(tokens, ++index, token);
            } else {
                throw new IllegalArgumentException("update: unknown argument: " + token);
            }
        }
    }

    private static TeamJoinDescriptor loadDescriptor(CliArgs args) throws java.io.IOException {
        if (args.descriptorJson != null) {
            return TeamJoinDescriptor.fromJson(args.descriptorJson);
        }
        if (args.descriptorFile != null) {
            return TeamJoinDescriptor.fromJson(Files.readString(Path.of(args.descriptorFile), StandardCharsets.UTF_8));
        }
        return TeamJoinDescriptor.fromEnv();
    }

    private static int dispatch(TeamMemberClient client, CliArgs args, PrintStream out, PrintStream err) {
        return switch (args.command) {
            case "inbox" -> dispatchInbox(client, args.watch, out);
            case "send" -> dispatchSend(client, args.to, args.content, out, err);
            case "broadcast" -> dispatchBroadcast(client, args.content, out, err);
            case "task" -> dispatchTask(client, args, out, err);
            case "claim" -> reportOp(join(client.claimTask(args.taskId)), "claimed " + args.taskId, out, err);
            case "complete" -> reportOp(join(client.completeTask(args.taskId)), "completed " + args.taskId, out, err);
            case "update" -> reportOp(join(client.updateTask(args.taskId, args.title, args.content)),
                    "updated " + args.taskId, out, err);
            case "members" -> dispatchMembers(client, out);
            default -> {
                err.println("Unknown command: " + args.command);
                yield 2;
            }
        };
    }

    private static int dispatchInbox(TeamMemberClient client, boolean watch, PrintStream out) {
        printInbox(join(client.fetchInbox()), client.isLeader(), out);
        if (watch) {
            join(client.watch(view -> {
                printInbox(view, client.isLeader(), out);
                return CompletableFuture.completedFuture(null);
            }));
        }
        return 0;
    }

    private static int dispatchSend(TeamMemberClient client, String to, String content, PrintStream out, PrintStream err) {
        String messageId = join(client.sendMessage(to, content));
        if (messageId == null) {
            err.println("Failed to send message to '" + to + "'");
            return 1;
        }
        out.println("sent " + messageId + " -> " + to);
        return 0;
    }

    private static int dispatchBroadcast(TeamMemberClient client, String content, PrintStream out, PrintStream err) {
        String messageId = join(client.sendMessage(BROADCAST_TARGET, content));
        if (messageId == null) {
            err.println("Failed to broadcast message");
            return 1;
        }
        out.println("broadcast " + messageId);
        return 0;
    }

    private static int dispatchTask(TeamMemberClient client, CliArgs args, PrintStream out, PrintStream err) {
        return switch (args.taskAction) {
            case "list" -> {
                for (TeamTask task : join(client.listTasks(args.status))) {
                    String assignee = isBlank(task.getAssignee()) ? "-" : task.getAssignee();
                    out.println("[" + task.getTaskId() + "] [" + task.getStatus() + "] "
                            + task.getTitle() + " (" + assignee + ")");
                }
                yield 0;
            }
            case "claimable" -> {
                for (TeamTask task : join(client.claimableTasks())) {
                    out.println("[" + task.getTaskId() + "] " + task.getTitle());
                }
                yield 0;
            }
            case "get" -> {
                TaskDetail detail = join(client.getTask(args.taskId));
                if (detail == null) {
                    err.println("Task '" + args.taskId + "' not found");
                    yield 1;
                }
                printTaskDetail(detail, out);
                yield 0;
            }
            default -> {
                err.println("Unknown task action: " + args.taskAction);
                yield 2;
            }
        };
    }

    private static int dispatchMembers(TeamMemberClient client, PrintStream out) {
        for (TeamMember member : join(client.listMembers())) {
            out.println(member.getMemberName() + " (" + member.getRole() + ") [" + member.getStatus() + "]");
        }
        return 0;
    }

    private static void printInbox(InboxView view, boolean isLeader, PrintStream out) {
        long nowMs = TeamDatabase.getCurrentTime();
        if (!view.messages().isEmpty()) {
            out.println(ExternalFormat.renderMessages(
                    view.messages().stream().map(MessageAdapter::new).toList(),
                    nowMs
            ));
        }
        String board = ExternalFormat.renderTaskBoard(
                view.tasks().stream().map(TaskAdapter::new).toList(),
                isLeader,
                nowMs
        );
        if (!board.isBlank()) {
            if (!view.messages().isEmpty()) {
                out.println();
            }
            out.println(board);
        }
        if (view.isEmpty()) {
            out.println("(inbox empty)");
        }
    }

    private static void printTaskDetail(TaskDetail detail, PrintStream out) {
        out.println("id:        " + detail.getTaskId());
        out.println("title:     " + detail.getTitle());
        out.println("status:    " + detail.getStatus());
        out.println("assignee:  " + defaultDash(detail.getAssignee()));
        out.println("blocked_by: " + joinOrDash(detail.getBlockedBy()));
        out.println("blocks:     " + joinOrDash(detail.getBlocks()));
        out.println("content:\n" + defaultString(detail.getContent()));
    }

    private static int reportOp(TaskOpResult result, String ok, PrintStream out, PrintStream err) {
        if (result.ok()) {
            out.println(ok);
            return 0;
        }
        err.println(result.reason());
        return 1;
    }

    private static CompletionStage<TeamMemberClient> createExternalClient(TeamJoinDescriptor descriptor) {
        return CompletableFuture.completedFuture(new ExternalClientAdapter(new ExternalTeamClient(descriptor)));
    }

    private static String requireValue(List<String> tokens, int index, String option) {
        if (index >= tokens.size()) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return tokens.get(index);
    }

    private static void requireSize(List<String> tokens, int size, String message) {
        if (tokens.size() != size) {
            throw new IllegalArgumentException(message);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String defaultDash(String value) {
        return isBlank(value) ? "-" : value;
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }

    private static String joinOrDash(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "-";
        }
        return String.join(", ", values);
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

    /**
     * Parsed command-line options.
     *
     * <p>Mirrors Python's {@code argparse.Namespace} shape in
     * {@code openjiuwen/agent_teams/skill/cli.py}.</p>
     */
    public static final class CliArgs {
        private String descriptorFile;
        private String descriptorJson;
        private String command;
        private boolean watch;
        private String to;
        private String content;
        private String taskAction;
        private String status;
        private String taskId;
        private String title;

        private CliArgs() {
        }

        public String getDescriptorFile() {
            return descriptorFile;
        }

        public String getDescriptorJson() {
            return descriptorJson;
        }

        public String getCommand() {
            return command;
        }

        public boolean isWatch() {
            return watch;
        }

        public String getTo() {
            return to;
        }

        public String getContent() {
            return content;
        }

        public String getTaskAction() {
            return taskAction;
        }

        public String getStatus() {
            return status;
        }

        public String getTaskId() {
            return taskId;
        }

        public String getTitle() {
            return title;
        }
    }

    /**
     * Factory for the external-member client used by a parsed descriptor.
     *
     * <p>Mirrors Python's construction of {@code ExternalTeamClient(descriptor)} in
     * {@code openjiuwen/agent_teams/skill/cli.py}.</p>
     */
    @FunctionalInterface
    public interface ClientFactory {
        CompletionStage<TeamMemberClient> create(TeamJoinDescriptor descriptor);
    }

    /**
     * Minimal external-member operations consumed by the CLI dispatcher.
     *
     * <p>Mirrors Python's {@code ExternalTeamClient} usage in
     * {@code openjiuwen/agent_teams/skill/cli.py}.</p>
     */
    public interface TeamMemberClient {
        CompletionStage<Void> connect();

        CompletionStage<Void> close();

        boolean isLeader();

        CompletionStage<InboxView> fetchInbox();

        CompletionStage<Void> watch(InboxObserver observer);

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
     * Production facade over {@link ExternalTeamClient}.
     *
     * <p>Mirrors Python's {@code async with ExternalTeamClient(descriptor)} in
     * {@code openjiuwen/agent_teams/skill/cli.py}.</p>
     */
    private record ExternalClientAdapter(ExternalTeamClient client) implements TeamMemberClient {
        private ExternalClientAdapter {
            Objects.requireNonNull(client, "client");
        }

        @Override
        public CompletionStage<Void> connect() {
            return client.connect();
        }

        @Override
        public CompletionStage<Void> close() {
            return client.closeAsync();
        }

        @Override
        public boolean isLeader() {
            return client.isLeader();
        }

        @Override
        public CompletionStage<InboxView> fetchInbox() {
            return client.fetchInbox();
        }

        @Override
        public CompletionStage<Void> watch(InboxObserver observer) {
            return client.watch(observer);
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
     * Adapter from team messages to the external formatter protocol.
     *
     * <p>Mirrors Python's {@code _print_inbox} message rendering use in
     * {@code openjiuwen/agent_teams/skill/cli.py}.</p>
     */
    private record MessageAdapter(TeamMessage message) implements ExternalFormat.MessageLike {
        private MessageAdapter {
            Objects.requireNonNull(message, "message");
        }

        @Override
        public String messageId() {
            return message.getMessageId();
        }

        @Override
        public String fromMemberName() {
            return message.getFromMemberName();
        }

        @Override
        public String content() {
            return message.getContent();
        }

        @Override
        public boolean broadcast() {
            return Boolean.TRUE.equals(message.getBroadcast());
        }

        @Override
        public long timestamp() {
            return message.getTimestamp() == null ? 0L : message.getTimestamp();
        }
    }

    /**
     * Adapter from team tasks to the external formatter protocol.
     *
     * <p>Mirrors Python's {@code _print_inbox} task-board rendering use in
     * {@code openjiuwen/agent_teams/skill/cli.py}.</p>
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
