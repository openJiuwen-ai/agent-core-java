package com.openjiuwen.core.common.utils;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DictUtilsTest {

    @Test
    void createFlattenAndRebuildFollowPythonShape() {
        Object nested = DictUtils.createNestedDict("a.b", 1);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("a", List.of(1, Map.of("b", 2)));
        data.put("c", Map.of("d", "x"));

        assertThat(nested).isEqualTo(Map.of("a", Map.of("b", 1)));
        assertThat(DictUtils.formatPath(List.of("a", "[0]"))).isEqualTo("a[0]");
        assertThat(DictUtils.extractLeafNodes(data))
                .containsExactly(
                        new DictUtils.PathValuePair(List.of("a", "[0]"), 1),
                        new DictUtils.PathValuePair(List.of("a", "[1]", "b"), 2),
                        new DictUtils.PathValuePair(List.of("c", "d"), "x")
                );
        assertThat(DictUtils.flattenDict(data))
                .containsEntry("a[0]", 1)
                .containsEntry("a[1].b", 2)
                .containsEntry("c.d", "x");
        assertThat(DictUtils.rebuildDictFromPaths(List.of(
                new DictUtils.PathValuePair(List.of("a", "b"), 1),
                new DictUtils.PathValuePair(List.of("a", "c"), 2)
        ))).isEqualTo(Map.of("a", Map.of("b", 1, "c", 2)));
        assertThat(DictUtils.rebuildDict(List.of(
                new DictUtils.PathValuePair(List.of("a", "[0]"), 1),
                new DictUtils.PathValuePair(List.of("a", "[1]", "b"), 2)
        ))).isEqualTo(Map.of("a", List.of(1, Map.of("b", 2))));
    }
}
