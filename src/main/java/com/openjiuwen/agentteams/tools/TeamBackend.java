/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.openjiuwen.agentteams.agent.Allocation;
import com.openjiuwen.agentteams.messager.Messager;
import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.schema.events.TeamEvent;
import com.openjiuwen.agentteams.schema.events.TeamTopic;
import com.openjiuwen.agentteams.schema.status.ExecutionStatus;
import com.openjiuwen.agentteams.schema.status.MemberStatus;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.spawn.SpawnContext;
import com.openjiuwen.agentteams.tools.database.DatabaseConfig;
import com.openjiuwen.agentteams.tools.database.DatabaseType;
import com.openjiuwen.agentteams.tools.database.GraphUtils;
import com.openjiuwen.agentteams.tools.database.MemberRecord;
import com.openjiuwen.agentteams.tools.database.TeamDatabase;
import com.openjiuwen.agentteams.tools.database.TeamRecord;
import com.openjiuwen.agentteams.interaction.HumanAgentInboundEvent;
import com.openjiuwen.agentteams.schema.team.TeamCompletionSnapshot;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Public class TeamBackend used by the Java parity implementation.
 *
 * @since 1.0
 */
public class TeamBackend {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Mirrors Python spawn/shared_resources.py:get_shared_db: process-global
    // singleton so every TeamBackend (leader + teammates) shares the same
    // database instance. Keyed by dbType::connectionString for persistent
    // backends; MEMORY uses a single shared instance. Lets teammates see the
    // same team/member/task rows as the leader without an explicit shareDb
    // call at spawn time (Python shares via get_shared_db in _build_team_backend).
    private static TeamDatabase sharedMemoryDb;
    private static final Map<String, TeamDatabase> SHARED_DB_INSTANCES = new LinkedHashMap<>();

    private final String teamName;
    private final String memberName;
    private final boolean isLeader;
    private final Messager messager;
    private final String displayName;
    private final String description;
    private final long created;

    // Non-final: the leader constructs the backend before its agent session is
    // resolved, so the session id is latched later via setTeamSessionId once
    // AgentSessionApi.create returns. Members set it once at construction.
    private String teamSessionId;
    private TeamDatabase db;
    private TeamMessageManager messageManager;
    private TeamTaskManager taskManager;
    private List<TeamMember> members = new ArrayList<>();
    private BiConsumer<String, String> onAutoLaunch;

    // Mirrors Python team.py: on_team_built / on_team_cleaned fire exactly
    // once on the build_team / clean_team success paths so the hosting
    // TeamAgent can persist DB lifecycle state and latch StreamController
    // state deterministically (rather than rely on the racy bus event).
    private Runnable onTeamBuilt;
    private Runnable onTeamCleaned;
    private final List<Path> cleanupPaths = new ArrayList<>();
    private final Map<String, Function<HumanAgentInboundEvent, ?>> humanAgentInboundCallbacks =
            new ConcurrentHashMap<>();

    /**
     * Create a TeamBackend for inter-member communication.
     *
     * @param teamName team id
     * @param memberName local member name
     * @param isLeader leader flag
     * @param messager messager for event publishing
     */
    public TeamBackend(String teamName, String memberName, boolean isLeader, Messager messager) {
        this(teamName, memberName, isLeader, messager, SpawnContext.getSessionId());
    }

    /**
     * Construct a TeamBackend bound to a fixed team-level session id.
     *
     * <p>The session id pins every topic this backend publishes to (TEAM / MESSAGE / TASK)
     * so leader and teammates — which may run on different threads with different
     * {@link SpawnContext} thread-locals — agree on the same topic. Without this,
     * a leader ReAct round that swaps {@link SpawnContext#getSessionId()} to its
     * own stream session publishes events onto a topic no teammate is subscribed
     * to, and {@code MEMBER_SHUTDOWN} never reaches the target member.</p>
     *
     * @param teamName team id
     * @param memberName local member name
     * @param isLeader leader flag
     * @param messager messager
     * @param teamSessionId team-level session id (the one used at subscribe time)
     * @since 0.1.13
     */
    public TeamBackend(String teamName, String memberName, boolean isLeader, Messager messager,
                       String teamSessionId) {
        this.teamName = teamName;
        this.memberName = memberName;
        this.isLeader = isLeader;
        this.messager = messager;
        this.displayName = teamName;
        this.description = "";
        this.created = System.currentTimeMillis();
        this.teamSessionId = teamSessionId != null ? teamSessionId : "";
        this.db = getSharedDb(DatabaseConfig.builder().build());
        this.db.setTeamSessionId(this.teamSessionId);
        this.db.initialize();
        Loggers.TOOL.info("TeamBackend created: db={} team={} member={} session={}",
                Integer.toHexString(System.identityHashCode(this.db)), teamName, memberName,
                this.teamSessionId);
        this.db.team.createTeam(teamName, teamName, memberName, description, null);
        this.db.member.createMember(TeamDatabase.MemberCreateParams.builder()
                .memberName(memberName).teamName(teamName)
                .displayName(memberName).agentCard("{}")
                .status(MemberStatus.READY.value()).desc(description)
                .executionStatus(null).mode("build")
                .prompt(null).modelRefJson(null)
                .role((isLeader ? TeamRole.LEADER : TeamRole.MEMBER).value())
                .build());
        this.messageManager =
                new TeamMessageManager(teamName, memberName, db, messager, this.teamSessionId);
        this.taskManager = new TeamTaskManager(teamName, memberName, db, messager, this.teamSessionId);
        this.members.add(
                TeamMember.builder()
                        .memberName(memberName)
                        .displayName(memberName)
                        .role(isLeader ? TeamRole.LEADER : TeamRole.MEMBER)
                        .status(MemberStatus.READY)
                        .build());
    }

    /**
     * Return the team-level session id pinned at construction time.
     *
     * @return the team-level session id (never {@code null}; empty string when unset)
     * @since 0.1.13
     */
    public String getTeamSessionId() {
        return teamSessionId;
    }

