/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent.coordination;

import com.openjiuwen.agentteams.agent.coordination.handlers.AgentLifecycleHandler;
import com.openjiuwen.agentteams.agent.coordination.handlers.BaseCoordinationHandler;
import com.openjiuwen.agentteams.agent.coordination.handlers.MemberHandler;
import com.openjiuwen.agentteams.agent.coordination.handlers.MessageHandler;
import com.openjiuwen.agentteams.agent.coordination.handlers.StaleTaskHandler;
import com.openjiuwen.agentteams.agent.coordination.handlers.TaskBoardHandler;
import com.openjiuwen.agentteams.agent.coordination.handlers.TeamCompletionHandler;
import com.openjiuwen.agentteams.agent.coordination.handlers.WorkflowHandler;
import com.openjiuwen.agentteams.schema.events.CoordinationEvent;
import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.schema.events.TeamEvent;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coordination lifecycle and transport wiring for TeamAgent.
 *
 * <p>Mirrors Python {@code agent/coordination/kernel.py:CoordinationKernel}.
 * Owns the coordination subsystem: {@link EventBus}, {@link AsyncCallbackFramework},
 * and the seven scenario-scoped handlers. Provides {@link #dispatch(CoordinationEvent)}
 * as the wake callback bound to the EventBus at {@link #start()} time, applying
 * coarse dispatch filters (agent readiness, role-based event gating) before
 * fanning out to registered handler callbacks.
 *
 * <h2>Iron rules</h2>
 * <ul>
 *   <li><b>Agent-ready gate</b>: dispatch is skipped when the agent is not ready.</li>
 *   <li><b>HUMAN_AGENT poll muting</b>: inner POLL_MAILBOX / POLL_TASK events are
 *       dropped for human agents — their LLM is driven by USER_INPUT from the
 *       controller, not by autonomous polling.</li>
 *   <li><b>HUMAN_AGENT transport whitelist</b>: only a curated set of transport
 *       events reach the human-agent avatar; everything else is muted to prevent
 *       autonomous task-board scanning.</li>
 * </ul>
 *
 * @since 2026/7/9
 */
public class CoordinationKernel {
    private final EventBus eventBus;
    private final AsyncCallbackFramework framework;
    private final TeamAgentBlueprint blueprint;
    private final TeamInfra infra;
    private final List<BaseCoordinationHandler> handlers;

    private final AgentRoundController round;

    /**
     * Construct the coordination kernel.
     *
     * <p>Builds the EventBus, instantiates all seven handlers in registration
     * order (lifecycle → member → message → task_board → stale_task →
     * team_completion → workflow), registers their callbacks into the framework,
     * and binds the kernel's dispatch method as the EventBus wake callback.
     *
     * @param host the owning TeamAgent as DispatcherHost
     * @param blueprint immutable blueprint-level config
     * @param infra per-process infrastructure services
     */
    public CoordinationKernel(DispatcherHost host, TeamAgentBlueprint blueprint,
                              TeamInfra infra) {
        this.blueprint = blueprint;
        this.infra = infra;
        this.round = host;

        TeamRole role = blueprint.role().orElse(null);
        this.eventBus = new EventBus(role != null ? role : TeamRole.MEMBER);
        this.framework = new AsyncCallbackFramework();

        // Shared stale-claim throttle between MemberHandler (status-change path)
        // and StaleTaskHandler (poll path) so the same task cannot be nudged
        // twice within one stale window regardless of trigger source.
        Map<String, Long> staleClaimThrottle = new ConcurrentHashMap<>();

        // Handler registration order matters for fan-out on shared event keys.
        // E.g. MEMBER_SHUTDOWN: MemberHandler processes lifecycle state, then
        // MessageHandler flushes the mailbox. Same priority → registration order
        // is preserved. team_completion is registered last so its POLL_TASK
        // callback fans out after StaleTaskHandler's.
        this.handlers = List.of(
                new AgentLifecycleHandler(host, blueprint, infra, eventBus),
                new MemberHandler(host, blueprint, infra, eventBus, staleClaimThrottle),
                new MessageHandler(host, blueprint, infra, eventBus),
                new TaskBoardHandler(host, blueprint, infra, eventBus),
                new StaleTaskHandler(host, blueprint, infra, eventBus, staleClaimThrottle),
                new TeamCompletionHandler(host, blueprint, infra, eventBus),
                new WorkflowHandler(host, blueprint, infra, eventBus)
        );

        for (BaseCoordinationHandler handler : handlers) {
            for (var entry : handler.getCallbacks().entrySet()) {
                framework.registerSync(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Wake-up entry from EventBus. Applies coarse dispatch rules, then
     * triggers the callback framework.
     *
     * <p>Mirrors Python {@code EventDispatcher.dispatch()}.
     *
     * @param event the coordination event to dispatch (inner or transport)
     */
    public void dispatch(CoordinationEvent event) {
        if (event == null) {
            return;
        }
        if (!round.isAgentReady()) {
            Loggers.AGENT.debug("agent not ready, skipping coordination wake");
            return;
        }

        TeamRole role = blueprint.role().orElse(null);

        if (event instanceof InnerEventMessage innerEvent) {
            // HUMAN_AGENT: mute autonomous polls, but USER_INPUT is legitimate
            // (avatar controller driving its LLM).
            if (role == TeamRole.HUMAN_AGENT
                    && (innerEvent.getEventType() == InnerEventType.POLL_TASK
                    || innerEvent.getEventType() == InnerEventType.POLL_MAILBOX)) {
                return;
            }
            Loggers.AGENT.debug("inner event received: type={}, payload={}",
                    innerEvent.getEventType(), innerEvent.getPayload());
            framework.trigger(innerEvent.eventKey(), event);
            return;
        }

        // --- Transport events (cross-process EventMessage) ---
        if (blueprint.memberName().isEmpty() || blueprint.memberName().get().isBlank()) {
            Loggers.AGENT.debug("no member_name, skipping transport event");
            return;
        }

        // HUMAN_AGENT transport whitelist: only lifecycle and team-notification
        // events reach the avatar. Everything else is muted to prevent
        // autonomous task-board scanning.
        if (role == TeamRole.HUMAN_AGENT && event instanceof EventMessage transportEvent) {
            String type = transportEvent.getEventType();
            if (!isHumanAgentAllowedTransportEvent(type)) {
                return;
            }
        }

        if (event instanceof EventMessage transportEvent) {
            framework.trigger(transportEvent.eventKey(), event);
        }
    }

    /**
     * Start the event loop with the kernel's dispatch as the wake callback.
     */
    public void start() {
        eventBus.start(this::dispatch);
    }

    /**
     * Stop the event loop.
     */
    public void stop() {
        eventBus.stop();
    }

    /**
     * Push an event into the processing queue.
     *
     * @param event the coordination event (inner or transport)
     */
    public void enqueue(CoordinationEvent event) {
        eventBus.enqueue(event);
    }

    /**
     * Convenience: enqueue a USER_INPUT inner event.
     *
     * @param content the user input content
     */
    public void enqueueUserInput(String content) {
        eventBus.enqueue(InnerEventMessage.builder()
                .eventType(InnerEventType.USER_INPUT)
                .payload(Map.of("content", content != null ? content : ""))
                .build());
    }

    /**
     * Convenience: enqueue a POLL_MAILBOX inner event.
     */
    public void enqueuePollMailbox() {
        eventBus.enqueue(InnerEventMessage.builder()
                .eventType(InnerEventType.POLL_MAILBOX)
                .build());
    }

    /**
     * Whether the event loop is running.
     *
     * @return true if the event bus is currently running, false otherwise
     */
    public boolean isRunning() {
        return eventBus.isRunning();
    }

    /**
     * Pause periodic polling on the event bus.
     */
    public void pausePolls() {
        eventBus.pausePolls();
    }

    /**
     * Resume periodic polling on the event bus.
     */
    public void resumePolls() {
        eventBus.resumePolls();
    }

    /**
     * Return the event bus instance.
     *
     * @return the EventBus used by this kernel
     */
    public EventBus getEventBus() {
        return eventBus;
    }

    /**
     * Rearm the team-completion rising-edge guard so the next completion
     * re-emits. Called on every start (cold start / resume / recover) so
     * each run cycle evaluates team completion independently.
     */
    public void rearmCompletion() {
        for (BaseCoordinationHandler h : handlers) {
            if (h instanceof TeamCompletionHandler tch) {
                tch.rearm();
            }
        }
    }

    private static boolean isHumanAgentAllowedTransportEvent(String type) {
        return TeamEvent.CLEANED.equals(type)
                || TeamEvent.MEMBER_SHUTDOWN.equals(type)
                || TeamEvent.MEMBER_CANCELED.equals(type)
                || TeamEvent.STANDBY.equals(type)
                || TeamEvent.MESSAGE.equals(type)
                || TeamEvent.BROADCAST.equals(type)
                || TeamEvent.TASK_CLAIMED.equals(type);
    }
}
