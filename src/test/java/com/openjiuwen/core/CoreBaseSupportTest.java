package com.openjiuwen.core;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.constants.ControllerType;
import com.openjiuwen.core.common.constants.TaskType;
import com.openjiuwen.core.foundation.prompt.assemble.variables.Variable;
import com.openjiuwen.core.graph.pregel.BarrierMessage;
import com.openjiuwen.core.graph.pregel.Channel;
import com.openjiuwen.core.graph.pregel.GraphInterrupt;
import com.openjiuwen.core.graph.pregel.Interrupt;
import com.openjiuwen.core.graph.pregel.Message;
import com.openjiuwen.core.graph.pregel.PregelConstants;
import com.openjiuwen.core.graph.pregel.PregelNode;
import com.openjiuwen.core.graph.pregel.TriggerMessage;
import com.openjiuwen.core.graph.store.Serializer;
import com.openjiuwen.core.runner.callback.AgentEvents;
import com.openjiuwen.core.runner.callback.AgentTeamEvents;
import com.openjiuwen.core.runner.callback.ChainAction;
import com.openjiuwen.core.runner.callback.ContextEvents;
import com.openjiuwen.core.runner.callback.Events;
import com.openjiuwen.core.runner.callback.FilterAction;
import com.openjiuwen.core.runner.callback.HookType;
import com.openjiuwen.core.runner.callback.MemoryEvents;
import com.openjiuwen.core.runner.callback.SessionEvents;
import com.openjiuwen.core.runner.callback.TaskManagerEvents;
import com.openjiuwen.core.runner.callback.ToolCallEvents;
import com.openjiuwen.core.runner.callback.WorkflowEvents;
import com.openjiuwen.core.session.constants.SessionConstants;
import com.openjiuwen.core.session.stream.CustomSchema;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.session.stream.TraceSchema;
import com.openjiuwen.core.workflow.component.ComponentAbility;
import com.openjiuwen.core.workflow.component.ComponentConfig;
import com.openjiuwen.core.workflow.component.ComponentState;
import com.openjiuwen.core.workflow.component.WorkflowComponentMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CoreBaseSupportTest {

    @Test
    void pregelBaseTypesAndSerializerMatchPythonContracts() throws Exception {
        Interrupt interrupt = new Interrupt("stop");
        GraphInterrupt graphInterrupt = new GraphInterrupt(List.of(interrupt));
        PregelNode node = new PregelNode("n1", input -> input, List.of(source -> List.of(new TriggerMessage(source, "n2"))));
        TestChannel channel = new TestChannel("chan");

        channel.accept(new Message("a", "b", 7));

        assertThat(interrupt.getValue()).isEqualTo("stop");
        assertThat(graphInterrupt.getValue()).isEqualTo(List.of(interrupt));
        assertThat(node.getName()).isEqualTo("n1");
        assertThat(node.getRouters()).hasSize(1);
        assertThat(channel.isReady()).isTrue();
        assertThat(channel.consume()).isEqualTo(7);
        assertThat(new BarrierMessage("x", "y").getTarget()).isEqualTo("y");

        Serializer serializer = Serializer.createSerializer("java");
        Serializer.TypedBytes dumped = serializer.dumpsTyped("demo");
        assertThat(dumped.type()).isEqualTo("java");
        assertThat(serializer.loadsTyped(dumped)).isEqualTo("demo");
        assertThat(Serializer.createSerializer("json")).isInstanceOf(Serializer.JsonSerializer.class);
    }

    @Test
    void variableWorkflowStreamEventsAndConstantsRemainStable() {
        TestVariable variable = new TestVariable("v", List.of("x"));
        Object value = variable.eval(Map.of("x", "ok", "y", "skip"));
        WorkflowComponentMetadata metadata = new WorkflowComponentMetadata("id", "type", "name");
        ComponentConfig config = new ComponentConfig(metadata);
        ComponentState state = new ComponentState("c1", HookType.BEFORE);
        OutputSchema outputSchema = new OutputSchema("output", 1, "payload");
        TraceSchema traceSchema = new TraceSchema("trace", Map.of("k", "v"));
        CustomSchema customSchema = new CustomSchema();
        customSchema.put("x", 1);

        assertThat(value).isEqualTo("ok");
        assertThat(variable.publicPrepareInputs(Map.of("x", 1, "z", 2))).containsOnlyKeys("x");
        assertThat(ComponentAbility.TRANSFORM.getAbilityName()).isEqualTo("transform");
        assertThat(config.getMetadata()).isEqualTo(metadata);
        assertThat(state.getStatus()).isEqualTo(HookType.BEFORE);
        assertThat(StreamMode.CUSTOM.toString()).contains("custom");
        assertThat(outputSchema.getIndex()).isEqualTo(1);
        assertThat(traceSchema.getPayload()).isEqualTo(Map.of("k", "v"));
        assertThat(customSchema.getProperties()).containsEntry("x", 1);
        assertThat(FilterAction.MODIFY.getValue()).isEqualTo("modify");
        assertThat(ChainAction.ROLLBACK.getValue()).isEqualTo("rollback");
        assertThat(AgentEvents.AGENT_STARTED).isEqualTo("_framework:agent_started");
        assertThat(AgentTeamEvents.AGENT_P2P_RECEIVED).isEqualTo("_framework:agent_p2p_received");
        assertThat(WorkflowEvents.COMPONENT_STREAM_OUTPUT).isEqualTo("_framework:workflow_component_stream_output");
        assertThat(ToolCallEvents.TOOL_AUTH).isEqualTo("_framework:tool_auth");
        assertThat(ContextEvents.CONTEXT_COMPRESSION_STATE).isEqualTo("_framework:context.compression_state");
        assertThat(SessionEvents.AGENT_SESSION_CREATED).isEqualTo("_framework:agent_session_created");
        assertThat(MemoryEvents.MEMORY_SEARCH_FINISHED).isEqualTo("_framework:memory_search_finished");
        assertThat(TaskManagerEvents.TASK_TIMEOUT).isEqualTo("_framework:task_timeout");
        assertThat(Events.buildEventName("scope", "event")).isEqualTo("scope:event");
        assertThat(Events.parseEventName("scope:event")).containsExactly("scope", "event");
        assertThat(Constant.INTERACTIVE_INPUT).isEqualTo("__interactive_input__");
        assertThat(PregelConstants.SESSION_ID).isEqualTo("session_id");
        assertThat(SessionConstants.LOOP_NUMBER_MAX_LIMIT_DEFAULT).isEqualTo(1000);
        assertThat(ControllerType.fromValue("react")).isEqualTo(ControllerType.REACT_CONTROLLER);
        assertThat(TaskType.fromValue("mcp")).isEqualTo(TaskType.MCP);
    }

    private static final class TestVariable extends Variable {

        private TestVariable(String name, List<String> inputKeys) {
            super(name, inputKeys);
        }

        @Override
        public Object update(Map<String, Object> kwargs) {
            this.value = kwargs.get("x");
            return this.value;
        }
    }

    private static final class TestChannel extends Channel {

        private Object payload;

        private TestChannel(String name) {
            super(name);
        }

        @Override
        public boolean isReady() {
            return payload != null;
        }

        @Override
        public void accept(Message msg) {
            payload = msg.getPayload();
        }

        @Override
        public Object consume() {
            Object current = payload;
            payload = null;
            return current;
        }

        @Override
        public void restore(Object snapshot) {
            payload = snapshot;
        }
    }
}
