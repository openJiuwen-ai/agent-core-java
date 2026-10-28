/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.retrieval.vector_store;

import com.openjiuwen.core.retrieval.common.StoreType;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChromaPersistenceTest {
    @TempDir
    Path storage;

    @Test
    void newlyCreatedClientReadsAndDeletesPreviouslyStoredVectors() {
        ChromaVectorStore first = store("docs");
        first.add(List.of(document("one")), null, Map.of()).join();

        ChromaVectorStore recovered = store("docs");
        assertThat(recovered.search(List.of(1.0, 0.0), 10, null, Map.of()).join()).hasSize(1);
        assertThat(recovered.getCollection().query(null, "text-one", 10, Map.of()).get("documents"))
                .isEqualTo(List.of(List.of("text-one")));
        recovered.getCollection().delete(List.of("one"), Map.of());
        assertThat(first.search(List.of(1.0, 0.0), 10, null, Map.of()).join()).isEmpty();
        recovered.deleteTable("docs").join();
        assertThat(first.tableExists("docs").join()).isFalse();
    }

    @Test
    void independentlyConstructedClientsMergeConcurrentWrites() throws Exception {
        ChromaVectorStore first = store("docs");
        ChromaVectorStore second = store("docs");
        List<CompletableFuture<Void>> writes = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            ChromaVectorStore writer = index % 2 == 0 ? first : second;
            writes.add(writer.add(List.of(document("doc-" + index)), null, Map.of()));
        }
        CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
        assertThat(store("docs").search(List.of(1.0, 0.0), 100, null, Map.of()).join()).hasSize(40);
        assertThat(store("separate").search(List.of(1.0, 0.0), 100, null, Map.of()).join()).isEmpty();
    }

    @Test
    void corruptPersistenceIsReportedInsteadOfReplacingExistingData() throws Exception {
        store("docs");
        Path database;
        try (var files = Files.list(storage)) {
            database = files.filter(file -> file.toString().endsWith(".json")).findFirst().orElseThrow();
        }
        Files.writeString(database, "not-json");
        assertThatThrownBy(() -> store("docs")).isInstanceOf(java.io.UncheckedIOException.class);
        assertThat(Files.readString(database)).isEqualTo("not-json");
    }

    private ChromaVectorStore store(String collection) {
        return new ChromaVectorStore(new VectorStoreConfig(StoreType.CHROMA, collection), storage.toString());
    }

    private static Map<String, Object> document(String id) {
        return Map.of("id", id, "content", "text-" + id, "embedding", List.of(1.0, 0.0),
                "metadata", Map.of("source", "regression"));
    }
}
