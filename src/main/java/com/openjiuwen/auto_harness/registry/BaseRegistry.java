package com.openjiuwen.auto_harness.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Mirrors Python's {@code BaseRegistry} in {@code openjiuwen.auto_harness.registry.base}.
 */
public class BaseRegistry<T> {

    private final Map<String, T> items = new LinkedHashMap<>();
    private final Function<T, String> nameExtractor;

    public BaseRegistry(Function<T, String> nameExtractor) {
        this.nameExtractor = nameExtractor;
    }

    public void register(T spec) {
        String name = nameExtractor.apply(spec);
        if (items.containsKey(name)) {
            throw new IllegalArgumentException("Duplicate registration: " + name);
        }
        items.put(name, spec);
    }

    public T get(String name) {
        return items.get(name);
    }

    public T require(String name) {
        T spec = get(name);
        if (spec == null) {
            throw new IllegalArgumentException("Unknown item '" + name + "'");
        }
        return spec;
    }

    public List<String> names() {
        return new ArrayList<>(items.keySet());
    }
}
