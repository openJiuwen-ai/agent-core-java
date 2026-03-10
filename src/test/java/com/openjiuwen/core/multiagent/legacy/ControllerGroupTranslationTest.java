package com.openjiuwen.core.multiagent.legacy;

import com.openjiuwen.core.session.AgentGroupSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ControllerGroupTranslationTest {

    @Test
    void streamForwardsAgentChunksToCaller() {
        DefaultGroupController controller = new DefaultGroupController();
        ControllerGroup group = new ControllerGroup(new AgentGroupConfig("group"), controller);
        RecordingAgent agent = new RecordingAgent(
                "worker",
                List.of(
                        new OutputSchema("answer", 0, Map.of("output", "first", "result_type", "answer")),
                        new OutputSchema("answer", 1, Map.of("output", "second", "result_type", "answer"))
                )
        );
        group.addAgent("worker", agent);

        GroupEvent event = GroupEvent.createUserEvent("hello", "conversation-1");
        event.setReceiverId("worker");

        List<Object> chunks = new ArrayList<>();
        Iterator<Object> stream = group.stream(event, null);
        while (stream.hasNext()) {
            chunks.add(stream.next());
        }

        assertEquals(2, chunks.size());
        assertEquals("first", ((Map<?, ?>) ((OutputSchema) chunks.get(0)).getPayload()).get("output"));
        assertEquals("second", ((Map<?, ?>) ((OutputSchema) chunks.get(1)).getPayload()).get("output"));
    }

    @Test
    void sendToAgentPreservesInteractiveInputPayload() {
        DefaultGroupController controller = new DefaultGroupController();
        ControllerGroup group = new ControllerGroup(new AgentGroupConfig("group"), controller);
        RecordingAgent agent = new RecordingAgent(
                "worker",
                List.of(new OutputSchema("answer", 0, Map.of("output", "ok", "result_type", "answer")))
        );
        group.addAgent("worker", agent);

        InteractiveInput interactiveInput = new InteractiveInput();
        interactiveInput.update("questioner", "resume");
        GroupEvent event = GroupEvent.fromMap(Map.of(
                "query", interactiveInput,
                "conversation_id", "conversation-2",
                "receiver_id", "worker"
        ));

        controller.sendToAgent(event, "worker", new AgentGroupSessionApi("conversation-2"));

        @SuppressWarnings("unchecked")
        Map<String, Object> lastInputs = (Map<String, Object>) agent.lastInputs;
        assertSame(interactiveInput, lastInputs.get("query"));
    }

    private static final class RecordingAgent extends BaseAgent {

        private final List<Object> outputs;
        private Object lastInputs;

        private RecordingAgent(String id, List<Object> outputs) {
            super(AgentCard.builder().id(id).name(id).description(id).build());
            this.outputs = outputs;
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }

        @Override
        public Object getConfig() {
            return null;
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            lastInputs = inputs;
            return outputs.isEmpty() ? null : outputs.get(outputs.size() - 1);
        }

        @Override
        public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
            lastInputs = inputs;
            return outputs.iterator();
        }
    }
}
