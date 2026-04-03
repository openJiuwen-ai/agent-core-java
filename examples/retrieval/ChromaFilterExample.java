import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.vector_store.ChromaVectorStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Demonstrates Java retrieval-layer filtering with the Chroma-compatible in-memory store.
 */
public final class ChromaFilterExample {

    private ChromaFilterExample() {
    }

    public static void main(String[] args) {
        String collectionName = "retrieval_chroma_example_" + System.currentTimeMillis();
        ChromaVectorStore store = new ChromaVectorStore(
                RetrievalExampleSupport.chromaVectorStoreConfig(collectionName),
                "hybrid");

        try {
            store.add(buildSampleData(), 4, Map.of());

            ExampleOutput.section("Chroma-Compatible Filter Example");
            ExampleOutput.keyValue("Collection", collectionName);
            ExampleOutput.line("Java retrieval filters in this store currently support equality and in-list checks.");

            ExampleOutput.subsection("queryByFilters(category = tech)");
            printResults(store.queryByFilters(Map.of("category", "tech"), 10));

            ExampleOutput.subsection("queryByFilters(author in [Alice, Bob])");
            printResults(store.queryByFilters(Map.of("author", List.of("Alice", "Bob")), 10));

            ExampleOutput.subsection("Vector search with category filter");
            printResults(store.search(List.of(0.98f, 0.02f, 0.00f), 5, Map.of("category", "tech"), Map.of()));

            ExampleOutput.subsection("Hybrid search with author filter");
            printResults(store.hybridSearch(
                    "distributed systems and embeddings",
                    List.of(0.95f, 0.04f, 0.01f),
                    5,
                    0.7,
                    Map.of("author", List.of("Alice", "Bob")),
                    Map.of()));
        } finally {
            store.deleteTable(collectionName);
            store.close();
        }
    }

    private static List<Map<String, Object>> buildSampleData() {
        List<Map<String, Object>> data = new ArrayList<>();
        data.add(record("chunk_1", "doc_1", "tech", "Alice", "Dense retrieval with vector embeddings.", List.of(0.99f, 0.01f, 0.00f)));
        data.add(record("chunk_2", "doc_1", "tech", "Bob", "Sparse retrieval still helps long-tail matching.", List.of(0.90f, 0.08f, 0.02f)));
        data.add(record("chunk_3", "doc_2", "science", "Charlie", "Astronomy observations need careful calibration.", List.of(0.05f, 0.90f, 0.05f)));
        data.add(record("chunk_4", "doc_3", "business", "Alice", "Revenue forecasting depends on historical demand.", List.of(0.02f, 0.12f, 0.86f)));
        data.add(record("chunk_5", "doc_4", "tech", "Diana", "Rerankers improve recall-heavy retrieval pipelines.", List.of(0.93f, 0.05f, 0.02f)));
        return data;
    }

    private static Map<String, Object> record(String chunkId,
                                              String docId,
                                              String category,
                                              String author,
                                              String text,
                                              List<Float> vector) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("doc_id", docId);
        metadata.put("chunk_id", chunkId);
        metadata.put("category", category);
        metadata.put("author", author);

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", chunkId);
        record.put("chunk_id", chunkId);
        record.put("doc_id", docId);
        record.put("category", category);
        record.put("author", author);
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
                    "  %-8s score=%.4f doc_id=%s category=%s author=%s text=%s",
                    result.getId(),
                    result.getScore(),
                    result.getMetadata().get("doc_id"),
                    result.getMetadata().get("category"),
                    result.getMetadata().get("author"),
                    result.getText());
        }
    }
}