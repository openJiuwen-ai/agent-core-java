/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.PrivateAgentResources;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamAgentBlueprint;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.coordination.CoordinationKernel.StreamControllerView;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.session.stream.OutputSchema;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages agent execution rounds, streaming, and input delivery.
 *
 * <p>Mirrors Python's {@code StreamController} in
 * {@code openjiuwen/agent_teams/agent/stream_controller.py}.</p>
 */
public class StreamController implements StreamControllerView, SpawnManager.StreamController {

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;
    private static final int MAX_RETRY_ATTEMPTS = 10;
    private static final int RETRYABLE_ERROR_CODE = 181001;
    private static final String RETRY_QUERY = "鍒氭墠鏈夊紓甯哥姸鍐碉紝缁х画鎵ц";
    private static final String TASK_FAILED_PAYLOAD_TYPE = "task_failed";
    private static final Pattern ERROR_CODE_PATTERN = Pattern.compile("^\\[(\\d+)]");

    private final Supplier<TeamAgentBlueprint> blueprintGetter;
    private final TeamAgentState state;
    private final PrivateAgentResources resources;
    private final StatusUpdater statusUpdater;
    private final ExecutionUpdater executionUpdater;
    private final Supplier<CompletionStage<Void>> wakeMailboxCallback;
    private final Supplier<CompletionStage<Void>> requestCompletionPollCallback;
    private final Queue<Object> streamQueue = new LinkedList<>();
    private final QueueChunkQueue chunkQueue = new QueueChunkQueue();
    private final List<Object> pendingInterruptResumes = new ArrayList<>();
    private final List<Object> pendingInputs = new ArrayList<>();
    private final List<SpawnManager.ChunkObserver> chunkObservers = new ArrayList<>();
    private CompletableFuture<Void> agentTask;
    private boolean streamingActive;
    private boolean cancelRequested;

    public StreamController(
            Supplier<TeamAgentBlueprint> blueprintGetter,
            TeamAgentState state,
            PrivateAgentResources resources,
            StatusUpdater statusUpdater,
            ExecutionUpdater executionUpdater
    ) {
        this(blueprintGetter, state, resources, statusUpdater, executionUpdater, null, null);
    }

    public StreamController(
            Supplier<TeamAgentBlueprint> blueprintGetter,
            TeamAgentState state,
            PrivateAgentResources resources,
            StatusUpdater statusUpdater,
            ExecutionUpdater executionUpdater,
            Supplier<CompletionStage<Void>> wakeMailboxCallback,
            Supplier<CompletionStage<Void>> requestCompletionPollCallback
    ) {
        this.blueprintGetter = blueprintGetter;
        this.state = state;
        this.resources = resources;
        this.statusUpdater = statusUpdater;
        this.executionUpdater = executionUpdater;
        this.wakeMailboxCallback = wakeMailboxCallback;
        this.requestCompletionPollCallback = requestCompletionPollCallback;
    }

    public Queue<Object> getRawStreamQueue() {
        return streamQueue;
    }

    public CompletableFuture<Void> getAgentTask() {
        return agentTask;
    }

    public List<Object> getPendingInterruptResumes() {
        return pendingInterruptResumes;
    }

    public List<Object> getPendingInputs() {
        return pendingInputs;
    }

    public boolean isAgentRunning() {
        return streamingActive;
    }

    public boolean hasInFlightRound() {
        return agentTask != null && !agentTask.isDone();
    }

    public boolean hasPendingInterrupt() {
        MemberRuntime harness = resources.getHarness();
        return harness != null && harness.hasPendingInterrupt();
    }

    public CompletionStage<Void> startRound(Object content) {
        if (resources.getHarness() == null) {
            return CompletableFuture.completedFuture(null);
        }
        TEAM_LOGGER.info("[%s] start_agent: %.120s", memberNameOrQuestion(), String.valueOf(content));
        agentTask = CompletableFuture.runAsync(() -> runOneRound(content).toCompletableFuture().join());
        agentTask.whenComplete((ignored, error) -> logAgentTaskException(error));
        return CompletableFuture.completedFuture(null);
    }

    public CompletionStage<Void> steer(String content) {
        MemberRuntime harness = resources.getHarness();
        return harness == null ? CompletableFuture.completedFuture(null) : harness.steer(content);
    }

