/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent.coordination;

import com.openjiuwen.core.session.interaction.InteractiveInput;

/**
 * Round-level control surface exposed by the owning TeamAgent to coordination handlers.
 *
 * <p>Mirrors Python {@code AgentRoundController} protocol
 * ({@code agent/coordination/dispatcher.py:50-91}). Only the entry points that
 * handlers actually call are declared. {@code startAgent} / {@code followUp} /
 * {@code steer} are intentionally not part of the public surface — handlers go
 * through {@link #deliverInput(Object)} and let the host pick the right
 * primitive based on round state.
 *
 * <p>All methods are synchronous — Java's {@code EventBus} processes events on
 * a single thread, mirroring Python's asyncio single-loop semantics.
 *
 * @since 2026/7/9
 */
public interface AgentRoundController {
    /**
     * Return whether the agent has been fully initialized.
     *
     * @return true if the agent is ready for a new round
     */
    boolean isAgentReady();

    /**
     * Return whether the agent is in an active round.
     *
     * @return true if the agent is currently running a round
     */
    boolean isAgentRunning();

    /**
     * Return whether an agent round is scheduled and not yet finalized.
     *
     * @return true if a round is in flight
     */
    boolean hasInFlightRound();

    /**
     * Return whether an unresolved tool interrupt is pending.
     *
     * @return true if a tool interrupt is pending
     */
    boolean hasPendingInterrupt();

    /**
     * Cancel the running agent task.
     */
    void cancelAgent();

    /**
     * Guarantee that content reaches the agent regardless of state.
     *
     * <p>Equivalent to {@link #deliverInput(Object, boolean) deliverInput(content, true)}.
     *
     * @param content content to deliver
     */
    void deliverInput(Object content);

    /**
     * Deliver input to the agent with steer-vs-followUp control.
     *
     * @param content content to deliver
     * @param shouldUseSteer when {@code true}, use steer (interrupt current round);
     *                 when {@code false}, queue as follow-up input
     */
    void deliverInput(Object content, boolean shouldUseSteer);

    /**
     * Resume the agent after a tool interrupt with the given input.
     *
     * @param input structured interactive input carrying the tool-call decision
     */
    void resumeInterrupt(InteractiveInput input);
}
