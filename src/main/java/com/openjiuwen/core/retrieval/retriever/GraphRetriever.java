/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.retriever;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.common.TripleBeam;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.utils.FusionUtils;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Graph-aware retriever that expands retrieved chunks through linked triples.
 */
public class GraphRetriever extends AbstractRetriever {

    private final Retriever chunkRetriever;
    private final Retriever tripleRetriever;
    private final VectorStore vectorStore;
    private final Embedding embedModel;
    private final String chunkCollection;
    private final String tripleCollection;
    private String indexType;

    public GraphRetriever(Retriever chunkRetriever, Retriever tripleRetriever) {
        this(chunkRetriever, tripleRetriever, null, null, null, null);
    }

    public GraphRetriever(VectorStore vectorStore,
                          Embedding embedModel,
                          String chunkCollection,
                          String tripleCollection) {
        this(null, null, vectorStore, embedModel, chunkCollection, tripleCollection);
    }

    public GraphRetriever(Retriever chunkRetriever,
                          Retriever tripleRetriever,
                          VectorStore vectorStore,
                          Embedding embedModel,
                          String chunkCollection,
                          String tripleCollection) {
        this.chunkRetriever = chunkRetriever;
        this.tripleRetriever = tripleRetriever;
        this.vectorStore = vectorStore;
        this.embedModel = embedModel;
        this.chunkCollection = chunkCollection;
        this.tripleCollection = tripleCollection;
        this.indexType = vectorStore != null ? vectorStore.getIndexType() : null;
    }

    public void setIndexType(String indexType) {
        this.indexType = indexType;
    }

    @Override
    public String getIndexType() {
        return indexType == null ? "hybrid" : indexType;
    }

    @Override
    public boolean supportsMode(String mode) {
        return allowedModes().contains(mode);
    }

