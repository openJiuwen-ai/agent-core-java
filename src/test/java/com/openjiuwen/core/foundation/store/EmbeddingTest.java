package com.openjiuwen.core.foundation.store;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingTest {

    @Test
    void overloadsUseEmptyKwargsAndOptionalBatchSize() {
        RecordingEmbedding embedding = new RecordingEmbedding();

        assertThat(embedding.embedQuery("hello").join()).containsExactly(1.0D, 2.0D);
        assertThat(embedding.lastQueryKwargs).isEmpty();

        assertThat(embedding.embedDocuments(List.of("a", "b")).join())
                .containsExactly(List.of(1.0D), List.of(2.0D));
        assertThat(embedding.lastBatchSize).isNull();
        assertThat(embedding.lastDocumentKwargs).isEmpty();
    }

    @Test
    void configRetainsOptionalApiKey() {
        EmbeddingConfig config = EmbeddingConfig.builder()
                .modelName("demo")
                .baseUrl("https://example.test")
                .apiKey("secret")
                .build();

        assertThat(config.getModelName()).isEqualTo("demo");
        assertThat(config.getBaseUrl()).isEqualTo("https://example.test");
        assertThat(config.getApiKey()).isEqualTo("secret");
    }

    private static final class RecordingEmbedding extends Embedding {
        private Map<String, Object> lastQueryKwargs = Map.of();
        private Map<String, Object> lastDocumentKwargs = Map.of();
        private Integer lastBatchSize;

        @Override
        public CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
            this.lastQueryKwargs = kwargs;
            return CompletableFuture.completedFuture(List.of(1.0D, 2.0D));
        }

        @Override
        public CompletableFuture<List<List<Double>>> embedDocuments(
                List<String> texts,
                Integer batchSize,
                Map<String, Object> kwargs
        ) {
            this.lastBatchSize = batchSize;
            this.lastDocumentKwargs = kwargs;
            return CompletableFuture.completedFuture(List.of(List.of(1.0D), List.of(2.0D)));
        }

        @Override
        public int getDimension() {
            return 2;
        }
    }
}
