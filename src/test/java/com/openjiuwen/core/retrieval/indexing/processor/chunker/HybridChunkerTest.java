/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HybridChunkerTest {

    @Test
    void constructorInheritsInnerChunkerSettings() {
        RecordingChunker innerChunker = new RecordingChunker(256, 12);
        HybridChunker hybridChunker = new HybridChunker(innerChunker);

        assertThat(hybridChunker.getChunkSize()).isEqualTo(256);
        assertThat(hybridChunker.getChunkOverlap()).isEqualTo(12);
        assertThat(hybridChunker.getLengthFunction().applyAsInt("abcd")).isEqualTo(4);
    }

    @Test
    void chunkTextDelegatesToInnerChunker() {
        RecordingChunker innerChunker = new RecordingChunker(256, 12);
        HybridChunker hybridChunker = new HybridChunker(innerChunker);

        assertThat(hybridChunker.chunkText("abcdefghij"))
                .containsExactly("abcde", "fghij");
    }

    @Test
    void defaultPredicateKeepsTableRowsAsSingleChunk() {
        RecordingChunker innerChunker = new RecordingChunker(256, 12);
        HybridChunker hybridChunker = new HybridChunker(innerChunker);
        Document document = new Document("doc-1", "  row text  ", Map.of("source_type", "row", "table", "demo"));

        List<TextChunk> chunks = hybridChunker.chunkDocuments(List.of(document));

        assertThat(chunks).hasSize(1);
        TextChunk chunk = chunks.get(0);
        assertThat(chunk.getText()).isEqualTo("row text");
        assertThat(chunk.getDocId()).isEqualTo("doc-1");
        assertThat(chunk.getMetadata())
                .containsEntry("source_type", "row")
                .containsEntry("table", "demo")
                .containsEntry("chunk_index", 0)
                .containsEntry("total_chunks", 1);
        assertThat(chunk.getMetadata().get("chunk_id")).isEqualTo(chunk.getId_());
        assertThat(innerChunker.getDelegatedDocuments()).isEmpty();
    }

    @Test
    void nonMatchingDocumentsDelegateToInnerChunker() {
        RecordingChunker innerChunker = new RecordingChunker(256, 12);
        HybridChunker hybridChunker = new HybridChunker(innerChunker, document -> false);
        Document document = new Document("doc-2", "abcdef", Map.of("source_type", "text"));

        List<TextChunk> chunks = hybridChunker.chunkDocuments(List.of(document));

        assertThat(innerChunker.getDelegatedDocuments()).containsExactly("doc-2");
        assertThat(chunks).hasSize(2);
        assertThat(chunks).extracting(TextChunk::getText).containsExactly("abcde", "f");
        assertThat(chunks).allMatch(chunk -> "doc-2".equals(chunk.getDocId()));
    }

    private static final class RecordingChunker extends Chunker {

        private final List<String> delegatedDocuments = new ArrayList<>();

        private RecordingChunker(int chunkSize, int chunkOverlap) {
            super(chunkSize, chunkOverlap, null);
        }

        @Override
        public List<String> chunkText(String text) {
            List<String> chunks = new ArrayList<>();
            for (int i = 0; i < text.length(); i += 5) {
                chunks.add(text.substring(i, Math.min(i + 5, text.length())));
            }
            return chunks;
        }

        @Override
        public List<TextChunk> chunkDocuments(List<Document> documents) {
            delegatedDocuments.addAll(documents.stream().map(Document::getId_).toList());
            List<TextChunk> chunks = new ArrayList<>();
            for (Document document : documents) {
                List<String> texts = chunkText(document.getText());
                for (int i = 0; i < texts.size(); i++) {
                    Map<String, Object> metadata = new LinkedHashMap<>(document.getMetadata());
                    metadata.put("chunk_index", i);
                    metadata.put("total_chunks", texts.size());
                    chunks.add(new TextChunk("inner-" + document.getId_() + "-" + i, texts.get(i), document.getId_(), metadata));
                }
            }
            return chunks;
        }

        private List<String> getDelegatedDocuments() {
            return delegatedDocuments;
        }
    }
}
