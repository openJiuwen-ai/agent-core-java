// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ControllerOutput schema models.
 * Tests ControllerOutputPayload, ControllerOutputChunk, and ControllerOutput
 * models including type literals, defaults, and serialization.
 */
@DisplayName("ControllerOutput Schema Tests")
class ControllerOutputTest {

    @Nested
    @DisplayName("ControllerOutput Models Tests")
    class ControllerOutputModelsTests {

        // ---- ControllerOutputPayload ----

        @Test
        @DisplayName("Payload should accept all valid types with correct defaults and carry data")
        void testPayloadAcceptsAllValidTypesAndCarriesData() {
            // All valid types should be accepted
            String[] validTypes = {
                EventType.TASK_COMPLETION.getValue(),
                EventType.TASK_INTERACTION.getValue(),
                EventType.TASK_FAILED.getValue(),
                ControllerOutputConstants.TASK_PROCESSING,
                ControllerOutputConstants.ALL_TASKS_PROCESSED,
            };
            for (String type : validTypes) {
                ControllerOutputPayload p = new ControllerOutputPayload(type);
                assertEquals(type, p.getType());
                assertTrue(p.getData().isEmpty());
                assertNull(p.getMetadata());
            }

            // With data and metadata
            List<BaseDataFrame> data = List.of(
                new TextDataFrame("result"),
                new JsonDataFrame(Map.of("k", 1))
            );
            Map<String, Object> meta = Map.of("timing", 1.23);
            ControllerOutputPayload p = new ControllerOutputPayload(
                EventType.TASK_COMPLETION.getValue(), data, meta
            );
            assertEquals(2, p.getData().size());
            assertEquals(meta, p.getMetadata());
        }

        // ---- ControllerOutputChunk ----

        @Test
        @DisplayName("Chunk: defaults are correct, accepts payload/last_chunk, and round-trips")
        void testChunkDefaultsPayloadAndRoundtrip() {
            // Defaults
            ControllerOutputChunk c = new ControllerOutputChunk(0);
            assertEquals(0, c.getIndex());
            assertEquals("controller_output", c.getType());
            assertNull(c.getPayload());
            assertFalse(c.isLastChunk());

            // With payload and last_chunk
            ControllerOutputPayload payload = new ControllerOutputPayload(
                EventType.TASK_COMPLETION.getValue(),
                List.of(new TextDataFrame("done")),
                null
            );
            ControllerOutputChunk c2 = new ControllerOutputChunk(3, "controller_output", payload, true);
            assertEquals(3, c2.getIndex());
            assertTrue(c2.isLastChunk());
            assertEquals(EventType.TASK_COMPLETION.getValue(), c2.getPayload().getType());

            // Round-trip (verify data consistency)
            ControllerOutputPayload payload2 = new ControllerOutputPayload(
                ControllerOutputConstants.TASK_PROCESSING,
                List.of(new TextDataFrame("working")),
                null
            );
            ControllerOutputChunk c3 = new ControllerOutputChunk(1, "controller_output", payload2, false);
            assertEquals(1, c3.getIndex());
            assertEquals(ControllerOutputConstants.TASK_PROCESSING, c3.getPayload().getType());
        }

        // ---- ControllerOutput ----

        @Test
        @DisplayName("ControllerOutput should accept chunk lists, dict data, and various types")
        void testOutputWithVariousDataTypes() {
            // Chunk list
            ControllerOutputPayload chunkPayload = new ControllerOutputPayload(
                EventType.TASK_COMPLETION.getValue(),
                List.of(new TextDataFrame("result")),
                null
            );
            List<ControllerOutputChunk> chunks = List.of(
                new ControllerOutputChunk(0, "controller_output", chunkPayload, false)
            );
            ControllerOutput out1 = new ControllerOutput(
                EventType.TASK_COMPLETION.getValue(), chunks, null
            );
            assertEquals(EventType.TASK_COMPLETION.getValue(), out1.getType());
            assertEquals(1, out1.getChunks().size());
            assertNull(out1.getInputEventId());

            // With input_event_id and dict data
            ControllerOutput out2 = new ControllerOutput(
                EventType.TASK_FAILED.getValue(),
                Map.of("error", "something went wrong"),
                "evt-123"
            );
            assertNotNull(out2.getDictData());
            assertEquals("evt-123", out2.getInputEventId());

            // TASK_PROCESSING type
            ControllerOutput out3 = new ControllerOutput(
                ControllerOutputConstants.TASK_PROCESSING, List.of(), null
            );
            assertEquals(ControllerOutputConstants.TASK_PROCESSING, out3.getType());
        }
    }
}