    public CompletionStage<Void> followUp(String content) {
        MemberRuntime harness = resources.getHarness();
        return harness == null ? CompletableFuture.completedFuture(null) : harness.followUp(content);
    }

    public CompletionStage<Void> cancelAgent() {
        if (agentTask == null || agentTask.isDone()) {
            return CompletableFuture.completedFuture(null);
        }
        return updateExecution(ExecutionStatus.CANCEL_REQUESTED)
                .thenCompose(ignored -> updateExecution(ExecutionStatus.CANCELLING))
                .thenCompose(ignored -> cooperativeCancel());
    }

    @Override
    public void closeStream() {
        streamQueue.offer(null);
    }

    @Override
    public void clearStreamQueue() {
        streamQueue.clear();
    }

    public void emitCompletionAndClose(int memberCount, int taskCount) {
        streamQueue.offer(new TeamOutputChunk(
                "message",
                0,
                Map.of("event_type", "team.completed", "member_count", memberCount, "task_count", taskCount),
                memberName(),
                role()
        ));
        closeStream();
    }

    @Override
    public CompletionStage<Void> drainAgentTask() {
        pendingInputs.clear();
        pendingInterruptResumes.clear();
        return cancelAgent();
    }

    public CompletionStage<Void> cooperativeCancel() {
        if (agentTask == null || agentTask.isDone()) {
            return CompletableFuture.completedFuture(null);
        }
        cancelRequested = true;
        MemberRuntime harness = resources.getHarness();
        CompletionStage<Void> aborted = harness == null ? CompletableFuture.completedFuture(null) : harness.abort();
        return aborted.handle((ignored, error) -> {
            if (!agentTask.isDone()) {
                agentTask.cancel(true);
            }
            return null;
        });
    }

    @Override
    public SpawnManager.ChunkQueue getStreamQueue() {
        return chunkQueue;
    }

    @Override
    public void addChunkObserver(SpawnManager.ChunkObserver observer) {
        chunkObservers.add(observer);
    }

    @Override
    public void removeChunkObserver(SpawnManager.ChunkObserver observer) {
        chunkObservers.remove(observer);
    }

    public Object tagChunk(Object chunk) {
        String memberName = memberName();
        TeamRole role = role();
        if (memberName == null || !(chunk instanceof OutputSchema output)) {
            return chunk;
        }
        if (chunk instanceof TeamOutputChunk teamChunk) {
            if (memberName.equals(teamChunk.getSourceMember()) && role == teamChunk.getRole()) {
                return teamChunk;
            }
            return new TeamOutputChunk(
                    teamChunk.getType(),
                    teamChunk.getIndex(),
                    teamChunk.getPayload(),
                    memberName,
                    role
            );
        }
        return new TeamOutputChunk(output.getType(), output.getIndex(), output.getPayload(), memberName, role);
    }

    public CompletionStage<Void> runOneRound(Object message) {
        cancelRequested = false;
        MemberRuntime harness = resources.getHarness();
        if (harness != null) {
            harness.initCwdForRound();
        }
        return updateStatus(MemberStatus.READY)
                .thenCompose(ignored -> updateStatus(MemberStatus.BUSY))
                .thenCompose(ignored -> executeRound(message))
                .thenCompose(ignored -> updateReadyUnlessShutdownRequested())
                .handle((ignored, error) -> {
                    agentTask = null;
                    if (error != null) {
                        TEAM_LOGGER.error("Failed to execute deep agent, %s", error.getMessage());
                        updateStatus(MemberStatus.ERROR).toCompletableFuture().join();
                    }
                    handleRoundFinally();
                    return null;
                });
    }

    public CompletionStage<StreamFailure> streamOneRound(Object query) {
        MemberRuntime harness = resources.getHarness();
        if (harness == null) {
            return CompletableFuture.completedFuture(null);
        }
        boolean errorSeen = false;
        Integer errorCode = null;
        String errorText = "";
        streamingActive = true;
        try {
            Iterator<Object> iterator = harness.runStreaming(Map.of("query", query), AgentTeamsContext.getSessionId());
            while (iterator.hasNext()) {
                Object chunk = iterator.next();
                if (errorSeen) {
                    continue;
                }
                StreamFailure detected = detectTaskFailed(chunk);
                if (detected != null) {
                    errorSeen = true;
                    errorCode = detected.errorCode();
                    errorText = detected.errorText();
                    continue;
                }
                Object tagged = tagChunk(chunk);
                streamQueue.offer(tagged);
                fanOut(tagged);
            }
        } finally {
            streamingActive = false;
        }
        if (!errorSeen) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.completedFuture(new StreamFailure(errorCode, errorText));
    }

