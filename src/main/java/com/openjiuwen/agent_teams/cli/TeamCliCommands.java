/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.cli;

import com.openjiuwen.agent_teams.interaction.DeliverResult;
import com.openjiuwen.agent_teams.runtime.TeamRuntimeManager;
import com.openjiuwen.core.runner.Runner;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Slash-command registry and handlers for the Team CLI.
 *
 * <p>Mirrors Python's module-level command tables and handlers in
 * {@code openjiuwen/agent_teams/cli/commands.py}.</p>
 */
public final class TeamCliCommands {

    public static final double RUNTIME_READY_TIMEOUT_SECONDS = 30.0d;

    public static final Map<String, TeamCliCommandHandler> SPEC_ACTIONS = actions(Map.of(
            "load", TeamCliCommands::specLoad,
            "list", TeamCliCommands::specList,
            "show", TeamCliCommands::specShow
    ));

    public static final Map<String, TeamCliCommandHandler> TEAM_ACTIONS = actions(Map.ofEntries(
            Map.entry("list", TeamCliCommands::teamList),
            Map.entry("status", TeamCliCommands::teamStatus),
            Map.entry("monitor", TeamCliCommands::teamMonitor),
            Map.entry("use", TeamCliCommands::teamUse),
            Map.entry("start", TeamCliCommands::teamStart),
            Map.entry("switch", TeamCliCommands::teamSwitch),
            Map.entry("pause", TeamCliCommands::teamPause),
            Map.entry("resume", TeamCliCommands::teamResume),
            Map.entry("stop", TeamCliCommands::teamStop),
            Map.entry("delete", TeamCliCommands::teamDelete),
            Map.entry("watch", TeamCliCommands::teamWatch),
            Map.entry("unwatch", TeamCliCommands::teamUnwatch)
    ));

    public static final Map<String, TeamCliCommandHandler> SESSION_ACTIONS = actions(Map.of(
            "active", TeamCliCommands::sessionActive,
            "list", TeamCliCommands::sessionList,
            "switch", TeamCliCommands::sessionSwitch,
            "release", TeamCliCommands::sessionRelease
    ));

    public static final Map<String, TeamCliCommandHandler> SLASH_COMMANDS = actions(Map.of(
            "/team", TeamCliCommands::cmdTeam,
            "/session", TeamCliCommands::cmdSession,
            "/spec", TeamCliCommands::cmdSpec,
            "/help", TeamCliCommands::cmdHelp,
            "/clear", TeamCliCommands::cmdClear,
            "/exit", TeamCliCommands::cmdExit,
            "/quit", TeamCliCommands::cmdExit
    ));

    private static final Map<String, String> TOP_LEVEL_DESCRIPTIONS = Map.of(
            "/team", "team lifecycle (list / start / stop / pause / ...)",
            "/session", "session lifecycle (switch / release / list)",
            "/spec", "team spec registry (load / list / show)",
            "/help", "show command reference",
            "/clear", "clear the screen",
            "/exit", "leave the CLI"
    );

    private static final Map<String, Map<String, TeamCliCommandHandler>> SUB_ACTION_TABLES = Map.of(
            "/team", TEAM_ACTIONS,
            "/session", SESSION_ACTIONS,
            "/spec", SPEC_ACTIONS
    );

    private TeamCliCommands() {
    }

    public static CompletionStage<Void> dispatchSlash(TeamCliState state, String line) {
        return dispatchSlash(new CommandContext(state), line);
    }

    public static CompletionStage<Void> dispatchSlash(CommandContext context, String line) {
        List<String> parts = splitArgs(stripLeadingSlash(line));
        if (parts.isEmpty()) {
            context.console().println("[yellow]empty command[/yellow]");
            return completed();
        }
        String head = "/" + parts.get(0);
        List<String> rest = parts.subList(1, parts.size());
        TeamCliCommandHandler handler = SLASH_COMMANDS.get(head);
        if (handler == null) {
            context.console().println("[red]unknown command: " + head + "[/red]");
            return completed();
        }
        try {
            return handler.handle(context, rest).exceptionally(error -> {
                Throwable cause = unwrap(error);
                if (cause instanceof ExitCli) {
                    throw (ExitCli) cause;
                }
                context.console().println("[red]" + head + " crashed: " + cause.getMessage() + "[/red]");
                return null;
            });
        } catch (ExitCli exitCli) {
            throw exitCli;
        } catch (RuntimeException error) {
            context.console().println("[red]" + head + " failed: " + error.getMessage() + "[/red]");
            return completed();
        }
    }

