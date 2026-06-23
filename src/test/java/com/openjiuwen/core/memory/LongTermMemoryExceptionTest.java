/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.store.BaseDbStore;
import com.openjiuwen.core.foundation.store.BaseKVStore;
import com.openjiuwen.core.foundation.store.BaseMemoryIndex;
import com.openjiuwen.core.foundation.store.BaseVectorStore;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.memory.config.MemoryEngineConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.core.memory.test_memory_exception} in
 * {@code tests/unit_tests/core/memory/test_memory_exception.py}.</p>
 */
class LongTermMemoryExceptionTest {

    @Test
    void registerStoreKvStoreNoneRaisesMemoryRegisterError() {
        LongTermMemory memory = new LongTermMemory();

        BaseError error = assertThrows(BaseError.class, () -> memory.registerStore(null, null, null, null));

        assertThat(error.getStatus()).isEqualTo(StatusCode.MEMORY_REGISTER_STORE_EXECUTION_ERROR);
        assertThat(error.getCode()).isEqualTo(StatusCode.MEMORY_REGISTER_STORE_EXECUTION_ERROR.getCode());
    }

    @Test
    void registerStoreWrongVectorStoreTypeIsRejectedAtJavaBoundary() throws ReflectiveOperationException {
        Method registerStore = LongTermMemory.class.getMethod(
                "registerStore",
                BaseKVStore.class,
                BaseVectorStore.class,
                BaseDbStore.class,
                com.openjiuwen.core.foundation.store.Embedding.class
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> registerStore.invoke(new LongTermMemory(), mock(BaseKVStore.class), new Object(), null, null)
        );
    }

    @Test
    void registerStoreWrongDbStoreTypeIsRejectedAtJavaBoundary() throws ReflectiveOperationException {
        Method registerStore = LongTermMemory.class.getMethod(
                "registerStore",
                BaseKVStore.class,
                BaseVectorStore.class,
                BaseDbStore.class,
                com.openjiuwen.core.foundation.store.Embedding.class
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> registerStore.invoke(
                        new LongTermMemory(),
                        mock(BaseKVStore.class),
                        mock(BaseVectorStore.class),
                        new Object(),
                        null
                )
        );
    }

    @Test
    void setConfigPropagatesLlmInitializationFailure() throws ReflectiveOperationException {
        LongTermMemory memory = new LongTermMemory();
        setField(memory, "kvStore", new InMemoryKVStore());
        setField(memory, "dbStore", mock(BaseDbStore.class));
        setField(memory, "memoryIndex", mock(BaseMemoryIndex.class));

        ModelClientConfig clientConfig = new ModelClientConfig();
        setField(clientConfig, "clientProvider", "__missing_provider__");
        MemoryEngineConfig config = MemoryEngineConfig.builder()
                .defaultModelCfg(ModelRequestConfig.builder().modelName("unit-test").build())
                .defaultModelClientCfg(clientConfig)
                .build();

        BaseError error = assertThrows(BaseError.class, () -> memory.setConfig(config));

        assertThat(error.getStatus()).isEqualTo(StatusCode.MODEL_PROVIDER_INVALID);
        assertThat(error.getMessage()).contains("__missing_provider__");
    }

    @Test
    void deleteMemByIdWriteManagerNotInitializedRaisesMemoryDeleteError() throws ReflectiveOperationException {
        LongTermMemory memory = new LongTermMemory();
        setField(memory, "kvStore", new InMemoryKVStore());

        BaseError error = assertThrows(BaseError.class, () -> memory.deleteMemById("mem123", "u1", "s1"));

        assertThat(error.getStatus()).isEqualTo(StatusCode.MEMORY_DELETE_MEMORY_EXECUTION_ERROR);
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