    /**
     * Latch the team-level session id after construction.
     *
     * <p>The leader constructs its {@code TeamBackend} before {@code AgentSessionApi.create}
     * resolves the session id, so the field starts empty. Call this once the leader's
     * session is known so subsequent {@code publishTeamEvent} / topic builds route to
     * the same topic members subscribe on. Also propagates to the message/task
     * managers so their topic builders stay consistent.</p>
     *
     * @param sessionId team-level session id; {@code null} is ignored
     * @since 0.1.13
     */
    public void setTeamSessionId(String sessionId) {
        if (sessionId == null) {
            return;
        }
        this.teamSessionId = sessionId;
        if (db != null) {
            db.setTeamSessionId(sessionId);
        }
        if (messageManager != null) {
            messageManager.setTeamSessionId(sessionId);
        }
        if (taskManager != null) {
            taskManager.setTeamSessionId(sessionId);
        }
    }

    /**
     * Return a process-global shared database instance matching {@code config}.
     *
     * <p>Mirrors Python {@code spawn/shared_resources.py:get_shared_db}. The
     * leader and every teammate share the same database instance so teammates
     * see the same team/member/task rows without an explicit share-step at
     * spawn time. MEMORY backends use a single global singleton; persistent
     * backends are keyed by {@code dbType::connectionString}.</p>
     *
     * @return the shared database instance
     */
    public static synchronized TeamDatabase getSharedDb(DatabaseConfig config) {
        DatabaseConfig effective = config != null ? config : DatabaseConfig.builder().build();
        if (effective.getDbType() == DatabaseType.MEMORY) {
            if (sharedMemoryDb == null) {
                sharedMemoryDb = new TeamDatabase(effective);
            }
            return sharedMemoryDb;
        }
        String key = buildDbKey(effective);
        return SHARED_DB_INSTANCES.computeIfAbsent(key, ignored -> new TeamDatabase(effective));
    }

    /**
     * Clear the shared database cache.
     */
    public static synchronized void resetSharedDbCache() {
        sharedMemoryDb = null;
        SHARED_DB_INSTANCES.clear();
    }

    private static String buildDbKey(DatabaseConfig config) {
        String dbType = config.getDbType() != null
                ? config.getDbType().name().toLowerCase(Locale.ROOT)
                : "sqlite";
        String connStr = config.getConnectionString() != null ? config.getConnectionString() : "";
        return dbType + "::" + connStr;
    }

    /**
     * Request spawning a new team member with default MEMBER role.
     *
     * @param memberName unique member name
     * @param displayName display name for the member
     * @param agentCard agent card describing the member's capabilities
     * @return a future that completes with {@code true} on success
     */
    public CompletableFuture<Boolean> spawnMember(
            String memberName, String displayName, AgentCard agentCard) {
        return spawnMember(SpawnMemberParams.builder()
                .memberName(memberName)
                .displayName(displayName)
                .agentCard(agentCard)
                .role(TeamRole.MEMBER)
                .build());
    }

    /**
     * Request spawning a new team member with the specified role.
     *
     * @param memberName unique member name
     * @param displayName display name for the member
     * @param agentCard agent card describing the member's capabilities
     * @param role team role for the new member
     * @return a future that completes with {@code true} on success
     */
    public CompletableFuture<Boolean> spawnMember(
            String memberName, String displayName, AgentCard agentCard, TeamRole role) {
        return spawnMember(SpawnMemberParams.builder()
                .memberName(memberName)
                .displayName(displayName)
                .agentCard(agentCard)
                .role(role)
                .build());
    }

    /**
     * Request spawning a new team member with full configuration.
     *
     * @param params spawn parameters bundling member name, display name, card, role, prompt, and allocation
     * @return a future that completes with {@code true} on success
     */
    public CompletableFuture<Boolean> spawnMember(SpawnMemberParams params) {
        String name = params.getMemberName();
        String display = params.getDisplayName();
        AgentCard card = params.getAgentCard();
        TeamRole teamRole = params.getRole();
        String teamPrompt = params.getPrompt();
        Allocation alloc = params.getAllocation();
        members.removeIf(member -> name.equals(member.getMemberName()));
        members.add(
                TeamMember.builder()
                        .memberName(name)
                        .displayName(display)
                        .description(card != null ? card.getDescription() : "")
                        .prompt(teamPrompt)
                        .role(teamRole != null ? teamRole : TeamRole.MEMBER)
                        .status(MemberStatus.UNSTARTED)
                        .build());
        db.member.createMember(TeamDatabase.MemberCreateParams.builder()
                .memberName(name).teamName(teamName)
                .displayName(display).agentCard(card != null ? card.toString() : "{}")
                .status(MemberStatus.UNSTARTED.value())
                .desc(card != null ? card.getDescription() : "")
                .executionStatus(null).mode("build")
                .prompt(teamPrompt).modelRefJson(modelRefJson(alloc))
                .role((teamRole != null ? teamRole : TeamRole.MEMBER).value())
                .build());
        return CompletableFuture.completedFuture(true);
    }

    /**
     * Parameters for spawning a new team member.
     *
     * @since 0.1.15
     */
    public static final class SpawnMemberParams {
        private final String memberName;
        private final String displayName;
        private final AgentCard agentCard;
        private final TeamRole role;
        private final String prompt;
        private final Allocation allocation;

        private SpawnMemberParams(Builder builder) {
            this.memberName = builder.memberName;
            this.displayName = builder.displayName;
            this.agentCard = builder.agentCard;
            this.role = builder.role;
            this.prompt = builder.prompt;
            this.allocation = builder.allocation;
        }

        /**
         * Get member name.
         *
         * @return the member name
         */
        public String getMemberName() {
            return memberName;
        }

        /**
         * Get display name.
         *
         * @return the display name
         */
        public String getDisplayName() {
            return displayName;
        }

        /**
         * Get agent card.
         *
         * @return the agent card
         */
        public AgentCard getAgentCard() {
            return agentCard;
        }

        /**
         * Get team role.
         *
         * @return the team role
         */
        public TeamRole getRole() {
            return role;
        }

        /**
         * Get system prompt.
         *
         * @return the system prompt
         */
        public String getPrompt() {
            return prompt;
        }

        /**
         * Get model allocation.
         *
         * @return the model allocation
         */
        public Allocation getAllocation() {
            return allocation;
        }

        /**
         * Create a new builder.
         *
         * @return a new Builder instance
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Builder for SpawnMemberParams.
         */
        public static final class Builder {
            private String memberName;
            private String displayName;
            private AgentCard agentCard;
            private TeamRole role;
            private String prompt;
            private Allocation allocation;

