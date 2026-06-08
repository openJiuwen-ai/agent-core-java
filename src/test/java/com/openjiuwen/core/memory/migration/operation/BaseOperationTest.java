/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BaseOperationTest {

    private static final class ExampleOperation extends BaseOperation {
        private ExampleOperation(OperationMetadata metadata) {
            super(metadata);
        }
    }

    @Test
    void exposesMetadataAndDescriptionFallback() {
        BaseOperation operation = new ExampleOperation(new OperationMetadata(7));

        assertThat(operation.getMetadata().getSchemaVersion()).isEqualTo(7);
        assertThat(operation.getSchemaVersion()).isEqualTo(7);
        assertThat(operation.getDescription()).isEqualTo("ExampleOperation");
    }

    @Test
    void usesExplicitDescriptionWhenPresent() {
        BaseOperation operation = new ExampleOperation(new OperationMetadata(9, "audit note"));

        assertThat(operation.getDescription()).isEqualTo("audit note");
    }
}
