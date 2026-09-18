package com.openjiuwen.core.runner.resourcemanager;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThreadSafeDictTest {

    @Test
    void getOrSetAndGetOrCreatePreserveExistingNullEntries() {
        ThreadSafeDict<String, String> dictionary = new ThreadSafeDict<>(new LinkedHashMap<>());
        dictionary.put("presentNull", null);
        AtomicInteger invocations = new AtomicInteger();

        assertThat(dictionary.getOrSet("presentNull", "fallback")).isNull();
        assertThat(dictionary.getOrCreate("presentNull", () -> {
            invocations.incrementAndGet();
            return "created";
        })).isNull();
        assertThat(invocations).hasValue(0);
        assertThat(dictionary.snapshot()).containsEntry("presentNull", null);
    }

    @Test
    void iteratorAndItemsUseSnapshotsAndPopMatchesPythonBehavior() {
        ThreadSafeDict<String, Integer> dictionary = new ThreadSafeDict<>(new LinkedHashMap<>(Map.of("a", 1, "b", 2)));

        var iterator = dictionary.iterator();
        var items = dictionary.items();
        dictionary.put("c", 3);

        assertThat(iterator).toIterable().containsExactlyInAnyOrder("a", "b");
        assertThat(items).containsExactlyInAnyOrder(
                Map.entry("a", 1),
                Map.entry("b", 2)
        );
        assertThat(dictionary.pop("missing", 9)).isEqualTo(9);
        assertThatThrownBy(() -> dictionary.pop("missing"))
                .isInstanceOf(java.util.NoSuchElementException.class);
        dictionary.delete("c");
        assertThat(dictionary.containsKey("c")).isFalse();
    }
}
