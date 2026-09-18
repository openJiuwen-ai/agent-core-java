package com.openjiuwen.core.common.logging.defaults;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultLogConstantsTest {

    @Test
    void defaultInnerLogConfigMatchesPythonConstants() {
        Map<String, Object> config = DefaultLogConstants.defaultInnerLogConfig();

        assertEquals("default", config.get("backend"));
        assertEquals("INFO", config.get("level"));
        assertEquals("json", config.get("structured_output_format"));
        assertEquals(20, config.get("backup_count"));
        assertEquals(20_971_520, config.get("max_bytes"));
        assertEquals(
                "%(asctime)s | %(log_type)s | %(trace_id)s | %(levelname)s | %(message)s",
                config.get("format"));
        assertEquals("./logs/", config.get("log_path"));
        assertEquals("run/jiuwen.log", config.get("log_file"));
        assertEquals("interface/jiuwen_interface.log", config.get("interface_log_file"));
        assertEquals("interface/jiuwen_prompt_builder_interface.log",
                config.get("prompt_builder_interface_log_file"));
        assertEquals("performance/jiuwen_performance.log", config.get("performance_log_file"));
        assertEquals(true, config.get("propagate"));
        assertEquals(List.of("console", "file"), config.get("output"));
        assertEquals(List.of("console", "file"), config.get("interface_output"));
        assertEquals(List.of("console", "file"), config.get("performance_output"));
        assertEquals(Map.of(), config.get("loggers"));
    }

    @Test
    void defaultLogConfigWrapsInnerLoggingKey() {
        Map<String, Object> config = DefaultLogConstants.defaultLogConfig();

        assertTrue(config.containsKey("logging"));
        assertInstanceOf(Map.class, config.get("logging"));
    }
}
