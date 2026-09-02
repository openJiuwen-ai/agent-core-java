
package com.openjiuwen.core.application.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

class ConstrainConfigValidationTest {
    private static final String GREATER_THAN_ZERO_MESSAGE =
        "Input should be greater than 0 [type=greater_than, input_value=0, input_type=int]";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void builderRejectsZeroReservedMaxChatRounds() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ConstrainConfig.builder().reservedMaxChatRounds(0).build());

        assertEquals(GREATER_THAN_ZERO_MESSAGE, exception.getMessage());
    }

    @Test
    void builderRejectsZeroMaxIteration() {
        IllegalArgumentException exception =
            assertThrows(IllegalArgumentException.class, () -> ConstrainConfig.builder().maxIteration(0).build());

        assertEquals(GREATER_THAN_ZERO_MESSAGE, exception.getMessage());
    }

    @Test
    void jsonSetterRejectsZeroReservedMaxChatRounds() {
        JsonMappingException exception = assertThrows(JsonMappingException.class, () -> objectMapper.readValue("""
                {"reserved_max_chat_rounds":0}
                """, ConstrainConfig.class));

        assertEquals(GREATER_THAN_ZERO_MESSAGE, exception.getCause().getMessage());
    }

    @Test
    void defaultsRemainPythonCompatible() {
        ConstrainConfig config = ConstrainConfig.builder().build();

        assertEquals(ConstrainConfig.DEFAULT_RESERVED_MAX_CHAT_ROUNDS, config.getReservedMaxChatRounds());
        assertEquals(ConstrainConfig.DEFAULT_MAX_ITERATION, config.getMaxIteration());
    }
}