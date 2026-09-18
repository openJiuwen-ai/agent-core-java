package com.openjiuwen.core.common.utils;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code openjiuwen.core.common.utils.dict_utils} in
 * {@code openjiuwen/core/common/utils/dict_utils.py}.
 *
 * <p>Mirrors Python's {@code test_dict} in
 * {@code tests/unit_tests/core/common/utils/test_dict.py}.</p>
 */
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

    @Test
    void extractLeafMirrorsPythonTestDict() {
        Map<String, Object> profile = linkedMap(
                "name", "\u5f20\u4e09",
                "age", 25,
                "address", linkedMap("city", "\u5317\u4eac", "street", "\u671d\u9633\u8def")
        );
        Map<String, Object> user = linkedMap(
                "profile", profile,
                "settings", linkedMap("notifications", true, "language", "\u4e2d\u6587")
        );
        Map<String, Object> system = linkedMap(
                "version", "1.0.0",
                "modules", List.of("auth", "payment", "analytics"),
                "config", linkedMap("timeout", 30, "retry_count", 3)
        );
        Map<String, Object> sampleData = linkedMap(
                "user", user,
                "system", system,
                "status", "active"
        );

        List<DictUtils.PathValuePair> leaves = DictUtils.extractLeafNodes(sampleData);
        Object tree = DictUtils.rebuildDict(leaves);

        assertThat(leaves).hasSize(13);
        assertThat(leaves)
                .contains(
                        new DictUtils.PathValuePair(List.of("user", "profile", "name"), "\u5f20\u4e09"),
                        new DictUtils.PathValuePair(List.of("system", "modules", "[0]"), "auth"),
                        new DictUtils.PathValuePair(List.of("status"), "active")
                );
        assertThat(DictUtils.formatPath(List.of("system", "modules", "[0]"))).isEqualTo("system.modules[0]");
        assertThat(tree).isEqualTo(sampleData);
    }

    @Test
    void rebuildMirrorsPythonTestDict() {
        List<DictUtils.PathValuePair> sampleLeaves = List.of(
                new DictUtils.PathValuePair(List.of("user", "profile", "name"), "\u5f20\u4e09"),
                new DictUtils.PathValuePair(List.of("user", "profile", "age"), 25),
                new DictUtils.PathValuePair(List.of("user", "profile", "address", "city"), "\u5317\u4eac"),
                new DictUtils.PathValuePair(List.of("user", "profile", "address", "street"), "\u671d\u9633\u8def"),
                new DictUtils.PathValuePair(List.of("user", "settings", "notifications"), true),
                new DictUtils.PathValuePair(List.of("user", "settings", "language"), "\u4e2d\u6587"),
                new DictUtils.PathValuePair(List.of("system", "version"), "1.0.0"),
                new DictUtils.PathValuePair(List.of("system", "config", "timeout"), 30),
                new DictUtils.PathValuePair(List.of("system", "config", "retry_count"), 3),
                new DictUtils.PathValuePair(List.of("status"), "active")
        );
        List<DictUtils.PathValuePair> listLeaves = List.of(
                new DictUtils.PathValuePair(List.of("data", "users", "[0]", "name"), "Alice"),
                new DictUtils.PathValuePair(List.of("data", "users", "[0]", "age"), 30),
                new DictUtils.PathValuePair(List.of("data", "users", "[1]", "name"), "Bob"),
                new DictUtils.PathValuePair(List.of("data", "users", "[1]", "age"), 25),
                new DictUtils.PathValuePair(List.of("data", "tags", "[0]"), "python"),
                new DictUtils.PathValuePair(List.of("data", "tags", "[1]"), "programming"),
                new DictUtils.PathValuePair(List.of("metadata", "count"), 2)
        );

        Map<String, Object> rebuiltDict = DictUtils.rebuildDictFromPaths(sampleLeaves);
        Object rebuiltWithLists = DictUtils.rebuildDict(listLeaves);

        assertThat(rebuiltDict)
                .containsEntry("status", "active")
                .extractingByKey("user")
                .isInstanceOf(Map.class);
        assertThat(rebuiltWithLists)
                .isEqualTo(linkedMap(
                        "data", linkedMap(
                                "users", List.of(
                                        linkedMap("name", "Alice", "age", 30),
                                        linkedMap("name", "Bob", "age", 25)
                                ),
                                "tags", List.of("python", "programming")
                        ),
                        "metadata", linkedMap("count", 2)
                ));
    }

    private static Map<String, Object> linkedMap(Object... entries) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            map.put((String) entries[i], entries[i + 1]);
        }
        return map;
    }
}
