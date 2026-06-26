/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for the controller output schema model.
 *
 * <p>Mirrors Python's {@code ControllerOutputPayload}, {@code ControllerOutputChunk}, and
 * {@code ControllerOutput} in
 * {@code openjiuwen/core/controller/schema/controller_output.py}.</p>
 */
class ControllerOutputSchemaTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void payloadAndChunkDefaultsMirrorPythonModels() {
        ControllerOutputPayload payload = new ControllerOutputPayload(EventType.TASK_COMPLETION, null);

        assertThat(payload.getType()).isEqualTo("task_completion");
        assertThat(payload.getData()).isEmpty();
        assertThat(payload.getMetadata()).isNull();

        ControllerOutputChunk chunk = new ControllerOutputChunk();

        assertThat(chunk.getType()).isEqualTo("controller_output");
        assertThat(chunk.getIndex()).isZero();
        assertThat(chunk.getPayload()).isNull();
        assertThat(chunk.getControllerPayload()).isNull();
        assertThat(chunk.isLastChunk()).isFalse();
    }

    @Test
    void constructorsPreservePayloadAndControllerOutputTypeValues() {
        ControllerOutputPayload payload = new ControllerOutputPayload(
                ControllerOutputPayload.TASK_PROCESSING,
                List.of(new DataFrame.TextDataFrame("working")),
                Map.of("step", 1)
        );
        ControllerOutputChunk chunk = new ControllerOutputChunk(3, payload, true);
        ControllerOutput output = new ControllerOutput(EventType.TASK_COMPLETION, List.of(chunk));
        output.setInputEventId("input-1");

        assertThat(chunk.getPayload()).isSameAs(payload);
        assertThat(chunk.getControllerPayload()).isSameAs(payload);
        assertThat(output.getType()).isEqualTo("task_completion");
        assertThat(output.getDataAsChunks()).containsExactly(chunk);
        assertThat(output.getInputEventId()).isEqualTo("input-1");

        ControllerOutputPayload allProcessed = ControllerOutputPayload.allTasksProcessed("done");
        assertThat(allProcessed.getType()).isEqualTo("all_tasks_processed");
        assertThat(allProcessed.getData()).containsExactly(new DataFrame.TextDataFrame("done"));
    }

    @Test
    void jacksonSerializationUsesPythonFieldNames() throws Exception {
        ControllerOutputPayload payload = new ControllerOutputPayload(
                EventType.TASK_INTERACTION.getValue(),
                List.of(new DataFrame.TextDataFrame("need input")),
                Map.of("field", "answer")
        );
        ControllerOutputChunk chunk = new ControllerOutputChunk(7, payload, true);
        ControllerOutput output = new ControllerOutput(EventType.TASK_INTERACTION, List.of(chunk));
        output.setInputEventId("evt-7");

        @SuppressWarnings("unchecked")
        Map<String, Object> dumped = mapper.readValue(mapper.writeValueAsString(output), Map.class);

        assertThat(dumped).containsEntry("type", "task_interaction")
                .containsEntry("input_event_id", "evt-7")
                .containsKey("data");
        assertThat(dumped).doesNotContainKeys("inputEventId");

        List<?> data = (List<?>) dumped.get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> chunkMap = (Map<String, Object>) data.get(0);
        assertThat(chunkMap).containsEntry("type", "controller_output")
                .containsEntry("index", 7)
                .containsEntry("last_chunk", true)
                .containsKey("payload");
        assertThat(chunkMap).doesNotContainKeys("controllerPayload", "lastChunk");
    }

    @Test
    void jacksonDeserializationAcceptsPythonFieldNames() throws Exception {
        ControllerOutputChunk chunk = mapper.readValue(
                """
                {
                  "type": "controller_output",
                  "index": 2,
                  "payload": {
                    "type": "processing",
                    "data": [
                      {"type": "text", "text": "working"}
                    ],
                    "metadata": {"step": 1}
                  },
                  "last_chunk": false
                }
                """,
                ControllerOutputChunk.class
        );

        assertThat(chunk.getType()).isEqualTo("controller_output");
        assertThat(chunk.getIndex()).isEqualTo(2);
        assertThat(chunk.isLastChunk()).isFalse();
        assertThat(chunk.getPayload().getType()).isEqualTo("processing");
        assertThat(chunk.getPayload().getData()).containsExactly(new DataFrame.TextDataFrame("working"));
        assertThat(chunk.getPayload().getMetadata()).containsEntry("step", 1);

        ControllerOutput output = mapper.readValue(
                """
                {
                  "type": "processing",
                  "data": {"status": "queued"},
                  "input_event_id": "input-2"
                }
                """,
                ControllerOutput.class
        );

        assertThat(output.getType()).isEqualTo("processing");
        assertThat(output.getDataAsMap()).containsEntry("status", "queued");
        assertThat(output.getInputEventId()).isEqualTo("input-2");
    }

    @Test
    void invalidLiteralValuesAreRejectedLikePythonPydanticLiterals() {
        assertThatThrownBy(() -> new ControllerOutputPayload("not-supported", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported controller output payload type");
        assertThatThrownBy(() -> new ControllerOutput(
                ControllerOutputPayload.ALL_TASKS_PROCESSED,
                Map.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported controller output type");
    }
}