    public CompletionStage<Void> runRetryingStream(Object initialQuery) {
        Object currentQuery = initialQuery;
        int attempt = 0;
        while (true) {
            StreamFailure outcome = streamOneRound(currentQuery).toCompletableFuture().join();
            if (outcome == null) {
                return CompletableFuture.completedFuture(null);
            }
            if (outcome.errorCode() != null
                    && outcome.errorCode() == RETRYABLE_ERROR_CODE
                    && attempt < MAX_RETRY_ATTEMPTS) {
                attempt++;
                TEAM_LOGGER.warning(
                        "DeepAgent round transient error (code=%s, attempt=%d/%d): %s",
                        outcome.errorCode(),
                        attempt,
                        MAX_RETRY_ATTEMPTS,
                        outcome.errorText()
                );
                currentQuery = RETRY_QUERY;
                continue;
            }
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "streaming task failed after " + attempt + " retries, last error code="
                            + outcome.errorCode() + ": " + outcome.errorText()
            ));
        }
    }

    public CompletionStage<Void> executeRound(Object message) {
        return updateExecution(ExecutionStatus.STARTING)
                .thenCompose(ignored -> updateExecution(ExecutionStatus.RUNNING))
                .thenCompose(ignored -> runRetryingStream(message))
                .thenCompose(ignored -> cancelRequested
                        ? updateExecution(ExecutionStatus.CANCELLED)
                        : updateExecution(ExecutionStatus.COMPLETING)
                                .thenCompose(next -> updateExecution(ExecutionStatus.COMPLETED)))
                .exceptionallyCompose(error -> updateExecution(ExecutionStatus.FAILED)
                        .thenCompose(ignored -> CompletableFuture.failedFuture(error)))
                .whenComplete((ignored, error) -> updateExecution(ExecutionStatus.IDLE).toCompletableFuture().join());
    }

    public boolean isValidInterruptResume(Object userInput) {
        MemberRuntime harness = resources.getHarness();
        return harness != null && harness.isPendingInterruptResumeValid(userInput);
    }

    public Object dequeueValidInterruptResume() {
        while (!pendingInterruptResumes.isEmpty()) {
            Object candidate = pendingInterruptResumes.remove(0);
            if (isValidInterruptResume(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private void handleRoundFinally() {
        if (state.isTeamCleaned()) {
            closeStream();
            return;
        }
        if (cancelRequested) {
            return;
        }
        Object nextResume = dequeueValidInterruptResume();
        if (nextResume != null) {
            startRound(nextResume);
            return;
        }
        if (!pendingInputs.isEmpty()) {
            List<Object> drained = new ArrayList<>(pendingInputs);
            pendingInputs.clear();
            Object combined = drained.size() == 1
                    ? drained.get(0)
                    : String.join("\n\n---\n\n", drained.stream().map(String::valueOf).toList());
            startRound(combined);
            return;
        }
        wakeMailboxIfInterruptCleared().toCompletableFuture().join();
        if (requestCompletionPollCallback != null) {
            requestCompletionPollCallback.get().toCompletableFuture().join();
        }
    }

    private CompletionStage<Void> updateReadyUnlessShutdownRequested() {
        TeamMember teamMember = state.getTeamMember();
        if (teamMember == null) {
            return updateStatus(MemberStatus.READY);
        }
        return teamMember.status().thenCompose(status ->
                status == MemberStatus.SHUTDOWN_REQUESTED
                        ? CompletableFuture.completedFuture(null)
                        : updateStatus(MemberStatus.READY));
    }

    private CompletionStage<Void> wakeMailboxIfInterruptCleared() {
        if (wakeMailboxCallback == null) {
            return CompletableFuture.completedFuture(null);
        }
        return wakeMailboxCallback.get();
    }

    private void fanOut(Object tagged) {
        for (SpawnManager.ChunkObserver observer : List.copyOf(chunkObservers)) {
            try {
                observer.onChunk(tagged).toCompletableFuture().join();
            } catch (RuntimeException exception) {
                TEAM_LOGGER.error("[%s] chunk observer raised; detaching", memberNameOrQuestion());
                removeChunkObserver(observer);
            }
        }
    }

    private StreamFailure detectTaskFailed(Object chunk) {
        if (!(chunk instanceof OutputSchema output) || !(output.getPayload() instanceof Map<?, ?> payload)) {
            return null;
        }
        if (!TASK_FAILED_PAYLOAD_TYPE.equals(payload.get("type"))) {
            return null;
        }
        String text = "";
        Object data = payload.get("data");
        if (data instanceof List<?> items && !items.isEmpty()) {
            Object first = items.get(0);
            if (first instanceof Map<?, ?> textMap && textMap.get("text") != null) {
                text = String.valueOf(textMap.get("text"));
            }
        }
        Integer code = null;
        Matcher matcher = ERROR_CODE_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                code = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                code = null;
            }
        }
        return new StreamFailure(code, text);
    }

    private CompletionStage<Void> updateStatus(MemberStatus status) {
        return statusUpdater.update(status);
    }

    private CompletionStage<Void> updateExecution(ExecutionStatus status) {
        return executionUpdater.update(status);
    }

    private void logAgentTaskException(Throwable error) {
        if (error == null || error instanceof java.util.concurrent.CancellationException) {
            return;
        }
        Throwable actual = error instanceof CompletionException ? error.getCause() : error;
        TEAM_LOGGER.error("[%s] _run_one_round task crashed silently: %s", memberNameOrQuestion(), actual.getMessage());
    }

    private String memberName() {
        TeamAgentBlueprint blueprint = blueprintGetter.get();
        return blueprint == null ? null : blueprint.getMemberName();
    }

    private String memberNameOrQuestion() {
        String memberName = memberName();
        return memberName == null ? "?" : memberName;
    }

    private TeamRole role() {
        TeamAgentBlueprint blueprint = blueprintGetter.get();
        return blueprint == null ? null : blueprint.getRole();
    }

    /**
     * Status update callback.
     *
     * <p>Mirrors Python's {@code status_updater} callback in
     * {@code openjiuwen/agent_teams/agent/stream_controller.py}.</p>
     */
    public interface StatusUpdater {
        CompletionStage<Void> update(MemberStatus status);
    }

    /**
     * Execution-status update callback.
     *
     * <p>Mirrors Python's {@code execution_updater} callback in
     * {@code openjiuwen/agent_teams/agent/stream_controller.py}.</p>
     */
    public interface ExecutionUpdater {
        CompletionStage<Void> update(ExecutionStatus status);
    }

    /**
     * Detected task-failed chunk payload.
     *
     * <p>Mirrors Python's {@code _detect_task_failed} result in
     * {@code openjiuwen/agent_teams/agent/stream_controller.py}.</p>
     */
    public record StreamFailure(Integer errorCode, String errorText) {
    }

    /**
     * Team-tagged output chunk.
     *
     * <p>Mirrors Python's {@code TeamOutputSchema} tagging in
     * {@code openjiuwen/agent_teams/agent/stream_controller.py}.</p>
     */
    public static class TeamOutputChunk extends OutputSchema {
        private String sourceMember;
        private TeamRole role;

        public TeamOutputChunk(String type, int index, Object payload, String sourceMember, TeamRole role) {
            super(type, index, payload);
            this.sourceMember = sourceMember;
            this.role = role;
        }

        public String getSourceMember() {
            return sourceMember;
        }

        public void setSourceMember(String sourceMember) {
            this.sourceMember = sourceMember;
        }

        public TeamRole getRole() {
            return role;
        }

        public void setRole(TeamRole role) {
            this.role = role;
        }
    }

    private final class QueueChunkQueue implements SpawnManager.ChunkQueue {
        @Override
        public CompletionStage<Void> put(Object chunk) {
            streamQueue.offer(chunk);
            return CompletableFuture.completedFuture(null);
        }
    }
}