    public Retriever getRetrieverForMode(String mode, boolean isChunk) {
        ensureModeAllowed(mode);
        Retriever fixed = isChunk ? chunkRetriever : tripleRetriever;
        if (fixed != null) {
            if (!fixed.supportsMode(mode)) {
                throw RetrievalExceptions.error(
                        StatusCode.RETRIEVAL_RETRIEVER_CAPABILITY_NOT_SUPPORT,
                        "Provided retriever does not support mode=" + mode);
            }
            return fixed;
        }
        if (vectorStore == null) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_RETRIEVER_VECTOR_STORE_NOT_FOUND,
                    "vector_store is required for dynamic retriever creation");
        }
        String collection = isChunk ? chunkCollection : tripleCollection;
        if (collection == null || collection.isBlank()) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_RETRIEVER_COLLECTION_NOT_FOUND,
                    (isChunk ? "chunk" : "triple") + "_collection is required for dynamic retriever creation");
        }
        VectorStore scoped = vectorStore.withCollection(collection);
        return switch (mode) {
            case "vector" -> {
                if (embedModel == null) {
                    throw RetrievalExceptions.error(
                            StatusCode.RETRIEVAL_RETRIEVER_EMBED_MODEL_NOT_FOUND,
                            "embed_model is required for vector mode");
                }
                yield new VectorRetriever(scoped, embedModel);
            }
            case "sparse" -> new SparseRetriever(scoped);
            case "hybrid" -> new HybridRetriever(scoped, embedModel);
            default -> throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_RETRIEVER_MODE_NOT_SUPPORT,
                    "Unsupported mode: " + mode);
        };
    }

    @Override
    public List<RetrievalResult> retrieve(String query,
                                          int topK,
                                          Double scoreThreshold,
                                          String mode,
                                          Map<String, Object> options) {
        ensureModeAllowed(mode);
        if (scoreThreshold != null && !"vector".equals(mode)) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_RETRIEVER_SCORE_THRESHOLD_INVALID,
                    "score_threshold is only supported when mode='vector'");
        }
        Retriever chunkModeRetriever = getRetrieverForMode(mode, true);
        List<RetrievalResult> chunkResults = chunkModeRetriever.retrieve(query, topK, scoreThreshold, mode, options);
        int graphHops = options != null && options.get("graph_hops") instanceof Number n ? n.intValue() : 2;
        return graphExpansion(query, chunkResults, null, topK, mode, Map.of("graph_hops", graphHops));
    }

    public List<RetrievalResult> graphExpansion(String query,
                                                List<RetrievalResult> chunks,
                                                List<RetrievalResult> triples,
                                                Integer topK,
                                                String mode,
                                                Map<String, Object> options) {
        ensureModeAllowed(mode);
        if (chunks == null || chunks.isEmpty()) {
            if ("sparse".equals(mode)) {
                return getRetrieverForMode("sparse", true).retrieve(query, topK == null ? 5 : topK, null, "sparse", Map.of());
            }
            return List.of();
        }
        List<RetrievalResult> effectiveTriples = triples == null || triples.isEmpty()
                ? fetchTriples(chunks, mode)
                : triples;
        if (effectiveTriples.isEmpty()) {
            return trim(chunks, topK);
        }
        int graphHops = options != null && options.get("graph_hops") instanceof Number n ? n.intValue() : 2;
        List<TripleBeam> beams;
        try {
            beams = new TripleBeamSearch(getRetrieverForMode(mode, false), 10, 100, graphHops)
                    .beamSearch(query, effectiveTriples);
        } catch (Exception e) {
            return trim(chunks, topK);
        }
        if (beams.isEmpty()) {
            return trim(chunks, topK);
        }
        List<RetrievalResult> expandedTriples = new ArrayList<>();
        int maxLength = 0;
        for (TripleBeam beam : beams) {
            maxLength = Math.max(maxLength, beam.size());
        }
        for (int col = 0; col < maxLength; col++) {
            for (TripleBeam beam : beams) {
                if (col < beam.size()) {
                    expandedTriples.add(beam.get(col));
                }
            }
        }
        List<RetrievalResult> newChunks = fetchChunks(expandedTriples, mode);
        List<RetrievalResult> fused = newChunks.isEmpty()
                ? chunks
                : FusionUtils.rrfFusionRetrieval(List.of(newChunks, chunks), 60);
        return trim(fused, topK);
    }

    @Override
    public void close() {
        closeQuietly(chunkRetriever);
        closeQuietly(tripleRetriever);
    }

    private List<RetrievalResult> fetchTriples(List<RetrievalResult> chunks, String mode) {
        Retriever retriever = getRetrieverForMode(mode, false);
        if (!(retriever instanceof AbstractStoreBackedRetriever storeBacked)) {
            return List.of();
        }
        Set<String> chunkIds = new LinkedHashSet<>();
        for (RetrievalResult chunk : chunks) {
            String chunkId = chunk.getChunkId();
            if (chunkId == null && chunk.getMetadata() != null) {
                chunkId = VectorRetriever.stringValue(chunk.getMetadata().get("chunk_id"));
            }
            if (chunkId != null) {
                chunkIds.add(chunkId);
            }
        }
        if (chunkIds.isEmpty()) {
            return List.of();
        }
        List<SearchResult> raw = storeBacked.getVectorStore().queryByFilters(Map.of("chunk_id", chunkIds), 200);
        return raw.stream()
                .map(result -> new RetrievalResult(
                        result.getText(),
                        result.getScore(),
                        result.getMetadata(),
                        VectorRetriever.stringValue(result.getMetadata().get("doc_id")),
                        VectorRetriever.stringValue(result.getMetadata().get("chunk_id"))))
                .toList();
    }

    private List<RetrievalResult> fetchChunks(List<RetrievalResult> triples, String mode) {
        Retriever retriever = getRetrieverForMode(mode, true);
        if (!(retriever instanceof AbstractStoreBackedRetriever storeBacked)) {
            return List.of();
        }
        Set<String> chunkIds = new LinkedHashSet<>();
        for (RetrievalResult triple : triples) {
            String chunkId = triple.getChunkId();
            if (chunkId == null && triple.getMetadata() != null) {
                chunkId = VectorRetriever.stringValue(triple.getMetadata().get("chunk_id"));
            }
            if (chunkId != null) {
                chunkIds.add(chunkId);
            }
        }
        if (chunkIds.isEmpty()) {
            return List.of();
        }
        List<SearchResult> raw = storeBacked.getVectorStore().queryByFilters(Map.of("chunk_id", chunkIds), 200);
        return raw.stream()
                .map(result -> new RetrievalResult(
                        result.getText(),
                        result.getScore(),
                        result.getMetadata(),
                        VectorRetriever.stringValue(result.getMetadata().get("doc_id")),
                        VectorRetriever.stringValue(result.getMetadata().get("chunk_id"))))
                .toList();
    }

    private List<String> allowedModes() {
        return switch (getIndexType()) {
            case "vector" -> List.of("vector");
            case "bm25" -> List.of("sparse");
            default -> List.of("vector", "sparse", "hybrid");
        };
    }

    private void ensureModeAllowed(String mode) {
        if (indexType == null) {
            return;
        }
        if (!allowedModes().contains(mode)) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_RETRIEVER_MODE_INVALID,
                    "mode=" + mode + " is incompatible with index_type=" + indexType);
        }
    }

    private static List<RetrievalResult> trim(List<RetrievalResult> results, Integer topK) {
        if (topK == null || results.size() <= topK) {
            return results;
        }
        return new ArrayList<>(results.subList(0, topK));
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }
}
