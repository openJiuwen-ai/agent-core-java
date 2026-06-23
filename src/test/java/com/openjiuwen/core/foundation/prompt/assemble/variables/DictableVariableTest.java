/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.prompt.assemble.variables;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's dict/list placeholder recursion in
 * {@code openjiuwen/core/foundation/prompt/assemble/variables/dictable.py}.
 */
class DictableVariableTest {

    @Test
    void recursivelyFormatsDictAndListPlaceholders() {
        DictableVariable variable = new DictableVariable(Map.of(
                "title", "{{user.name}}",
                "items", List.of("{{count}}", Map.of("flag", "{{enabled}}"))
        ));

        Object result = variable.eval(Map.of(
                "user", new Person("Ada"),
                "count", 3,
                "enabled", true
        ));

        assertThat(result).isEqualTo(Map.of(
                "title", "Ada",
                "items", List.of("3", Map.of("flag", "True"))
        ));
    }

    private record Person(String name) {
    }
}
