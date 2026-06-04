/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.indexing.processor;

import com.openjiuwen.core.retrieval.indexing.processor.Processor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Processor abstract base class test cases.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/core/retrieval/indexing/processor/test_base.py}
 * using Java's interface-based contract.</p>
 */
class TestProcessor {

    @Test
    void testProcess() {
        ConcreteProcessor processor = new ConcreteProcessor();

        String result = processor.process("test", Map.of("key", "value"));

        assertEquals("processed_result", result);
    }

    @Test
    void testCannotInstantiateAbstractContractDirectly() {
        assertTrue(Processor.class.isInterface() || Modifier.isAbstract(Processor.class.getModifiers()));
    }

    private static final class ConcreteProcessor implements Processor<String, String> {
        @Override
        public String process(String input, Map<String, Object> options) {
            return "processed_result";
        }
    }
}
