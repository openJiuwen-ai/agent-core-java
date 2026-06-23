/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.prompt.assemble.variables;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's string placeholder formatting in
 * {@code openjiuwen/core/foundation/prompt/assemble/variables/textable.py}.
 */
class TextableVariableTest {

    @Test
    void formatsNestedPlaceholdersFromMapsAndObjects() {
        TextableVariable variable = new TextableVariable(
                "Hello {{user.name}}, active={{user.active}}, count={{count}}"
        );

        Object result = variable.eval(Map.of(
                "user", new Person("Ada", true),
                "count", 5
        ));

        assertThat(result).isEqualTo("Hello Ada, active=True, count=5");
    }

    private record Person(String name, boolean active) {
    }
}
