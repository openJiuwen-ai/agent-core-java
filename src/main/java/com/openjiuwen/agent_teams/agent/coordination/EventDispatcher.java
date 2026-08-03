/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent.coordination;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamInfra;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.TeamAgentBlueprint;
import com.openjiuwen.agent_teams.schema.TeamEvent;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Routes coordination events to scenario-scoped handler callbacks.
 *
 * <p>Mirrors Python's {@code EventDispatcher},
 * {@code AgentRoundController}, {@code TeamLifecycleController},
 * {@code PollController}, and {@code DispatcherHost} in
 * {@code openjiuwen/agent_teams/agent/coordination/dispatcher.py}.</p>
 *
 * <p>The concrete handler modules are translated by sibling tasks. The handler
 * facades here intentionally model only the dispatcher-owned contract:
 * callback registration, stable fan-out order, and trigger visibility for
 * focused tests.</p>
 */
public class EventDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventDispatcher.class);

    private final AgentRoundController round;
    private final TeamAgentBlueprint blueprint;
    private final TeamInfra infra;
    private final PollController pollController;
    private final CallbackFramework framework;
    private final List<String> callbackTrace = new ArrayList<>();

    private final AgentLifecycleHandler lifecycle;
    private final MemberHandler member;
    private final MessageHandler message;
    private final TaskBoardHandler taskBoard;
    private final StaleTaskHandler staleTask;
    private final TeamCompletionHandler teamCompletion;

    public EventDispatcher(
            DispatcherHost host,
            TeamAgentBlueprint blueprint,
            TeamInfra infra,
            PollController pollController
    ) {
        this.round = Objects.requireNonNull(host, "host");
        this.blueprint = Objects.requireNonNull(blueprint, "blueprint");
        this.infra = Objects.requireNonNull(infra, "infra");
        this.pollController = Objects.requireNonNull(pollController, "pollController");

        Map<String, Double> staleClaimThrottle = new LinkedHashMap<>();
        this.lifecycle = new AgentLifecycleHandler(host, blueprint, infra, pollController, callbackTrace);
        this.member = new MemberHandler(host, blueprint, infra, pollController, staleClaimThrottle, callbackTrace);
        this.message = new MessageHandler(host, blueprint, infra, pollController, callbackTrace);
        this.taskBoard = new TaskBoardHandler(host, blueprint, infra, pollController, callbackTrace);
        this.staleTask = new StaleTaskHandler(host, blueprint, infra, pollController, staleClaimThrottle, callbackTrace);
        this.teamCompletion = new TeamCompletionHandler(host, blueprint, infra, pollController, callbackTrace);

        this.framework = new CallbackFramework();
        for (BaseCoordinationHandler handler : List.of(
                lifecycle,
                member,
                message,
                taskBoard,
                staleTask,
                teamCompletion
        )) {
            for (Map.Entry<String, EventCallback> entry : handler.getCallbacks().entrySet()) {
                framework.registerSync(entry.getKey(), entry.getValue());
            }
        }
    }

    public AgentLifecycleHandler getLifecycle() {
        return lifecycle;
    }

    public MemberHandler getMember() {
        return member;
    }

    public MessageHandler getMessage() {
        return message;
    }

    public TaskBoardHandler getTaskBoard() {
        return taskBoard;
    }

    public StaleTaskHandler getStaleTask() {
        return staleTask;
    }

    public TeamCompletionHandler getTeamCompletion() {
        return teamCompletion;
    }

    public TeamInfra getInfra() {
        return infra;
    }

    public PollController getPollController() {
        return pollController;
    }

    public List<String> getCallbackTrace() {
        return Collections.unmodifiableList(callbackTrace);
    }

    public CompletionStage<Void> dispatch(InnerEventMessage event) {
        return dispatchCoordinationEvent(Objects.requireNonNull(event, "event"));
    }

    public CompletionStage<Void> dispatch(EventMessage event) {
        return dispatchCoordinationEvent(new TransportEvent(Objects.requireNonNull(event, "event")));
    }

    public CompletionStage<Void> dispatch(CoordinationEvent event) {
        return dispatchCoordinationEvent(Objects.requireNonNull(event, "event"));
    }

    private CompletionStage<Void> dispatchCoordinationEvent(CoordinationEvent event) {
        if (!round.isAgentReady()) {
            LOGGER.debug("agent not ready, skipping coordination wake");
            return CompletableFuture.completedFuture(null);
        }

        TeamRole role = blueprint.getRole();
        if (event instanceof InnerEventMessage innerEvent) {
            if (role == TeamRole.HUMAN_AGENT
                    && (innerEvent.getEventType() == InnerEventType.POLL_TASK
                    || innerEvent.getEventType() == InnerEventType.POLL_MAILBOX)) {
                return CompletableFuture.completedFuture(null);
            }
            LOGGER.debug("inner event received: type={}, payload={}", innerEvent.getEventType(), innerEvent.getPayload());
            return framework.trigger(innerEvent.eventKey(), innerEvent);
        }

        TransportEvent transportEvent = (TransportEvent) event;
        if (blueprint.getMemberName() == null || blueprint.getMemberName().isEmpty()) {
            LOGGER.debug("no member_name, skipping transport event");
            return CompletableFuture.completedFuture(null);
        }

        String eventType = transportEvent.getMessage().getEventType();
        if (role == TeamRole.HUMAN_AGENT && !isHumanTransportWhitelisted(eventType)) {
            return CompletableFuture.completedFuture(null);
        }

        return framework.trigger(eventType, transportEvent);
    }

    private static boolean isHumanTransportWhitelisted(String eventType) {
        return TeamEvent.CLEANED.equals(eventType)
                || TeamEvent.MEMBER_SHUTDOWN.equals(eventType)
                || TeamEvent.MEMBER_CANCELED.equals(eventType)
                || TeamEvent.STANDBY.equals(eventType)
                || TeamEvent.MESSAGE.equals(eventType)
                || TeamEvent.BROADCAST.equals(eventType)
                || TeamEvent.TASK_CLAIMED.equals(eventType);
    }

    /**
     * Round-level control surface against the owning agent.
     *
     * <p>Mirrors Python's {@code AgentRoundController} protocol in
     * {@code openjiuwen/agent_teams/agent/coordination/dispatcher.py}.</p>
     */
    public interface AgentRoundController {
        boolean isAgentReady();

        boolean isAgentRunning();

        boolean hasInFlightRound();

        boolean hasPendingInterrupt();

        CompletionStage<Void> cancelAgent();

        CompletionStage<Void> deliverInput(Object content, boolean useSteer);

        CompletionStage<Void> resumeInterrupt(Object userInput);
    }

    /**
     * TeamAgent-level lifecycle effects spanning managers.
     *
     * <p>Mirrors Python's {@code TeamLifecycleController} protocol in
     * {@code openjiuwen/agent_teams/agent/coordination/dispatcher.py}.</p>
     */
    public interface TeamLifecycleController {
        CompletionStage<Void> shutdownSelf();

        CompletionStage<Void> concludeCompletedRound(int memberCount, int taskCount);
    }

    /**
     * Periodic-poll control surface owned by the coordination event bus.
     *
     * <p>Mirrors Python's {@code PollController} protocol in
     * {@code openjiuwen/agent_teams/agent/coordination/dispatcher.py}.</p>
     */
    public interface PollController {
        CompletionStage<Void> pausePolls();

        CompletionStage<Void> resumePolls();
    }

    /**
     * Composite host contract consumed by the dispatcher and handlers.
     *
     * <p>Mirrors Python's {@code DispatcherHost} protocol in
     * {@code openjiuwen/agent_teams/agent/coordination/dispatcher.py}.</p>
     */
    public interface DispatcherHost extends AgentRoundController, TeamLifecycleController {
    }

    /**
     * Event types generated inside the coordination layer.
     *
     * <p>Mirrors Python's {@code InnerEventType} imported by
     * {@code openjiuwen/agent_teams/agent/coordination/dispatcher.py}.</p>
     */
    public enum InnerEventType {
        USER_INPUT("user_input"),
        POLL_MAILBOX("coordination_poll_mailbox"),
        POLL_TASK("coordination_poll_task"),
        SHUTDOWN("shutdown");

        private final String value;

        InnerEventType(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    /**
     * Internal event message isolated from cross-process transport events.
     *
     * <p>Mirrors Python's {@code InnerEventMessage} imported by
     * {@code openjiuwen/agent_teams/agent/coordination/dispatcher.py}.</p>
     */
    public static final class InnerEventMessage implements CoordinationEvent {
        private final InnerEventType eventType;
        private final Map<String, Object> payload;

        public InnerEventMessage(InnerEventType eventType) {
            this(eventType, Map.of());
        }

        public InnerEventMessage(InnerEventType eventType, Map<String, ?> payload) {
            this.eventType = Objects.requireNonNull(eventType, "eventType");
            this.payload = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(payload, "payload")));
        }

        public InnerEventType getEventType() {
            return eventType;
        }

        public Map<String, Object> getPayload() {
            return payload;
        }

        @Override
        public String eventKey() {
            return eventType.value();
        }
    }

    /**
     * Event union marker for dispatcher inputs.
     *
     * <p>Mirrors Python's {@code CoordinationEvent} type alias in
     * {@code openjiuwen/agent_teams/agent/coordination/dispatcher.py}.</p>
     */
    public interface CoordinationEvent {
        String eventKey();
    }

    /**
     * Adapter that lets Java keep transport {@link EventMessage} immutable while
     * sharing the same callback path as inner events.
     *
     * <p>Mirrors Python's transport side of {@code CoordinationEvent} in
     * {@code openjiuwen/agent_teams/agent/coordination/dispatcher.py}.</p>
     */
    public static final class TransportEvent implements CoordinationEvent {
        private final EventMessage message;

        public TransportEvent(EventMessage message) {
            this.message = Objects.requireNonNull(message, "message");
        }

        public EventMessage getMessage() {
            return message;
        }

        @Override
        public String eventKey() {
            return message.getEventType();
        }
    }

    /**
     * Dispatcher callback contract.
     *
     * <p>Mirrors Python's bound handler callbacks consumed by
     * {@code AsyncCallbackFramework} in
     * {@code openjiuwen/agent_teams/agent/coordination/dispatcher.py}.</p>
     */
    @FunctionalInterface
    public interface EventCallback {
        CompletionStage<Void> handle(CoordinationEvent event);
    }

    /**
     * Recorded callback invocation for focused dispatcher tests.
     *
     * <p>Mirrors Python's test-facing public handler attributes in
     * {@code openjiuwen/agent_teams/agent/coordination/dispatcher.py}.</p>
     */
    public record HandledEvent(String handlerName, String callbackName, String eventKey, CoordinationEvent event) {
    }

    /**
     * Minimal stable-order callback framework private to a dispatcher instance.
     *
     * <p>Mirrors Python's private {@code AsyncCallbackFramework} use in
     * {@code openjiuwen/agent_teams/agent/coordination/dispatcher.py}.</p>
     */
    private static final class CallbackFramework {
        private final Map<String, List<EventCallback>> callbacks = new LinkedHashMap<>();

        private void registerSync(String eventKey, EventCallback callback) {
            callbacks.computeIfAbsent(eventKey, ignored -> new ArrayList<>()).add(callback);
        }

        private CompletionStage<Void> trigger(String eventKey, CoordinationEvent event) {
            List<EventCallback> eventCallbacks = callbacks.getOrDefault(eventKey, List.of());
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            for (EventCallback callback : eventCallbacks) {
                chain = chain.thenCompose(ignored -> safeInvoke(callback, event));
            }
            return chain;
        }

        private CompletableFuture<Void> safeInvoke(EventCallback callback, CoordinationEvent event) {
            try {
                CompletionStage<Void> stage = callback.handle(event);
                if (stage == null) {
                    return CompletableFuture.completedFuture(null);
                }
                return stage.handle((ignored, exception) -> {
                    if (exception != null) {
                        LOGGER.debug("coordination callback failed and was swallowed", exception);
                    }
                    return ignored;
                }).toCompletableFuture();
            } catch (RuntimeException exception) {
                LOGGER.debug("coordination callback failed and was swallowed", exception);
                return CompletableFuture.completedFuture(null);
            }
        }
    }

    /**
     * Base facade for dispatcher-owned handler callback registration.
     *
     * <p>Mirrors Python's handler {@code get_callbacks()} contract as consumed
     * by {@code openjiuwen/agent_teams/agent/coordination/dispatcher.py}.</p>
     */
    public abstract static class BaseCoordinationHandler {
        protected final DispatcherHost host;
        protected final TeamAgentBlueprint blueprint;
        protected final TeamInfra infra;
        protected final PollController pollController;
        private final String handlerName;
        private final List<String> callbackTrace;
        private final List<HandledEvent> handledEvents = new ArrayList<>();

        protected BaseCoordinationHandler(
                String handlerName,
                DispatcherHost host,
                TeamAgentBlueprint blueprint,
                TeamInfra infra,
                PollController pollController,
                List<String> callbackTrace
        ) {
            this.handlerName = handlerName;
            this.host = host;
            this.blueprint = blueprint;
            this.infra = infra;
            this.pollController = pollController;
            this.callbackTrace = callbackTrace;
        }

        public abstract Map<String, EventCallback> getCallbacks();

        public List<HandledEvent> getHandledEvents() {
            return Collections.unmodifiableList(handledEvents);
        }

        protected CompletionStage<Void> record(String callbackName, CoordinationEvent event) {
            callbackTrace.add(handlerName + "." + callbackName);
            handledEvents.add(new HandledEvent(handlerName, callbackName, event.eventKey(), event));
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Lifecycle handler facade used only for dispatcher callback fan-out.
     *
     * <p>Mirrors Python's {@code AgentLifecycleHandler.get_callbacks()} as
     * consumed by
     * {@code openjiuwen/agent_teams/agent/coordination/dispatcher.py}.</p>
     */
    public static final class AgentLifecycleHandler extends BaseCoordinationHandler {
        private AgentLifecycleHandler(
                DispatcherHost host,
                TeamAgentBlueprint blueprint,
                TeamInfra infra,
                PollController pollController,
                List<String> callbackTrace
        ) {
            super("AgentLifecycleHandler", host, blueprint, infra, pollController, callbackTrace);
        }

        @Override
        public Map<String, EventCallback> getCallbacks() {
            Map<String, EventCallback> callbacks = new LinkedHashMap<>();
            callbacks.put(InnerEventType.USER_INPUT.value(), event -> record("on_user_input", event));
            callbacks.put(TeamEvent.STANDBY, event -> record("on_standby", event));
            callbacks.put(TeamEvent.CLEANED, event -> record("on_cleaned", event));
            callbacks.put(TeamEvent.TOOL_APPROVAL_RESULT, event -> record("on_tool_approval_result", event));
            callbacks.put(TeamEvent.TASK_PLAN_RESPONSE, event -> record("on_task_plan_response", event));
            return callbacks;
        }
    }

    /**
     * Member handler facade used only for dispatcher callback fan-out.
     *
     * <p>Mirrors Python's {@code MemberHandler.get_callbacks()} as consumed by
     * {@code openjiuwen/agent_teams/agent/coordination/dispatcher.py}.</p>
     */
    public static final class MemberHandler extends BaseCoordinationHandler {
        private final Map<String, Double> staleClaimThrottle;

        private MemberHandler(
                DispatcherHost host,
                TeamAgentBlueprint blueprint,
                TeamInfra infra,
                PollController pollController,
                Map<String, Double> staleClaimThrottle,
                List<String> callbackTrace
        ) {
            super("MemberHandler", host, blueprint, infra, pollController, callbackTrace);
            this.staleClaimThrottle = staleClaimThrottle;
        }

        public Map<String, Double> getStaleClaimThrottle() {
            return staleClaimThrottle;
        }

        @Override
        public Map<String, EventCallback> getCallbacks() {
            Map<String, EventCallback> callbacks = new LinkedHashMap<>();
            callbacks.put(TeamEvent.MEMBER_SPAWNED, event -> record("on_member_event", event));
            callbacks.put(TeamEvent.MEMBER_RESTARTED, event -> record("on_member_event", event));
            callbacks.put(TeamEvent.MEMBER_STATUS_CHANGED, event -> record("on_member_event", event));
            callbacks.put(TeamEvent.MEMBER_EXECUTION_CHANGED, event -> record("on_member_event", event));
            callbacks.put(TeamEvent.MEMBER_SHUTDOWN, event -> record("on_member_event", event));
            callbacks.put(TeamEvent.MEMBER_CANCELED, event -> record("on_member_event", event));
            return callbacks;
        }
    }

    /**
     * Message handler facade used only for dispatcher callback fan-out.
     *
     * <p>Mirrors Python's {@code MessageHandler.get_callbacks()} as consumed by
     * {@code openjiuwen/agent_teams/agent/coordination/dispatcher.py}.</p>
     */
    public static final class MessageHandler extends BaseCoordinationHandler {
        private MessageHandler(
                DispatcherHost host,
                TeamAgentBlueprint blueprint,
                TeamInfra infra,
                PollController pollController,
                List<String> callbackTrace
        ) {
            super("MessageHandler", host, blueprint, infra, pollController, callbackTrace);
        }

        @Override
        public Map<String, EventCallback> getCallbacks() {
            Map<String, EventCallback> callbacks = new LinkedHashMap<>();
            callbacks.put(TeamEvent.MESSAGE, event -> record("on_message_or_broadcast", event));
            callbacks.put(TeamEvent.BROADCAST, event -> record("on_message_or_broadcast", event));
            callbacks.put(InnerEventType.POLL_MAILBOX.value(), event -> record("on_poll_mailbox", event));
            callbacks.put(TeamEvent.MEMBER_SHUTDOWN, event -> record("on_member_shutdown_drain", event));
            return callbacks;
        }
    }

    /**
     * Task-board handler facade used only for dispatcher callback fan-out.
     *
     * <p>Mirrors Python's {@code TaskBoardHandler.get_callbacks()} as consumed
     * by {@code openjiuwen/agent_teams/agent/coordination/dispatcher.py}.</p>
     */
    public static final class TaskBoardHandler extends BaseCoordinationHandler {
        private TaskBoardHandler(
                DispatcherHost host,
                TeamAgentBlueprint blueprint,
                TeamInfra infra,
                PollController pollController,
                List<String> callbackTrace
        ) {
            super("TaskBoardHandler", host, blueprint, infra, pollController, callbackTrace);
        }

        @Override
        public Map<String, EventCallback> getCallbacks() {
            Map<String, EventCallback> callbacks = new LinkedHashMap<>();
            callbacks.put(TeamEvent.TASK_CLAIMED, event -> record("on_task_claimed", event));
            callbacks.put(TeamEvent.TASK_CREATED, event -> record("on_task_board_event", event));
            callbacks.put(TeamEvent.TASK_PLAN_REQUEST, event -> record("on_task_board_event", event));
            callbacks.put(TeamEvent.TASK_PLAN_RESPONSE, event -> record("on_task_plan_decision", event));
            callbacks.put(TeamEvent.TASK_UPDATED, event -> record("on_task_board_event", event));
            callbacks.put(TeamEvent.TASK_COMPLETED, event -> record("on_task_board_event", event));
            callbacks.put(TeamEvent.TASK_CANCELLED, event -> record("on_task_board_event", event));
            callbacks.put(TeamEvent.TASK_UNBLOCKED, event -> record("on_task_board_event", event));
            return callbacks;
        }
    }

    /**
     * Stale-task handler facade used only for dispatcher callback fan-out.
     *
     * <p>Mirrors Python's {@code StaleTaskHandler.get_callbacks()} as consumed
     * by {@code openjiuwen/agent_teams/agent/coordination/dispatcher.py}.</p>
     */
    public static final class StaleTaskHandler extends BaseCoordinationHandler {
        private final Map<String, Double> staleClaimThrottle;

        private StaleTaskHandler(
                DispatcherHost host,
                TeamAgentBlueprint blueprint,
                TeamInfra infra,
                PollController pollController,
                Map<String, Double> staleClaimThrottle,
                List<String> callbackTrace
        ) {
            super("StaleTaskHandler", host, blueprint, infra, pollController, callbackTrace);
            this.staleClaimThrottle = staleClaimThrottle;
        }

        public Map<String, Double> getStaleClaimThrottle() {
            return staleClaimThrottle;
        }

        @Override
        public Map<String, EventCallback> getCallbacks() {
            Map<String, EventCallback> callbacks = new LinkedHashMap<>();
            callbacks.put(InnerEventType.POLL_TASK.value(), event -> record("on_poll_task", event));
            return callbacks;
        }
    }

    /**
     * Team-completion handler facade used only for dispatcher callback fan-out.
     *
     * <p>Mirrors Python's {@code TeamCompletionHandler.get_callbacks()} as
     * consumed by
     * {@code openjiuwen/agent_teams/agent/coordination/dispatcher.py}.</p>
     */
    public static final class TeamCompletionHandler extends BaseCoordinationHandler {
        private final List<com.openjiuwen.agent_teams.agent.TeamAgent.TeamCompletionCallback> completionCallbacks =
                new ArrayList<>();

        private TeamCompletionHandler(
                DispatcherHost host,
                TeamAgentBlueprint blueprint,
                TeamInfra infra,
                PollController pollController,
                List<String> callbackTrace
        ) {
            super("TeamCompletionHandler", host, blueprint, infra, pollController, callbackTrace);
        }

        @Override
        public Map<String, EventCallback> getCallbacks() {
            Map<String, EventCallback> callbacks = new LinkedHashMap<>();
            callbacks.put(InnerEventType.POLL_TASK.value(), event -> record("on_poll_task", event));
            callbacks.put(TeamEvent.TASK_LIST_DRAINED, event -> record("on_task_list_drained", event));
            callbacks.put(TeamEvent.TEAM_COMPLETED, event -> record("on_team_completed", event));
            return callbacks;
        }

        public void registerCompletionCallback(
                com.openjiuwen.agent_teams.agent.TeamAgent.TeamCompletionCallback callback) {
            completionCallbacks.add(Objects.requireNonNull(callback, "callback"));
        }

        public List<com.openjiuwen.agent_teams.agent.TeamAgent.TeamCompletionCallback> getCompletionCallbacks() {
            return List.copyOf(completionCallbacks);
        }
    }
}
