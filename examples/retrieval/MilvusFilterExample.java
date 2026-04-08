/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package examples.retrieval;

import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.vector_store.MilvusVectorStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Demonstrates retrieval-layer filtering against a live Milvus instance.
 */
public final class MilvusFilterExample {

    private MilvusFilterExample() {
    }

    public static void main(String[] args) {
        String collectionName = "retrieval_milvus_example_" + System.currentTimeMillis();
        VectorStoreConfig config = RetrievalExampleSupport.milvusVectorStoreConfig(collectionName);
        MilvusVectorStore store = newMilvusStore(config);

        try {
            store.add(buildSampleData(), 4, Map.of());

            ExampleOutput.section("Milvus Filter Example");
            ExampleOutput.keyValue("Milvus URI", RetrievalExampleSupport.milvusUri());
            ExampleOutput.keyValue("Collection", collectionName);
            ExampleOutput.line("The retrieval-layer Milvus integration filters on concrete collection fields such as doc_id and chunk_id.");

            ExampleOutput.subsection("queryByFilters(doc_id = doc_1)");
            printResults(store.queryByFilters(Map.of("doc_id", "doc_1"), 10));

            ExampleOutput.subsection("queryByFilters(chunk_id in [chunk_1, chunk_2])");
            printResults(store.queryByFilters(Map.of("chunk_id", List.of("chunk_1", "chunk_2")), 10));

            ExampleOutput.subsection("Vector search with doc_id filter");
            printResults(store.search(List.of(0.98f, 0.02f, 0.00f), 5, Map.of("doc_id", "doc_1"), Map.of()));

            ExampleOutput.subsection("Delete one chunk and query again");
            boolean deleted = store.delete(List.of("chunk_2"), Map.of("doc_id", "doc_1"), Map.of());
            ExampleOutput.keyValue("Deleted", deleted);
            printResults(store.queryByFilters(Map.of("doc_id", "doc_1"), 10));
        } catch (RuntimeException ex) {
            ExampleOutput.line("Milvus example failed: %s", ex.getMessage());
            ExampleOutput.line("Check MILVUS_URI, MILVUS_TOKEN, and whether the server is reachable.");
        } finally {
            try {
                if (store.tableExists(collectionName)) {
                    store.deleteTable(collectionName);
                }
            } catch (RuntimeException ignored) {
                ExampleOutput.line("Cleanup warning: failed to drop collection %s", collectionName);
            }
            store.close();
        }
    }

    private static MilvusVectorStore newMilvusStore(VectorStoreConfig config) {
        String token = RetrievalExampleSupport.milvusToken();
        if (token.isBlank()) {
            return new MilvusVectorStore(config, RetrievalExampleSupport.milvusUri(), "hybrid");
        }
        return new MilvusVectorStore(config, RetrievalExampleSupport.milvusUri(), token, "hybrid");
    }

    private static List<Map<String, Object>> buildSampleData() {
        List<Map<String, Object>> data = new ArrayList<>();
        data.add(record("chunk_1", "doc_1", "Milvus dense retrieval for technical content.", List.of(0.99f, 0.01f, 0.00f)));
        data.add(record("chunk_2", "doc_1", "A second chunk from the same Milvus document.", List.of(0.94f, 0.05f, 0.01f)));
        data.add(record("chunk_3", "doc_2", "Independent chunk in a different Milvus document.", List.of(0.05f, 0.94f, 0.01f)));
        return data;
    }

    private static Map<String, Object> record(String chunkId,
                                              String docId,
                                              String text,
                                              List<Float> vector) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("doc_id", docId);
        metadata.put("chunk_id", chunkId);

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", chunkId);
        record.put("chunk_id", chunkId);
        record.put("doc_id", docId);
        record.put("text", text);
        record.put("vector", vector);
        record.put("metadata", metadata);
        return record;
    }

    private static void printResults(List<SearchResult> results) {
        if (results.isEmpty()) {
            ExampleOutput.line("No results.");
            return;
        }
        for (SearchResult result : results) {
            ExampleOutput.line(
                    "  %-8s score=%.4f doc_id=%s chunk_id=%s text=%s",
                    result.getId(),
                    result.getScore(),
                    result.getMetadata().get("doc_id"),
                    result.getMetadata().get("chunk_id"),
                    result.getText());
        }
    }
}