    public static List<String> splitArgs(String rest) {
        String input = rest == null ? "" : rest.trim();
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean escaping = false;
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (escaping) {
                current.append(ch);
                escaping = false;
                continue;
            }
            if (ch == '\\') {
                escaping = true;
                continue;
            }
            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                } else {
                    current.append(ch);
                }
                continue;
            }
            if (ch == '\'' || ch == '"') {
                quote = ch;
                continue;
            }
            if (Character.isWhitespace(ch)) {
                if (!current.isEmpty()) {
                    args.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(ch);
        }
        if (!current.isEmpty()) {
            args.add(current.toString());
        }
        return args;
    }

    public static boolean popFlag(List<String> args, String flag) {
        return args.remove(flag);
    }

    public static String resolveTeamName(TeamCliState state, List<String> args) {
        return args.isEmpty() ? state.getActiveTeamName() : args.get(0);
    }

    public static String resolveSessionId(TeamCliState state, List<String> args, int index) {
        return args.size() > index ? args.get(index) : state.getActiveSessionId();
    }

    public static List<String> sortedSlashCommands() {
        return SLASH_COMMANDS.keySet().stream().sorted().toList();
    }

    public static Map<String, String> topLevelDescriptions() {
        return TOP_LEVEL_DESCRIPTIONS;
    }

    public static Map<String, Map<String, TeamCliCommandHandler>> subActionTables() {
        return SUB_ACTION_TABLES;
    }

    private static CompletionStage<Void> specLoad(CommandContext context, List<String> args) {
        if (args.isEmpty()) {
            context.console().println("[yellow]usage: /spec load <yaml_path>[/yellow]");
            return completed();
        }
        try {
            SpecEntry entry = context.state().getSpecRegistry().addYaml(args.get(0));
            context.console().println("[green]loaded[/green] team=" + entry.spec().getTeamName()
                    + " source=" + entry.source());
            context.console().println("[dim]start with: /team start " + entry.spec().getTeamName()
                    + " <session_id> [query][/dim]");
        } catch (RuntimeException error) {
            context.console().println("[red]load failed: " + error.getMessage() + "[/red]");
        }
        return completed();
    }

    private static CompletionStage<Void> specList(CommandContext context, List<String> args) {
        List<SpecEntry> entries = context.state().getSpecRegistry().entries();
        if (entries.isEmpty()) {
            context.console().println("[dim]no specs registered (use `/spec load <yaml>`).[/dim]");
            return completed();
        }
        entries.forEach(entry -> context.console().println(
                entry.spec().getTeamName() + "\t" + entry.source()
                        + "\tmembers=" + safeSize(entry.spec().getPredefinedMembers())));
        return completed();
    }

    private static CompletionStage<Void> specShow(CommandContext context, List<String> args) {
        if (args.isEmpty()) {
            context.console().println("[yellow]usage: /spec show <team_name>[/yellow]");
            return completed();
        }
        SpecEntry entry = context.state().getSpecRegistry().get(args.get(0));
        if (entry == null) {
            context.console().println("[red]no spec registered for " + args.get(0) + "[/red]");
            return completed();
        }
        context.console().println("team_name=" + entry.spec().getTeamName()
                + " source=" + entry.source()
                + " members=" + safeSize(entry.spec().getPredefinedMembers()));
        return completed();
    }

    private static CompletionStage<Void> teamList(CommandContext context, List<String> args) {
        List<TeamRuntimeManager.RuntimeEntryInfo> infos = Runner.listActiveTeams();
        Set<String> activeNames = infos.stream()
                .map(TeamRuntimeManager.RuntimeEntryInfo::teamName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> registered = new LinkedHashSet<>(context.state().getSpecRegistry().names());
        if (infos.isEmpty() && registered.isEmpty()) {
            context.console().println("[dim]no teams active or registered.[/dim]");
            return completed();
        }
        for (TeamRuntimeManager.RuntimeEntryInfo info : infos) {
            context.console().println(info.teamName() + "\t" + info.currentSessionId()
                    + "\t" + info.state().getValue()
                    + "\tgate=" + (info.gateClosed() ? "closed" : "open")
                    + "\tregistered=" + (registered.contains(info.teamName()) ? "yes" : "no"));
        }
        registered.stream()
                .filter(name -> !activeNames.contains(name))
                .forEach(name -> context.console().println(name + "\t-\tinactive\tgate=-\tregistered=yes"));
        return completed();
    }

    private static CompletionStage<Void> teamStatus(CommandContext context, List<String> args) {
        String target = resolveTeamName(context.state(), args);
        if (target == null) {
            context.console().println("[yellow]usage: /team status [name][/yellow]");
            return completed();
        }
        TeamRuntimeManager.RuntimeEntryInfo match = Runner.listActiveTeams().stream()
                .filter(info -> Objects.equals(info.teamName(), target))
                .findFirst()
                .orElse(null);
        if (match == null) {
            context.console().println("[red]team " + target + " is not active[/red]");
            return completed();
        }
        context.console().println(match.teamName() + " session=" + match.currentSessionId()
                + " state=" + match.state().getValue()
                + " gate=" + (match.gateClosed() ? "closed" : "open"));
        return completed();
    }

    private static CompletionStage<Void> teamMonitor(CommandContext context, List<String> args) {
        String teamName = resolveTeamName(context.state(), args);
        String sessionId = resolveSessionId(context.state(), args, 1);
        if (teamName == null || sessionId == null) {
            context.console().println("[yellow]usage: /team monitor [name [session_id]] (defaults to active)[/yellow]");
            return completed();
        }
        return Runner.getAgentTeamMonitor(teamName, sessionId, false).thenAccept(monitor -> {
            if (monitor == null) {
                context.console().println("[red]no active runtime for team=" + teamName + " session=" + sessionId + "[/red]");
            } else {
                context.console().println(String.valueOf(monitor));
            }
        });
    }

    private static CompletionStage<Void> teamUse(CommandContext context, List<String> args) {
        if (args.isEmpty()) {
            context.console().println("[yellow]usage: /team use <team_name>[/yellow]");
            return completed();
        }
        String name = args.get(0);
        StreamHandle handle = context.state().getStreamHandles().get(name);
        if (handle == null) {
            context.console().println("[red]team " + name
                    + " has no active stream in this CLI; run `/team start` first[/red]");
            return completed();
        }
        context.state().setActive(handle.getTeamName(), handle.getSessionId());
        context.console().println("[green]active[/green] team=" + handle.getTeamName()
                + " session=" + handle.getSessionId());
        return completed();
    }

    private static CompletionStage<Void> teamStart(CommandContext context, List<String> args) {
        if (args.size() < 2) {
            context.console().println("[yellow]usage: /team start <team_name> <session_id> [query...][/yellow]");
            return completed();
        }
        String query = args.size() > 2 ? String.join(" ", args.subList(2, args.size())) : "hello";
        return startOrResume(context, args.get(0), args.get(1), query).thenApply(ignored -> null);
    }

    private static CompletionStage<Void> teamSwitch(CommandContext context, List<String> args) {
        if (args.isEmpty()) {
            context.console().println("[yellow]usage: /team switch <team_name> [session_id] [query...][/yellow]");
            return completed();
        }
        String newTeam = args.get(0);
        String newSession = args.size() > 1 ? args.get(1) : context.state().getActiveSessionId();
        String query = args.size() > 2 ? String.join(" ", args.subList(2, args.size())) : "hello";
        if (newSession == null) {
            context.console().println("[yellow]no active session; pass session_id explicitly[/yellow]");
            return completed();
        }
        String previousTeam = context.state().getActiveTeamName();
        String previousSession = context.state().getActiveSessionId();
        CompletionStage<Void> stopPrevious = completed();
        if (previousTeam != null && !previousTeam.equals(newTeam)) {
            StreamHandle handle = context.state().getStreamHandles().get(previousTeam);
            if (handle != null && previousSession != null) {
                stopPrevious = Runner.stopAgentTeam(previousTeam, previousSession)
                        .thenCompose(ignored -> StreamRenderer.stopStream(handle))
                        .thenRun(() -> context.state().getStreamHandles().remove(previousTeam));
            }
        }
        return stopPrevious.thenCompose(ignored -> startOrResume(context, newTeam, newSession, query))
                .thenAccept(ok -> {
                    if (!ok) {
                        context.state().setActive(previousTeam, previousSession);
                    }
                });
    }

    private static CompletionStage<Void> teamPause(CommandContext context, List<String> args) {
        String teamName = resolveTeamName(context.state(), args);
        String sessionId = resolveSessionId(context.state(), args, 1);
        if (teamName == null || sessionId == null) {
            context.console().println("[yellow]no active team to pause[/yellow]");
            return completed();
        }
        return Runner.pauseAgentTeam(teamName, sessionId)
                .thenAccept(ok -> context.console().println("pause team=" + teamName + " session=" + sessionId + " ok=" + ok));
    }

    private static CompletionStage<Void> teamResume(CommandContext context, List<String> args) {
        String teamName = resolveTeamName(context.state(), args);
        if (teamName == null) {
            context.console().println("[yellow]no active team; pass <team_name>[/yellow]");
            return completed();
        }
        StreamHandle handle = context.state().getStreamHandles().get(teamName);
        String sessionId = handle != null ? handle.getSessionId() : context.state().getActiveSessionId();
        if (sessionId == null) {
            context.console().println("[yellow]no session id known; use `/team start` instead[/yellow]");
            return completed();
        }
        if (handle != null && handle.getTask() != null && !handle.getTask().isDone()) {
            context.console().println("[yellow]team " + teamName
                    + " stream already running; pause/stop first if you want a fresh start.[/yellow]");
            return completed();
        }
        context.state().getStreamHandles().remove(teamName);
        String query = args.size() > 1 ? String.join(" ", args.subList(1, args.size())) : "resume";
        return startOrResume(context, teamName, sessionId, query).thenApply(ignored -> null);
    }

    private static CompletionStage<Void> teamStop(CommandContext context, List<String> args) {
        String teamName = resolveTeamName(context.state(), args);
        if (teamName == null) {
            context.console().println("[yellow]no active team to stop[/yellow]");
            return completed();
        }
        StreamHandle handle = context.state().getStreamHandles().get(teamName);
        String sessionId = handle != null ? handle.getSessionId() : context.state().getActiveSessionId();
        if (sessionId == null) {
            context.console().println("[yellow]no session id for team " + teamName + "; cannot stop[/yellow]");
            return completed();
        }
        CompletionStage<Boolean> stopped = Runner.stopAgentTeam(teamName, sessionId);
        CompletionStage<Void> streamStopped = handle == null ? completed() : StreamRenderer.stopStream(handle);
        return stopped.thenCombine(streamStopped, (ok, ignored) -> ok).thenAccept(ok -> {
            context.state().getStreamHandles().remove(teamName);
            if (Objects.equals(context.state().getActiveTeamName(), teamName)) {
                context.state().setActive(null, null);
            }
            context.console().println("stop team=" + teamName + " session=" + sessionId + " ok=" + ok);
        });
    }

    private static CompletionStage<Void> teamDelete(CommandContext context, List<String> args) {
        List<String> mutableArgs = new ArrayList<>(args);
        boolean force = popFlag(mutableArgs, "--force");
        if (mutableArgs.isEmpty()) {
            context.console().println("[yellow]usage: /team delete <team_name> [--force][/yellow]");
            return completed();
        }
        String teamName = mutableArgs.get(0);
        List<String> sessionIds = new ArrayList<>(context.state().knownSessions(teamName));
        if (Objects.equals(context.state().getActiveTeamName(), teamName)
                && context.state().getActiveSessionId() != null
                && !sessionIds.contains(context.state().getActiveSessionId())) {
            sessionIds.add(context.state().getActiveSessionId());
        }
        return Runner.deleteAgentTeam(teamName, sessionIds, force).thenCompose(ok -> {
            StreamHandle handle = context.state().getStreamHandles().remove(teamName);
            CompletionStage<Void> stop = handle == null ? completed() : StreamRenderer.stopStream(handle);
            return stop.thenRun(() -> {
                if (Objects.equals(context.state().getActiveTeamName(), teamName)) {
                    context.state().setActive(null, null);
                }
                context.state().getHistorySessionIds().remove(teamName);
                context.console().println("delete team=" + teamName + " sessions=" + sessionIds + " ok=" + ok);
            });
        });
    }

    private static CompletionStage<Void> teamWatch(CommandContext context, List<String> args) {
        if (args.isEmpty()) {
            context.console().println("[yellow]usage: /team watch <member_name> [team_name][/yellow]");
            return completed();
        }
        String memberName = args.get(0);
        String teamName = args.size() > 1 ? args.get(1) : context.state().getActiveTeamName();
        String sessionId = context.state().getActiveSessionId();
        if (teamName == null || sessionId == null) {
            context.console().println("[yellow]no active team / session to watch[/yellow]");
            return completed();
        }
        return Runner.registerHumanAgentInbound(teamName, sessionId, memberName, context.inboxCallback())
                .thenAccept(ok -> {
                    if (Boolean.TRUE.equals(ok)) {
                        WatchBinding binding = new WatchBinding(teamName, sessionId, memberName);
                        context.state().getWatchBindings().put(WatchBindingKey.from(binding), binding);
                        context.console().println("[green]watching[/green] " + memberName
                                + " on team=" + teamName + " session=" + sessionId);
                    } else {
                        context.console().println("[red]no active runtime for team=" + teamName
                                + " session=" + sessionId + "[/red]");
                    }
                });
    }

    private static CompletionStage<Void> teamUnwatch(CommandContext context, List<String> args) {
        if (args.isEmpty()) {
            context.console().println("[yellow]usage: /team unwatch <member_name> [team_name][/yellow]");
            return completed();
        }
        String memberName = args.get(0);
        String teamName = args.size() > 1 ? args.get(1) : context.state().getActiveTeamName();
        String sessionId = context.state().getActiveSessionId();
        if (teamName == null || sessionId == null) {
            context.console().println("[yellow]no active team / session to unwatch[/yellow]");
            return completed();
        }
        return Runner.registerHumanAgentInbound(teamName, sessionId, memberName, null)
                .thenAccept(ok -> {
                    context.state().getWatchBindings().remove(new WatchBindingKey(teamName, sessionId, memberName));
                    context.console().println("unwatched " + memberName + " on team=" + teamName
                            + " session=" + sessionId + " ok=" + ok);
                });
    }

    private static CompletionStage<Void> sessionActive(CommandContext context, List<String> args) {
        context.console().println("team=" + nullToDash(context.state().getActiveTeamName())
                + " session=" + nullToDash(context.state().getActiveSessionId()));
        return completed();
    }

    private static CompletionStage<Void> sessionList(CommandContext context, List<String> args) {
        if (context.state().getHistorySessionIds().isEmpty()) {
            context.console().println("[dim]no sessions seen in this CLI yet.[/dim]");
            return completed();
        }
        context.state().getHistorySessionIds().forEach((team, sessions) ->
                context.console().println(team + "\t" + sessions.stream().sorted().collect(Collectors.joining(", "))));
        return completed();
    }

    private static CompletionStage<Void> sessionSwitch(CommandContext context, List<String> args) {
        if (args.isEmpty()) {
            context.console().println("[yellow]usage: /session switch <session_id> [query...][/yellow]");
            return completed();
        }
        String teamName = context.state().getActiveTeamName();
        if (teamName == null) {
            context.console().println("[yellow]no active team to switch session for[/yellow]");
            return completed();
        }
        String newSession = args.get(0);
        String query = args.size() > 1 ? String.join(" ", args.subList(1, args.size())) : "hello";
        StreamHandle handle = context.state().getStreamHandles().get(teamName);
        CompletionStage<Void> stopped = handle == null
                ? completed()
                : Runner.stopAgentTeam(teamName, handle.getSessionId())
                        .thenCompose(ignored -> StreamRenderer.stopStream(handle))
                        .thenRun(() -> context.state().getStreamHandles().remove(teamName));
        String previousSession = context.state().getActiveSessionId();
        return stopped.thenCompose(ignored -> startOrResume(context, teamName, newSession, query))
                .thenAccept(ok -> {
                    if (!ok) {
                        context.state().setActive(teamName, previousSession);
                    }
                });
    }

    private static CompletionStage<Void> sessionRelease(CommandContext context, List<String> args) {
        List<String> mutableArgs = new ArrayList<>(args);
        boolean force = popFlag(mutableArgs, "--force");
        String sessionId = mutableArgs.isEmpty() ? context.state().getActiveSessionId() : mutableArgs.get(0);
        if (sessionId == null) {
            context.console().println("[yellow]usage: /session release [session_id] [--force][/yellow]");
            return completed();
        }
        return Runner.release(sessionId, force)
                .thenRun(() -> context.console().println("[green]released[/green] session=" + sessionId + " force=" + force));
    }

    private static CompletionStage<Void> cmdTeam(CommandContext context, List<String> args) {
        return dispatchSubcommand(context, "team", TEAM_ACTIONS, args);
    }

    private static CompletionStage<Void> cmdSession(CommandContext context, List<String> args) {
        return dispatchSubcommand(context, "session", SESSION_ACTIONS, args);
    }

    private static CompletionStage<Void> cmdSpec(CommandContext context, List<String> args) {
        return dispatchSubcommand(context, "spec", SPEC_ACTIONS, args);
    }

    private static CompletionStage<Void> cmdHelp(CommandContext context, List<String> args) {
        context.console().println("""
                Team CLI commands
                  /spec load <yaml>             load a YAML team spec
                  /spec list                    list registered specs
                  /spec show <name>             dump one spec
                  /team list                    list active runtimes + registry diff
                  /team status [name]           show runtime state for one team
                  /team monitor [name [sid]]    monitor view
                  /team start <name> <sid> [q]  first-time activation, await runtime_ready
                  /team switch <name> [sid] [q] cross-team rebuild
                  /team use <name>              switch active routing target only
                  /team pause [name]            pause active runtime
                  /team resume [name] [q]       re-activate after pause
                  /team stop [name]             tear down runtime + cancel stream
                  /team delete <name> [--force] permanently delete a team
                  /team watch <m> [name]        subscribe to a human-agent inbox
                  /team unwatch <m> [name]      clear the subscription
                  /session active               print current routing target
                  /session list                 list (team, session) pairs from this CLI
                  /session switch <sid> [q]     restart active team on a new session
                  /session release [sid] [--force]  drop dynamic tables for a session
                  /help                         this help
                  /clear                        clear the screen
                  /exit, /quit                  leave the CLI
                  ! <shell-cmd>                 run a shell command
                """);
        return completed();
    }

    private static CompletionStage<Void> cmdClear(CommandContext context, List<String> args) {
        context.console().print("\033[H\033[2J");
        context.console().flush();
        return completed();
    }

    private static CompletionStage<Void> cmdExit(CommandContext context, List<String> args) {
        throw new ExitCli();
    }

    private static CompletionStage<Void> dispatchSubcommand(
            CommandContext context,
            String group,
            Map<String, TeamCliCommandHandler> actions,
            List<String> args
    ) {
        if (args.isEmpty()) {
            printSubhelp(context.console(), group, actions);
            return completed();
        }
        String action = args.get(0);
        TeamCliCommandHandler handler = actions.get(action);
        if (handler == null) {
            printSubhelp(context.console(), group, actions);
            return completed();
        }
        return handler.handle(context, args.subList(1, args.size()));
    }

    private static void printSubhelp(PrintStream console, String group, Map<String, TeamCliCommandHandler> actions) {
        console.println("usage: /" + group + " <action>; available actions: "
                + actions.keySet().stream().sorted().collect(Collectors.joining(", ")));
    }

    private static CompletionStage<Boolean> startOrResume(
            CommandContext context,
            String teamName,
            String sessionId,
            String query
    ) {
        SpecEntry entry = context.state().getSpecRegistry().get(teamName);
        if (entry == null) {
            List<String> registered = context.state().getSpecRegistry().names();
            if (registered.isEmpty()) {
                context.console().println("[red]no spec registered for " + teamName
                        + "; load one first via `/spec load <yaml>`.[/red]");
            } else {
                context.console().println("[red]no spec registered for " + teamName + "[/red] available: "
                        + String.join(", ", registered));
            }
            return CompletableFuture.completedFuture(false);
        }
        StreamHandle existing = context.state().getStreamHandles().get(teamName);
        if (existing != null && existing.getTask() != null && !existing.getTask().isDone()) {
            context.console().println("[yellow]team " + teamName
                    + " already has a running stream; stop it before restart.[/yellow]");
            return CompletableFuture.completedFuture(false);
        }

        context.state().setPending(teamName, sessionId);
        StreamHandle handle = StreamRenderer.spawnStream(
                entry.spec(),
                sessionId,
                Map.of("query", query),
                context.terminal(),
                context.console()
        );
        context.state().getStreamHandles().put(teamName, handle);
        return handle.getRuntimeReady()
                .orTimeout((long) RUNTIME_READY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .thenApply(ack -> {
                    context.state().setActive(teamName, sessionId);
                    context.state().rememberSession(teamName, sessionId);
                    context.console().println("[green]ready[/green] team=" + teamName + " session=" + sessionId);
                    return true;
                })
                .exceptionally(error -> {
                    context.console().println("[red]runtime_ready timeout for team=" + teamName
                            + " session=" + sessionId + "; check logs.[/red]");
                    StreamRenderer.stopStream(handle).toCompletableFuture().join();
                    context.state().getStreamHandles().remove(teamName);
                    context.state().setPending(null, null);
                    return false;
                });
    }

    public static String translateReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "未知错误";
        }
        Map<String, String> direct = Map.of(
                "missing_target", "尚未选定 active team / session，先执行 `/team start` 或 `/team use`。",
                "not_active", "目标 team 不在运行池中。可能已被 stop / delete；试 `/team list` 或 `/team start`。",
                "gate_closed", "团队当前轮次已结束（gate closed），等待新一轮 wakeup 或先 `/team resume`。",
                "human_agent_not_enabled", "该 team 未启用 HITT，无法以 human-agent 身份发声。",
                "no_team_backend", "当前 team 未挂 team backend（裸 leader），不能走 operator/human-agent 通道。"
        );
        String hint = direct.get(reason);
        if (hint != null) {
            return hint;
        }
        if (reason.startsWith("unknown_human_agent:")) {
            return "未知 human-agent: " + reason.substring("unknown_human_agent:".length());
        }
        if (reason.startsWith("unknown_member:")) {
            return "未知成员: " + reason.substring("unknown_member:".length());
        }
        if (reason.startsWith("send_failed:")) {
            return "消息发送失败: " + reason.substring("send_failed:".length());
        }
        return reason;
    }

    public static void renderDeliverResult(CommandContext context, String raw, DeliverResult result) {
        if (result.ok()) {
            context.console().println("[dim][dispatch] msg_id=" + nullToDash(result.messageId()) + "[/dim]");
            return;
        }
        context.console().println("[yellow][dispatch failed][/yellow] reason=" + result.reason()
                + "  " + translateReason(result.reason()));
    }

    private static Map<String, TeamCliCommandHandler> actions(Map<String, TeamCliCommandHandler> raw) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(raw));
    }

    private static CompletionStage<Void> completed() {
        return CompletableFuture.completedFuture(null);
    }

    private static String stripLeadingSlash(String line) {
        if (line == null) {
            return "";
        }
        String trimmed = line.trim();
        return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
    }

    private static String nullToDash(String value) {
        return value == null ? "-" : value;
    }

    private static int safeSize(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private static Throwable unwrap(Throwable error) {
        if (error instanceof CompletionException && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }
}
