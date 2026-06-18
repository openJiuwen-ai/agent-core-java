/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.interaction.HumanAgentInboundEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

/**
 * Focused tests for CLI inbox rendering.
 *
 * <p>Mirrors Python's {@code make_inbox_callback} in
 * {@code openjiuwen/agent_teams/cli/inbox_sink.py}.</p>
 */
class InboxSinkTest {

    @Test
    void callbackPrintsDirectMessageAndReplyHint() {
        List<String> lines = new ArrayList<>();
        Function<HumanAgentInboundEvent, CompletionStage<Void>> callback = InboxSink.makeInboxCallback(lines::add);
        HumanAgentInboundEvent event = new HumanAgentInboundEvent(
                "human",
                "leader",
                "  hello\nteam  ",
                false,
                "msg-1",
                123L
        );

        CompletionStage<Void> stage = callback.apply(event);

        assertThat(stage.toCompletableFuture()).isDone();
        assertThat(lines).containsExactly(
                "[bold yellow][inbox/human][/bold yellow] [dim]direct from <leader>[/dim] hello team",
                "  [dim italic]reply with: $human @leader <body> or @leader <body>[/dim italic]"
        );
    }

    @Test
    void callbackMarksBroadcastMessages() {
        List<String> lines = new ArrayList<>();
        Function<HumanAgentInboundEvent, CompletionStage<Void>> callback = InboxSink.makeInboxCallback(lines::add);

        callback.apply(new HumanAgentInboundEvent(
                "human",
                "leader",
                "broadcast",
                true,
                "msg-2",
                456L
        )).toCompletableFuture().join();

        assertThat(lines.getFirst())
                .isEqualTo("[bold yellow][inbox/human][/bold yellow] [dim]broadcast from <leader>[/dim] broadcast");
    }
}
