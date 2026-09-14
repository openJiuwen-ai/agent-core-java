/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RouterTest {

    @Test
    void staticRouterDispatchesTriggerMessagesToEachTarget() {
        StaticRouter router = new StaticRouter(List.of("a", "b", "c"));

        List<Message> messages = router.dispatch("start");

        assertThat(messages).hasSize(3);
        assertThat(messages).allMatch(TriggerMessage.class::isInstance);
        assertThat(messages).extracting(Message::getSender).containsOnly("start");
        assertThat(messages).extracting(Message::getTarget).containsExactly("a", "b", "c");
    }

    @Test
    void conditionalRouterNormalizesSingleStringTarget() {
        ConditionalRouter router = new ConditionalRouter(() -> "done");

        List<Message> messages = router.dispatch("worker");

        assertThat(messages).singleElement().satisfies(message -> {
            assertThat(message).isInstanceOf(TriggerMessage.class);
            assertThat(message.getSender()).isEqualTo("worker");
            assertThat(message.getTarget()).isEqualTo("done");
        });
    }

    @Test
    void conditionalRouterSupportsSelectorsThatReceiveState() {
        ConditionalRouter router = new ConditionalRouter(state -> List.of("left", "right"));

        List<Message> messages = router.dispatch("source");

        assertThat(messages).hasSize(2);
        assertThat(messages).extracting(Message::getTarget).containsExactly("left", "right");
    }

    @Test
    void barrierRouterCreatesBarrierMessages() {
        BarrierRouter router = new BarrierRouter(List.of("collect"));

        List<Message> messages = router.dispatch("node-a");

        assertThat(messages).singleElement().satisfies(message -> {
            assertThat(message).isInstanceOf(BarrierMessage.class);
            assertThat(message.getSender()).isEqualTo("node-a");
            assertThat(message.getTarget()).isEqualTo("collect");
        });
    }
}
