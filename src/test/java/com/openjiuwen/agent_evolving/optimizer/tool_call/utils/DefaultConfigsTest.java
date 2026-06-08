package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class DefaultConfigsTest {

    @Test
    void defaultConfigEgMatchesPythonDefaults() {
        Map<String, Object> config = DefaultConfigs.defaultConfigEg();

        assertEquals("gpt-5-mini", config.get("gen_model_id"));
        assertEquals("gpt-5-mini", config.get("eval_model_id"));
        assertEquals(1, ((Number) config.get("verbose")).intValue());
        assertEquals(3, ((Number) config.get("expand_num")).intValue());
        assertEquals(5, ((Number) config.get("top_k")).intValue());
    }

    @Test
    void defaultConfigDescMatchesPythonDefaultsAndReturnsFreshCopy() {
        Map<String, Object> first = DefaultConfigs.defaultConfigDesc();
        Map<String, Object> second = DefaultConfigs.defaultConfigDesc();

        assertEquals(4, ((Number) first.get("num_examples_for_desc")).intValue());
        assertEquals(2, ((Number) first.get("beam_width")).intValue());
        assertEquals(3, ((Number) first.get("top_k")).intValue());
        assertNotSame(first, second);
    }
}
