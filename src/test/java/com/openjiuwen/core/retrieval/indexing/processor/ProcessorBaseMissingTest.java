/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's {@code TestProcessor} in
 * {@code tests/unit_tests/core/retrieval/indexing/processor/test_base.py}.</p>
 */
class ProcessorBaseMissingTest {

    @Test
    void testProcess() {
        Processor<String> processor = (Object... args) -> CompletableFuture.completedFuture("processed_result");

        String result = processor.process("test", "key=value").join();

        assertThat(result).isEqualTo("processed_result");
    }

    @Test
    void testCannotInstantiateAbstractClass() {
        assertThat(Processor.class.isInterface()).isTrue();
        assertThat(Modifier.isAbstract(Processor.class.getModifiers())).isTrue();
        assertThat(Processor.class.getDeclaredConstructors()).isEmpty();
    }
}
