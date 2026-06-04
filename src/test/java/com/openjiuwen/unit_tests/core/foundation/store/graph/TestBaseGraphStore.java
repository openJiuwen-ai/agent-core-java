package com.openjiuwen.unit_tests.core.foundation.store.graph;

import com.openjiuwen.core.foundation.store.graph.GraphStore;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestBaseGraphStore {

    @Test
    void testMockImplementingProtocolIsInstanceOfGraphStore() {
        GraphStore store = Mockito.mock(GraphStore.class);
        assertTrue(store instanceof GraphStore);
    }

    @Test
    void testProtocolHasRequiredMembers() {
        Set<String> methodNames = Arrays.stream(GraphStore.class.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
        Set<String> required = Set.of(
                "getConfig",
                "getEmbedExecutor",
                "getEmbedder",
                "fromConfig",
                "refresh",
                "addData",
                "addEntity",
                "addRelation",
                "addEpisode",
                "isEmpty",
                "query",
                "delete",
                "search",
                "attachEmbedder",
                "close");
        assertTrue(methodNames.containsAll(required));
    }
}
