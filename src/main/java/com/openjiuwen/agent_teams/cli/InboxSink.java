/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.cli;

import com.openjiuwen.agent_teams.interaction.HumanAgentInboundEvent;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Renders HumanAgentInboundEvent notifications into a CLI console.
 *
 * <p>Mirrors Python's {@code make_inbox_callback} in
 * {@code openjiuwen/agent_teams/cli/inbox_sink.py}.</p>
 */
public final class InboxSink {

    public static final List<String> EXPORTED_SYMBOLS = List.of("make_inbox_callback");

    private InboxSink() {
    }

    /**
     * Builds an async inbound callback that prints one inbox notification to {@code console}.
     *
     * @param console print sink equivalent to Python rich {@code Console.print}
     * @return callback for a human-agent inbound event
     */
    public static Function<HumanAgentInboundEvent, CompletionStage<Void>> makeInboxCallback(ConsoleSink console) {
        Objects.requireNonNull(console, "console");
        return event -> {
            Objects.requireNonNull(event, "event");
            String kind = event.broadcast() ? "broadcast" : "direct";
            String body = Objects.requireNonNull(event.body(), "event.body")
                    .replace("\n", " ")
                    .strip();
            console.print("[bold yellow][inbox/" + event.memberName() + "][/bold yellow] [dim]"
                    + kind + " from <" + event.sender() + ">[/dim] " + body);
            console.print("  [dim italic]reply with: $" + event.memberName()
                    + " @" + event.sender() + " <body> or @" + event.sender()
                    + " <body>[/dim italic]");
            return CompletableFuture.completedFuture(null);
        };
    }

    /**
     * Minimal console boundary for CLI inbox rendering.
     *
     * <p>Mirrors the {@code Console.print(...)} method used by
     * {@code openjiuwen/agent_teams/cli/inbox_sink.py}.</p>
     */
    @FunctionalInterface
    public interface ConsoleSink {
        void print(String text);
    }
}