            /**
             * Set member name.
             *
             * @param val the member name
             * @return this builder
             */
            public Builder memberName(String val) {
                this.memberName = val;
                return this;
            }

            /**
             * Set display name.
             *
             * @param val the display name
             * @return this builder
             */
            public Builder displayName(String val) {
                this.displayName = val;
                return this;
            }

            /**
             * Set agent card.
             *
             * @param val the agent card
             * @return this builder
             */
            public Builder agentCard(AgentCard val) {
                this.agentCard = val;
                return this;
            }

            /**
             * Set team role.
             *
             * @param val the team role
             * @return this builder
             */
            public Builder role(TeamRole val) {
                this.role = val;
                return this;
            }

            /**
             * Set system prompt.
             *
             * @param val the system prompt
             * @return this builder
             */
            public Builder prompt(String val) {
                this.prompt = val;
                return this;
            }

            /**
             * Set model allocation.
             *
             * @param val the model allocation
             * @return this builder
             */
            public Builder allocation(Allocation val) {
                this.allocation = val;
                return this;
            }

            /**
             * Build the params.
             *
             * @return the constructed SpawnMemberParams
             */
            public SpawnMemberParams build() {
                return new SpawnMemberParams(this);
            }
        }
    }

    /**
     * Synchronize the local member list with the given member specs.
     *
     * @param memberSpecs list of team member specifications to sync
     */
    public void syncMembers(List<TeamMemberSpec> memberSpecs) {
        if (memberSpecs == null || memberSpecs.isEmpty()) {
            return;
        }
        Set<String> rosterNames = new LinkedHashSet<>();
        rosterNames.add(memberName);
        for (TeamMemberSpec spec : memberSpecs) {
            if (spec == null || spec.getName() == null || spec.getName().isBlank()) {
                continue;
            }
            rosterNames.add(spec.getName());
            TeamRole role = spec.getRole() != null ? spec.getRole() : TeamRole.MEMBER;
            TeamMember existing =
                    members.stream()
                            .filter(member -> spec.getName().equals(member.getMemberName()))
                            .findFirst()
                            .orElse(null);
            members.removeIf(member -> spec.getName().equals(member.getMemberName()));
            members.add(
                    TeamMember.builder()
                            .memberName(spec.getName())
                            .displayName(spec.getName())
                            .description(spec.getDescription())
                            .role(role)
                            .status(existing != null ? existing.getStatus() : defaultStatusFor(role))
                            .build());
            db.member.createMember(TeamDatabase.MemberCreateParams.builder()
                    .memberName(spec.getName()).teamName(teamName)
                    .displayName(spec.getName()).agentCard("{}")
                    .status((existing != null ? existing.getStatus()
                            : defaultStatusFor(role)).value())
                    .desc(spec.getDescription())
                    .executionStatus(null).mode("build")
                    .prompt(null).modelRefJson(null)
                    .role(role.value())
                    .build());
        }
        members.removeIf(member -> !rosterNames.contains(member.getMemberName()));
    }

    /**
     * List all team members excluding the local member.
     *
     * @return list of remote team members
     */
    public List<TeamMember> listMembers() {
        return members.stream().filter(member -> !member.getMemberName().equals(memberName)).toList();
    }

    /**
     * Register a callback invoked when a teammate is auto-launched.
     *
     * @param handler bi-consumer accepting member name and initial message
     */
    public void setOnAutoLaunch(BiConsumer<String, String> handler) {
        this.onAutoLaunch = handler;
    }

    /**
     * Accessor for the auto-launch callback.
     *
     * <p>Mirrors Python {@code SendMessageTool._on_teammate_created}: the
     * send_message tool reads this callback to drive {@code startup()}.
     * Kept as a {@code BiConsumer} because the Java {@code TeamAgent}
     * auto-launch handler still consumes an optional initial-message
     * argument (member prompt or {@code null}); the Python-aligned path
     * passes {@code null} so message delivery stays on the mailbox.</p>
     */
    public BiConsumer<String, String> getOnAutoLaunch() {
        return onAutoLaunch;
    }

    /**
     * Start all UNSTARTED members using the registered auto-launch callback.
     *
     * <p>Mirrors Python {@code tool_message.py:SendMessageTool._auto_start_members}:
     * invoked on every {@code send_message} path (broadcast / multicast /
     * unicast) to lazily start predefined members before the message is
     * delivered. Routes through {@link #startup(Consumer)} so each member
     * goes through the {@code UNSTARTED -> STARTING -> READY} CAS guard
     * rather than the force-flip shortcut. The {@code onAutoLaunch}
     * callback is invoked with a {@code null} initial message — message
     * delivery stays on the normal mailbox path, matching Python.</p>
     *
     * @return member names that were started (CAS succeeded)
     */
    public List<String> startupAllUnstarted() {
        if (onAutoLaunch == null) {
            Loggers.TOOL.warn("startupAllUnstarted: onAutoLaunch is null, cannot launch");
            return List.of();
        }
        BiConsumer<String, String> handler = onAutoLaunch;
        return startup(name -> handler.accept(name, null)).join();
    }

    /**
     * Return the team record from the database.
     *
     * @return the team record, or {@code null} if not found
     */
    public TeamRecord getTeamInfo() {
        return db.team.getTeam(teamName);
    }

    /**
     * Return the last-updated timestamp of the team record.
     *
     * @return the team updated-at timestamp
     */
    public long getTeamUpdatedAt() {
        return db.team.getTeamUpdatedAt(teamName);
    }

    /**
     * Return the maximum updated-at timestamp across all team members.
     *
     * @return the members max updated-at timestamp
     */
    public long getMembersMaxUpdatedAt() {
        return db.member.getMembersMaxUpdatedAt(teamName);
    }

    /**
     * Check whether a member with the given name exists in the team.
     *
     * @param memberName the member name to look up
     * @return true if the member exists
     */
    public boolean hasMember(String memberName) {
        return members.stream().anyMatch(member -> member.getMemberName().equals(memberName));
    }

    /**
     * Resolve a name to member_name. First tries exact member_name match,
     * then falls back to display_name lookup.
     *
     * @param name the name to resolve (member_name or display_name); may be {@code null} or blank
     * @return an {@link Optional} containing the resolved member_name,
     *     or {@link Optional#empty()} if no member matches
     */
    public Optional<String> resolveMemberName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        // Try exact member_name match
        if (hasMember(name)) {
            return Optional.of(name);
        }

        // Try display_name match
        return members.stream()
                .filter(member -> name.equals(member.getDisplayName()))
                .map(TeamMember::getMemberName)
                .findFirst()
                .map(Optional::of)
                .orElse(Optional.empty());
    }

    /**
     * Check whether a member is a human agent.
     *
     * @param memberName the member name to check
     * @return true if the member has the HUMAN_AGENT role
     */
    public boolean isHumanAgent(String memberName) {
        return members.stream()
                .anyMatch(
                        member ->
                                member.getMemberName().equals(memberName)
                                        && member.getRole() == TeamRole.HUMAN_AGENT);
    }

    /**
     * Evaluate whether the whole team has reached a completed state.
     *
     * <p>Mirrors Python {@code tools/team.py:is_team_completed}. Returns a snapshot
     * only when all three conditions hold at once, checked in order
     * task -&gt; member -&gt; message:
     * <ol>
     *   <li>At least one task exists and every task is terminal
     *       (completed / cancelled).</li>
     *   <li>Every member — including the leader — is in a settled status
     *       (READY / SHUTDOWN / PAUSED / UNSTARTED).</li>
     *   <li>No message is left unread by any member, broadcasts included.</li>
     * </ol>
     * Read-only; safe to call repeatedly.
     *
     * @return an {@link Optional} containing the snapshot when complete,
     *     or {@link Optional#empty()} otherwise
     */
    public Optional<TeamCompletionSnapshot> isTeamCompleted() {
        if (taskManager == null || messageManager == null) {
            return Optional.empty();
        }

        // Terminal: team DB row gone (clean_team succeeded). Return a terminal
        // snapshot so TeamCompletionHandler emits TEAM_COMPLETED once and the
        // leader stream closes. Without this, the three-condition check below
        // always returns null because tasks/members are cascade-deleted, and
        // the leader keeps looping on stale dispatch prompts.
        if (db.team.getTeam(teamName) == null) {
            return Optional.of(new TeamCompletionSnapshot(0, 0, true));
        }
        List<TeamTask> tasks = taskManager.list();
        if (tasks.isEmpty()) {
            return Optional.empty();
        }
        for (TeamTask task : tasks) {
            String status = task.getStatus();
            if (!GraphUtils.TASK_TERMINAL_STATUSES.contains(status)) {
                return Optional.empty();
            }
        }
        List<TeamMember> all = getAllMembers();
        if (all.isEmpty()) {
            return Optional.empty();
        }
        for (TeamMember member : all) {
            MemberStatus status = member.getStatus();

            // Mirrors Python MEMBER_SETTLED_STATUSES = {READY, PAUSED, STOPPED,
            // SHUTDOWN}. SHUTDOWN_REQUESTED and RESTARTING are NOT settled (they
            // are mid-transition) so they correctly block completion; BUSY/ERROR
            // likewise block because the member is actively executing or broken.
            if (!MemberStatus.MEMBER_SETTLED_STATUSES.contains(status.value())) {
                return Optional.empty();
            }
        }
        if (messageManager.hasUnreadMessages(true)) {
            return Optional.empty();
        }
        return Optional.of(new TeamCompletionSnapshot(all.size(), tasks.size()));
    }

    /**
     * Return the full team roster including the leader.
     *
     * <p>{@link #listMembers()} excludes the calling member; this method includes
     * everyone so {@code isTeamCompleted()} can verify the leader is settled too.
     *
     * @return unmodifiable view of all members
     */
    public List<TeamMember> getAllMembers() {
        return List.copyOf(members);
    }

    /**
     * Register a human-agent SDK inbound callback for a member.
     *
     * <p>Mirrors Python {@code TeamRuntimeManager.register_human_agent_inbound}.
     * The leader fires this callback when a MESSAGE / BROADCAST addresses a
     * human agent so the SDK can surface the inbound to the controlling human.
     *
     * @param memberName human-agent member name
     * @param callback callback fired with a {@link HumanAgentInboundEvent}
     */
    public void registerHumanAgentInbound(
            String memberName, Function<HumanAgentInboundEvent, ?> callback) {
        if (memberName != null && callback != null) {
            humanAgentInboundCallbacks.put(memberName, callback);
        }
    }

    /**
     * Return the registered inbound callback for a human agent.
     *
     * @param memberName human-agent member name
     * @return an {@link Optional} containing the callback, or {@link Optional#empty()} if none registered
     */
    public Optional<Function<HumanAgentInboundEvent, ?>> getHumanAgentInbound(String memberName) {
        if (memberName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(humanAgentInboundCallbacks.get(memberName));
    }

    /**
     * Return the member with the given name, or {@code null} if not found.
     *
     * @param memberName the member name to look up
     * @return the matching team member, or {@code null}
     */
    public TeamMember getMember(String memberName) {
        return members.stream()
                .filter(member -> member.getMemberName().equals(memberName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Update a member's status with transition validation.
     *
     * @param memberName the member name to update
     * @param status the target member status
     * @return true if the status was updated successfully
     */
    public boolean updateMemberStatus(String memberName, MemberStatus status) {
        return updateMemberStatus(memberName, status, false);
    }

    /**
     * Force-update a member's status, bypassing transition validation.
     *
     * @param memberName the member name to update
     * @param status the target member status
     * @return true if the status was updated successfully
     */
    public boolean forceUpdateMemberStatus(String memberName, MemberStatus status) {
        return updateMemberStatus(memberName, status, true);
    }

    private boolean updateMemberStatus(
            String memberName, MemberStatus status, boolean isForceEnabled) {
        TeamMember member = getMember(memberName);
        if (member == null || status == null) {
            return false;
        }
        MemberRecord record = db.member.getMember(memberName, teamName);
        String oldStatus = record != null ? record.getStatus() : member.getStatus().value();
        if (status.value().equals(oldStatus)) {
            return true;
        }
        MemberStatus currentStatus = MemberStatus.fromValue(oldStatus);
        if (!isForceEnabled && !currentStatus.canTransitionTo(status)) {
            return false;
        }
        if (!db.member.updateMemberStatus(memberName, teamName, status.value())) {
            return false;
        }
        member.setStatus(status);
        publishTeamEvent(
                TeamEvent.MEMBER_STATUS_CHANGED,
                Map.of(
                        "team_name", teamName,
                        "member_name", memberName,
                        "old_status", oldStatus,
                        "new_status", status.value()))
                .join();
        return true;
    }

    /**
     * Update a member's execution status with transition validation.
     *
     * @param memberName the member name to update
     * @param executionStatus the target execution status value
     * @return true if the execution status was updated successfully
     */
    public boolean updateMemberExecutionStatus(String memberName, String executionStatus) {
        MemberRecord record = db.member.getMember(memberName, teamName);
        if (record == null || executionStatus == null) {
            return false;
        }
        String oldStatus = record.getExecutionStatus();
        if (executionStatus.equals(oldStatus)) {
            return true;
        }
        ExecutionStatus currentStatus = ExecutionStatus.fromValue(oldStatus);
        ExecutionStatus nextStatus = ExecutionStatus.fromValue(executionStatus);
        if (!currentStatus.canTransitionTo(nextStatus)) {
            return false;
        }
        if (!db.member.updateMemberExecutionStatus(memberName, teamName, executionStatus)) {
            return false;
        }
        publishTeamEvent(
                TeamEvent.MEMBER_EXECUTION_CHANGED,
                Map.of(
                        "team_name", teamName,
                        "member_name", memberName,
                        "old_status", oldStatus != null ? oldStatus : "",
                        "new_status", executionStatus))
                .join();
        return true;
    }

    /**
     * Approve or reject a member plan submission by {@code plan_id}.
     *
     * <p>Mirrors Python 0.1.15 {@code team.py:TeamBackend.approve_plan}. Looks
     * up the plan record, validates the assignee exists, then delegates to
     * {@link TeamTaskManager#approvePlanResult(String, boolean, String, String)}.
     * Returns {@code false} when the plan id is unknown or the task manager
     * rejects the transition.</p>
     */
    public CompletableFuture<Boolean> approvePlan(String planId, boolean isApproved, String feedback) {
        if (planId == null || planId.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        Map<String, Object> planRecord = taskManager.getPlanRecord(planId);
        if (planRecord == null || planRecord.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        Object memberNameObj = planRecord.get("member_name");
        String memberNameFromPlan = memberNameObj == null ? "" : memberNameObj.toString().trim();
        if (memberNameFromPlan.isBlank()
                || db.member.getMember(memberNameFromPlan, teamName) == null) {
            return CompletableFuture.completedFuture(false);
        }
        TaskOpResult result =
                taskManager.approvePlanResult(planId, isApproved, feedback, memberName).join();
        if (!result.isOk()) {
            Loggers.TOOL.info("approvePlan: plan_id={} rejected by task manager: {}",
                    planId, result.getReason());
            return CompletableFuture.completedFuture(false);
        }
        return publishTeamEvent(
                TeamEvent.PLAN_APPROVAL,
                Map.of(
                        "team_name", teamName,
                        "plan_id", planId,
                        "member_name", memberNameFromPlan,
                        "approved", isApproved))
                .thenApply(ignored -> true);
    }

    /**
     * Approve or reject a member plan submission by {@code plan_id} with explicit leader name.
     *
     * <p>Mirrors Python {@code team.py:approve_plan(plan_id, approved, feedback)}
     * where the leader name defaults to {@code this.memberName}. Kept as a
     * separate overload so the {@code leader_name} argument is explicit when
     * callers need to override the default.</p>
     */
    public CompletableFuture<Boolean> approvePlan(
            String planId, boolean isApproved, String feedback, String leaderName) {
        if (planId == null || planId.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        Map<String, Object> planRecord = taskManager.getPlanRecord(planId);
        if (planRecord == null || planRecord.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        Object memberNameObj = planRecord.get("member_name");
        String memberNameFromPlan = memberNameObj == null ? "" : memberNameObj.toString().trim();
        if (memberNameFromPlan.isBlank()
                || db.member.getMember(memberNameFromPlan, teamName) == null) {
            return CompletableFuture.completedFuture(false);
        }
        TaskOpResult result =
                taskManager.approvePlanResult(planId, isApproved, feedback, leaderName).join();
        if (!result.isOk()) {
            Loggers.TOOL.info("approvePlan: plan_id={} rejected by task manager: {}",
                    planId, result.getReason());
            return CompletableFuture.completedFuture(false);
        }
        return publishTeamEvent(
                TeamEvent.PLAN_APPROVAL,
                Map.of(
                        "team_name", teamName,
                        "plan_id", planId,
                        "member_name", memberNameFromPlan,
                        "approved", isApproved))
                .thenApply(ignored -> true);
    }

    /**
     * Approve or reject a tool call for a team member.
     *
     * @param memberName the member name whose tool call is being approved
     * @param toolCallId the tool call id to approve or reject
     * @param isApproved whether the tool call is approved
     * @param feedback optional feedback for the member
     * @param shouldAutoConfirm whether to auto-confirm future tool calls
     * @return a future that completes with {@code true} on success
     */
    public CompletableFuture<Boolean> approveTool(
            String memberName,
            String toolCallId,
            boolean isApproved,
            String feedback,
            boolean shouldAutoConfirm) {
        if (db.member.getMember(memberName, teamName) == null) {
            return CompletableFuture.completedFuture(false);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("team_name", teamName);
        payload.put("member_name", memberName);
        payload.put("tool_call_id", toolCallId);
        payload.put("approved", isApproved);
        payload.put("feedback", feedback != null ? feedback : "");
        payload.put("auto_confirm", shouldAutoConfirm);
        return publishTeamEvent(TeamEvent.TOOL_APPROVAL_RESULT, payload).thenApply(ignored -> true);
    }

    /**
     * Cancel a task and notify the assignee if the task was claimed.
     *
     * <p>Mirrors Python {@code team.py:TeamBackend.cancel_task}. Idempotent:
     * cancelling an already-cancelled task returns {@code true} without
     * republishing. When the task has an assignee, a notification message is
     * sent so the teammate knows its claimed task was pulled back.</p>
     */
    public CompletableFuture<Boolean> cancelTask(String taskId) {
        Optional<TeamTask> taskOpt = taskManager.get(taskId);
        if (taskOpt.isEmpty()) {
            Loggers.TOOL.info("cancelTask: task {} not found", taskId);
            return CompletableFuture.completedFuture(false);
        }
        TeamTask task = taskOpt.get();
        if ("cancelled".equals(task.getStatus())) {
            return CompletableFuture.completedFuture(true);
        }
        TeamTask cancelled = taskManager.cancel(taskId).join();
        if (cancelled == null) {
            return CompletableFuture.completedFuture(false);
        }
        if (task.getAssignee() != null && !task.getAssignee().isBlank()) {
            String content = "Task '" + task.getTitle() + "' (ID: " + taskId
                    + ") has been cancelled by the team leader.";
            messageManager.sendMessage(content, task.getAssignee());
        }
        return CompletableFuture.completedFuture(true);
    }

    /**
     * Cancel every active task in the team and broadcast the result.
     *
     * <p>Mirrors Python {@code team.py:TeamBackend.cancel_all_tasks}. Honors
     * the HITT human-agent-locked guarantee via {@code skipAssignees}: any
     * task assigned to one of the named members is preserved.</p>
     */
    public CompletableFuture<Integer> cancelAllTasks(Set<String> skipAssignees) {
        List<TeamTask> cancelled = taskManager
                .cancelAllTasks(skipAssignees != null ? List.copyOf(skipAssignees) : List.of())
                .join();
        if (cancelled.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        String content = "All tasks (" + cancelled.size()
                + ") have been cancelled by team leader.";
        return messageManager
                .broadcastMessage(content)
                .thenApply(ignored -> cancelled.size());
    }

    /**
     * Atomically transition an UNSTARTED member to STARTING and spawn it.
     *
     * <p>Mirrors Python 0.1.15 {@code team.py:TeamBackend.startup_member}.
     * Uses {@link com.openjiuwen.agentteams.tools.database.TeamDatabase.MemberDao#tryTransitionMemberStatus}
     * as a CAS guard so concurrent startup paths cannot double-spawn a member.
     * On spawn-failure rolls STARTING &rarr; UNSTARTED so the member can be
     * retried.</p>
     */
    public CompletableFuture<Boolean> startupMember(String memberName, Consumer<String> onCreated) {
        boolean isTransitioned = db.member.tryTransitionMemberStatus(
                memberName, teamName,
                MemberStatus.UNSTARTED.value(), MemberStatus.STARTING.value());
        if (!isTransitioned) {
            Loggers.TOOL.info("startupMember: CAS failed for member={} (not UNSTARTED or already starting)",
                    memberName);
            return CompletableFuture.completedFuture(false);
        }
        try {
            onCreated.accept(memberName);
        } catch (IllegalStateException | NullPointerException
                | IllegalArgumentException | UnsupportedOperationException e) {
            Loggers.TOOL.error("startupMember: on_created threw for member={}, rolling back",
                    memberName, e);
            db.member.tryTransitionMemberStatus(
                    memberName, teamName,
                    MemberStatus.STARTING.value(), MemberStatus.UNSTARTED.value());
            return CompletableFuture.completedFuture(false);
        }
        return spawnAndPublish(memberName)
                .thenApply(
                        ignored -> {
                            db.member.updateMemberStatus(
                                    memberName, teamName, MemberStatus.READY.value());
                            TeamMember member = getMember(memberName);
                            if (member != null) {
                                member.setStatus(MemberStatus.READY);
                            }
                            return true;
                        })
                .exceptionally(
                        e -> {
                            Loggers.TOOL.error("startupMember: spawn/publish failed for member={}, rolling back",
                                    memberName, e);
                            db.member.tryTransitionMemberStatus(
                                    memberName, teamName,
                                    MemberStatus.STARTING.value(), MemberStatus.UNSTARTED.value());
                            return false;
                        });
    }

    /**
     * Start all UNSTARTED members.
     *
     * <p>Mirrors Python {@code team.py:TeamBackend.startup}. Returns the
     * member names that were started (CAS succeeded). Concurrent callers
     * racing the same member lose the CAS and the member is not double-counted.</p>
     */
    public CompletableFuture<List<String>> startup(Consumer<String> onCreated) {
        List<String> started = new ArrayList<>();
        for (MemberRecord record : db.member.getTeamMembers(teamName, MemberStatus.UNSTARTED.value())) {
            boolean isOk = startupMember(record.getMemberName(), onCreated).join();
            if (isOk) {
                started.add(record.getMemberName());
            }
        }
        return CompletableFuture.completedFuture(started);
    }

    private CompletableFuture<Void> spawnAndPublish(String memberName) {
        return publishTeamEvent(
                TeamEvent.MEMBER_SPAWNED,
                Map.of("team_name", teamName, "member_name", memberName));
    }

    /**
     * Create the team row, register the leader as BUSY, and publish TeamCreatedEvent.
     *
     * <p>Mirrors Python 0.1.15 {@code team.py:TeamBackend.build_team} for the
     * collaboration-only scope: HITT human-agent registration and Bridge-agent
     * registration are out of scope here, so predefined members with those
     * roles are silently skipped. Predefined teammates registered via
     * {@link #spawnMember} / {@link #syncMembers} are left UNSTARTED for the
     * later {@code startup()} pass. Fires {@code onTeamBuilt} exactly once.</p>
     */
    public CompletableFuture<Void> buildTeam(
            String displayName, String desc, String leaderDisplayName, String leaderDesc) {
        // Constructor already created the team row + leader member. Promote
        // the leader to BUSY to mirror Python's "leader starts busy/running
        // immediately" semantics.
        forceUpdateMemberStatus(memberName, MemberStatus.BUSY);
        if (onTeamBuilt != null) {
            try {
                onTeamBuilt.run();
            } catch (IllegalStateException | NullPointerException
                    | IllegalArgumentException | UnsupportedOperationException e) {
                Loggers.TOOL.error("buildTeam: on_team_built callback failed for team={}",
                        teamName, e);
            }
        }
        return publishTeamEvent(
                TeamEvent.CREATED,
                Map.of(
                        "team_name", teamName,
                        "display_name", displayName,
                        "leader_member_name", memberName,
                        "created", System.currentTimeMillis()))
                .thenApply(ignored -> null);
    }

    /**
     * Register a filesystem path to remove on {@link #cleanTeam()}.
     *
     * <p>Mirrors Python {@code team.py:TeamBackend.register_cleanup_path}. The
     * hosting TeamAgent wires the actual resolved workspace / member-workspace
     * directories here so cleanup wipes the real locations.</p>
     */
    public void registerCleanupPath(Path path) {
        if (path != null && !path.toString().isBlank()) {
            cleanupPaths.add(path.toAbsolutePath().normalize());
        }
    }

    /**
     * Register a callback fired when the team is built.
     *
     * @param onTeamBuilt callback to fire on team build
     */
    public void setOnTeamBuilt(Runnable onTeamBuilt) {
        this.onTeamBuilt = onTeamBuilt;
    }

    /**
     * Register a callback fired when the team is cleaned.
     *
     * @param onTeamCleaned callback to fire on team cleanup
     */
    public void setOnTeamCleaned(Runnable onTeamCleaned) {
        this.onTeamCleaned = onTeamCleaned;
    }

    /**
     * Force cleanup for the current session's team state.
     *
     * <p>Mirrors Python {@code team.py:TeamBackend.force_clean_team}. Unlike
     * {@link #cleanTeam()}, this does not wait for every member to reach
     * SHUTDOWN. Used during session switching to aggressively discard the old
     * team's runtime and persisted session data.</p>
     */
    public CompletableFuture<Boolean> forceCleanTeam(boolean shouldShutdownMembers) {
        if (shouldShutdownMembers) {
            for (MemberRecord record : db.member.getTeamMembers(teamName)) {
                if (memberName.equals(record.getMemberName())) {
                    continue;
                }
                try {
                    shutdownMember(record.getMemberName(), true).join();
                } catch (CompletionException e) {
                    Loggers.TOOL.warn("forceCleanTeam: shutdown failed for member={}: {}",
                            record.getMemberName(), e.getMessage());
                }
            }
        }
        boolean isDeleted = db.team.deleteTeam(teamName);
        try {
            removeCleanupPaths();
        } catch (IllegalStateException | NullPointerException
                | IllegalArgumentException | UnsupportedOperationException e) {
            Loggers.TOOL.error("forceCleanTeam: removeCleanupPaths failed: {}",
                    e.getMessage());
            isDeleted = false;
        }
        members.removeIf(member -> !memberName.equals(member.getMemberName()));
        if (isDeleted) {
            Loggers.TOOL.info("forceCleanTeam: team {} force cleaned successfully", teamName);
        }
        return CompletableFuture.completedFuture(isDeleted);
    }

    private void removeCleanupPaths() {
        if (cleanupPaths.isEmpty()) {
            return;
        }
        List<Path> ordered = new ArrayList<>(cleanupPaths);
        ordered.sort((left, right) -> Integer.compare(
                right.getNameCount(), left.getNameCount()));
        for (Path path : ordered) {
            if (!Files.isDirectory(path)) {
                continue;
            }
            try {
                deleteRecursively(path);
                Loggers.TOOL.info("Removed team filesystem path: {}", path);
            } catch (IllegalStateException | NullPointerException
                    | IllegalArgumentException | UnsupportedOperationException e) {
                Loggers.TOOL.error("Failed to remove path {}: {}", path,
                        e.getMessage());
            }
        }
        cleanupPaths.clear();
    }

    private static void deleteRecursively(Path path) {
        try (var stream = Files.walk(path)) {
            stream.sorted((left, right) -> right.compareTo(left))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // best-effort; logged at call site
                        }
                    });
        } catch (IOException ignored) {
            // best-effort; logged at call site
        }
    }

    /**
     * Shutdown a member, returning a {@link MemberOpResult} carrying the
     * failure reason.
     *
     * <p>Mirrors Python 0.1.15 {@code team.py:TeamBackend.shutdown_member}.
     * Returns {@code MemberOpResult.success()} on the idempotent
     * already-shutdown path and on a successful shutdown request. On failure
     * (member missing, invalid transition, DB rejection) returns
     * {@code MemberOpResult.fail(reason)} so the leader's tool layer can
     * surface the cause to the LLM rather than a bare {@code false}.</p>
     */
    public CompletableFuture<MemberOpResult> shutdownMember(String memberName) {
        return shutdownMember(memberName, false);
    }

    /**
     * Shutdown a member with optional force flag.
     *
     * @param memberName the member name to shut down
     * @param isForceEnabled whether to force the shutdown
     * @return a future with the member operation result
     */
    public CompletableFuture<MemberOpResult> shutdownMember(String memberName, boolean isForceEnabled) {
        MemberRecord record = db.member.getMember(memberName, teamName);
        if (record == null) {
            return CompletableFuture.completedFuture(
                    MemberOpResult.fail("Member " + memberName + " not found in team " + teamName));
        }
        MemberStatus current = MemberStatus.fromValue(record.getStatus());
        if (current == MemberStatus.SHUTDOWN || current == MemberStatus.SHUTDOWN_REQUESTED) {
            return CompletableFuture.completedFuture(MemberOpResult.success());
        }
        if (!current.canTransitionTo(MemberStatus.SHUTDOWN_REQUESTED)) {
            return CompletableFuture.completedFuture(MemberOpResult.fail(
                    "Member " + memberName + " cannot shut down from status '" + current.value() + "'"));
        }
        if (!updateMemberStatus(memberName, MemberStatus.SHUTDOWN_REQUESTED)) {
            return CompletableFuture.completedFuture(MemberOpResult.fail(
                    "Database rejected status update for member " + memberName));
        }
        return messageManager
                .sendMessage("Shutdown requested by team leader.", memberName)
                .thenCompose(
                        ignored ->
                                publishTeamEvent(
                                        TeamEvent.MEMBER_SHUTDOWN,
                                        Map.of(
                                                "team_name", teamName,
                                                "member_name", memberName,
                                                "isForceEnabled", isForceEnabled)))
                .thenApply(ignored -> MemberOpResult.success());
    }

    /**
     * Cancel a running member, resetting its claimed tasks and notifying it.
     *
     * @param memberName the member name to cancel
     * @return a future that completes with {@code true} on success
     */
    public CompletableFuture<Boolean> cancelMember(String memberName) {
        MemberRecord record = db.member.getMember(memberName, teamName);
        if (record == null) {
            return CompletableFuture.completedFuture(false);
        }
        MemberStatus current = MemberStatus.fromValue(record.getStatus());
        if (current != MemberStatus.BUSY) {
            return CompletableFuture.completedFuture(true);
        }
        CompletableFuture<Void> resetChain = CompletableFuture.completedFuture(null);
        for (TeamTask task : taskManager.getTasksByAssignee(memberName, "claimed")) {
            resetChain =
                    resetChain.thenCompose(
                            ignored -> taskManager.reset(task.getTaskId()).thenApply(done -> null));
        }
        return resetChain
                .thenCompose(
                        ignored -> messageManager.sendMessage("Cancel requested by team leader.", memberName))
                .thenCompose(
                        ignored ->
                                publishTeamEvent(
                                        TeamEvent.MEMBER_CANCELED,
                                        Map.of(
                                                "team_name", teamName,
                                                "member_name", memberName)))
                .thenApply(ignored -> true);
    }

    /**
     * Clean up the team.
     *
     * <p>Mirrors Python 0.1.15 {@code team.py:TeamBackend.clean_team}. After
     * the team DB row is deleted, fires {@code on_team_cleaned} so the
     * hosting TeamAgent can latch StreamController state deterministically,
     * then removes every registered cleanup path (team shared workspace,
     * member workspaces, team-named parent dir). The event publish is
     * best-effort -- the durable source of truth is the DB row delete.</p>
     */
    public CompletableFuture<Boolean> cleanTeam() {
        var memberRecords = db.member.getTeamMembers(teamName);
        Loggers.TOOL.info("cleanTeam: teamName={} memberRecords={} membersList={}",
                teamName,
                memberRecords.stream().map(r -> r.getMemberName() + "=" + r.getStatus()).toList(),
                members.stream().map(m -> m.getMemberName() + "=" + m.getStatus()).toList());
        for (MemberRecord record : memberRecords) {
            if (memberName.equals(record.getMemberName())) {
                continue;
            }
            if (!MemberStatus.SHUTDOWN.value().equals(record.getStatus())) {
                Loggers.TOOL.warn("cleanTeam: member {} is not SHUTDOWN (status={}), returning false",
                        record.getMemberName(), record.getStatus());
                return CompletableFuture.completedFuture(false);
            }
        }
        boolean isDeleted = db.team.deleteTeam(teamName);
        Loggers.TOOL.info("cleanTeam: deleteTeam({}) returned {}", teamName, isDeleted);
        if (!isDeleted) {
            return CompletableFuture.completedFuture(false);
        }
        if (onTeamCleaned != null) {
            try {
                onTeamCleaned.run();
            } catch (IllegalStateException | NullPointerException
                    | IllegalArgumentException | UnsupportedOperationException e) {
                Loggers.TOOL.error(
                        "cleanTeam: on_team_cleaned callback failed for team={}: {}",
                        teamName, e.getMessage());
            }
        }
        try {
            removeCleanupPaths();
        } catch (IllegalStateException | NullPointerException
                | IllegalArgumentException | UnsupportedOperationException e) {
            Loggers.TOOL.error("cleanTeam: removeCleanupPaths failed: {}",
                    e.getMessage());
        }
        members.removeIf(member -> !memberName.equals(member.getMemberName()));
        return publishTeamEvent(TeamEvent.CLEANED, Map.of("team_name", teamName))
                .thenApply(ignored -> true);
    }

    /**
     * Return the names of all human-agent members in the team.
     *
     * @return unmodifiable set of human-agent member names
     */
    public Set<String> humanAgentNames() {
        Set<String> names = new LinkedHashSet<>();
        for (TeamMember member : members) {
            if (member.getRole() == TeamRole.HUMAN_AGENT) {
                names.add(member.getMemberName());
            }
        }
        return Set.copyOf(names);
    }

    /**
     * Check whether human-in-the-team (HITT) is enabled.
     *
     * @return true if at least one human-agent member exists
     */
    public boolean hittEnabled() {
        return !humanAgentNames().isEmpty();
    }

    /**
     * Return the message manager for team communication.
     *
     * @return the team message manager
     */
    public TeamMessageManager getMessageManager() {
        return messageManager;
    }

    /**
     * Return the task manager for team task operations.
     *
     * @return the team task manager
     */
    public TeamTaskManager getTaskManager() {
        return taskManager;
    }

    /**
     * Return the team database instance.
     *
     * @return the team database
     */
    public TeamDatabase getDb() {
        return db;
    }

    /**
     * Return the messager used for event publishing.
     *
     * @return the messager instance
     */
    public Messager getMessager() {
        return messager;
    }

    /**
     * Return the team name.
     *
     * @return the team name
     */
    public String getTeamName() {
        return teamName;
    }

    /**
     * Return the local member name.
     *
     * @return the member name
     */
    public String getMemberName() {
        return memberName;
    }

    /**
     * Return whether this backend belongs to the team leader.
     *
     * @return true if this is the leader
     */
    public boolean isLeader() {
        return isLeader;
    }

    /**
     * Return the display name of the team.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Return the team description.
     *
     * @return the description string
     */
    public String getDescription() {
        return description;
    }

    /**
     * Return the creation timestamp of this backend.
     *
     * @return the creation timestamp in milliseconds
     */
    public long getCreated() {
        return created;
    }

    private MemberStatus defaultStatusFor(TeamRole role) {
        return role == TeamRole.LEADER ? MemberStatus.READY : MemberStatus.UNSTARTED;
    }

    private static String modelRefJson(Allocation allocation) {
        if (allocation == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(allocation.toDbRef());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize model allocation reference", e);
        }
    }

    private CompletableFuture<Void> publishTeamEvent(String eventType, Map<String, Object> payload) {
        // Mirrors Python team.py: TeamTopic.TEAM.build(get_session_id(), team_name).
        // Use the team-level session id pinned at construction time so events reach
        // members that subscribed with the same team session — regardless of which
        // ReAct-stream session the leader thread happens to be in.
        return messager.publish(
                TeamTopic.TEAM.build(teamSessionId, teamName),
                EventMessage.builder().eventType(eventType).payload(payload).build());
    }
}
