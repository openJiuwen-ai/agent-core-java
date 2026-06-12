/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.retriever;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.TripleBeam;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.utils.FusionUtils;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Graph retriever implementation combining chunk retrieval and graph retrieval.
 * <p>
 * Mirrors Python's {@code GraphRetriever} in
 * {@code openjiuwen/core/retrieval/retriever/graph_retriever.py}.
 * </p>
 */
public class GraphRetriever implements Retriever {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphRetriever.class);
    private static final String MODE_VECTOR = "vector";
    private static final String MODE_SPARSE = "sparse";
    private static final String MODE_HYBRID = "hybrid";
    private static final String MODE_BM25 = "bm25";
    private static final int DEFAULT_TOP_K = 5;
    private static final int DEFAULT_GRAPH_HOPS = 2;

    private final Retriever chunkRetriever;
    private final Retriever tripleRetriever;
    private final VectorStore vectorStore;
    private final Embedding embedModel;
    private final String chunkCollection;
    private final String tripleCollection;
    private String indexType;

    public GraphRetriever() {
        this(null, null, null, null, null, null);
    }

    public GraphRetriever(Retriever chunkRetriever, Retriever tripleRetriever) {
        this(chunkRetriever, tripleRetriever, null, null, null, null);
    }

    public GraphRetriever(
            VectorStore vectorStore,
            Embedding embedModel,
            String chunkCollection,
            String tripleCollection
    ) {
        this(null, null, vectorStore, embedModel, chunkCollection, tripleCollection);
    }

    public GraphRetriever(
            Retriever chunkRetriever,
            Retriever tripleRetriever,
            VectorStore vectorStore,
            Embedding embedModel,
            String chunkCollection,
            String tripleCollection
    ) {
        this.chunkRetriever = chunkRetriever;
        this.tripleRetriever = tripleRetriever;
        this.vectorStore = vectorStore;
        this.embedModel = embedModel;
        this.chunkCollection = chunkCollection;
        this.tripleCollection = tripleCollection;
    }

    public Retriever getChunkRetriever() {
        return chunkRetriever;
    }

    public Retriever getTripleRetriever() {
        return tripleRetriever;
    }

    public VectorStore getVectorStore() {
        return vectorStore;
    }

    public Embedding getEmbedModel() {
        return embedModel;
    }

    public String getChunkCollection() {
        return chunkCollection;
    }

    public String getTripleCollection() {
        return tripleCollection;
    }

    public void setIndexType(String indexType) {
        this.indexType = indexType;
    }

    @Override
    public String getIndexType() {
        return indexType;
    }

    @Override
    public List<RetrievalResult> retrieve(
            String query,
            int topK,
            Double scoreThreshold,
            String mode,
            Map<String, Object> options
    ) {
        String actualMode = normalizeMode(mode);
        ensureModeAllowed(actualMode);
        Map<String, Object> safeOptions = optionsOrEmpty(options);
        int graphHops = optionAsInt(safeOptions, "graph_hops", DEFAULT_GRAPH_HOPS);

        if (scoreThreshold != null && !MODE_VECTOR.equals(actualMode)) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_RETRIEVER_SCORE_THRESHOLD_INVALID,
                    "error_msg",
                    "score_threshold is only supported when mode='vector'"
            );
        }

        Retriever retriever = getRetrieverForMode(actualMode, true);
        List<RetrievalResult> chunkResults = retriever.retrieve(
                query,
                topK,
                scoreThreshold,
                actualMode,
                safeOptions
        );

        LOGGER.info(
                "[graph] Graph retrieval: graph_expansion=True chunk_hits={} topk={} mode={}",
                chunkResults == null ? 0 : chunkResults.size(),
                topK,
                actualMode
        );

        Map<String, Object> expansionOptions = new LinkedHashMap<>(safeOptions);
        expansionOptions.put("max_length", graphHops);
        return graphExpansion(query, chunkResults, null, topK, actualMode, expansionOptions);
    }

    @Override
    public List<List<RetrievalResult>> batchRetrieve(
            List<String> queries,
            int topK,
            String mode,
            Map<String, Object> options
    ) {
        if (queries == null || queries.isEmpty()) {
            return List.of();
        }
        List<List<RetrievalResult>> results = new ArrayList<>(queries.size());
        for (String query : queries) {
            results.add(retrieve(query, topK, null, mode, options));
        }
        return List.copyOf(results);
    }

    public List<RetrievalResult> graphExpansion(
            String query,
            List<RetrievalResult> chunks,
            Integer topk,
            String mode
    ) {
        return graphExpansion(query, chunks, null, topk, mode, Map.of());
    }

    public List<RetrievalResult> graphExpansion(
            String query,
            List<RetrievalResult> chunks,
            List<RetrievalResult> triples,
            Integer topk,
            String mode,
            Map<String, Object> options
    ) {
        String actualMode = normalizeMode(mode);
        ensureModeAllowed(actualMode);
        Map<String, Object> safeOptions = optionsOrEmpty(options);
        List<RetrievalResult> safeChunks = chunks == null ? List.of() : chunks;
        if (safeChunks.isEmpty()) {
            LOGGER.warn("[graph] chunk_retriever returned empty, no results to expand (mode={})", actualMode);
            if (MODE_SPARSE.equals(actualMode)) {
                Retriever sparseRetriever = getRetrieverForMode(MODE_SPARSE, true);
                List<RetrievalResult> fallback = sparseRetriever.retrieve(
                        query,
                        topk == null ? DEFAULT_TOP_K : topk,
                        null,
                        MODE_SPARSE,
                        safeOptions
                );
                return limit(fallback, topk);
            }
            return List.of();
        }

        List<RetrievalResult> safeTriples = triples == null ? List.of() : triples;
        if (safeTriples.isEmpty()) {
            try {
                safeTriples = fetchTriples(safeChunks, actualMode, safeOptions);
                LOGGER.info(
                        "[graph] Fetching triples from chunk index: chunks={} triples={}",
                        safeChunks.size(),
                        safeTriples.size()
                );
            } catch (RuntimeException exception) {
                LOGGER.warn("[graph] Failed to fetch triples from chunk index: {}", exception.getMessage());
                safeTriples = List.of();
            }
        }

        if (safeTriples.isEmpty()) {
            LOGGER.info("[graph] No triples found, returning original chunks");
            return limit(safeChunks, topk);
        }

        List<TripleBeam> beams;
        try {
            Retriever retriever = getRetrieverForMode(actualMode, false);
            TripleBeamSearch tripleBeamSearch = new TripleBeamSearch(
                    retriever,
                    optionAsInt(safeOptions, "num_beams", 10),
                    optionAsInt(safeOptions, "num_candidates_per_beam", 100),
                    optionAsInt(safeOptions, "max_length", DEFAULT_GRAPH_HOPS),
                    optionAsInt(safeOptions, "encoder_batch_size", 256),
                    actualMode
            );
            beams = tripleBeamSearch.beamSearch(query, safeTriples);
        } catch (RuntimeException exception) {
            LOGGER.warn("[graph] Beam search failed: {}, falling back to chunks", exception.getMessage());
            return limit(safeChunks, topk);
        }

        if (beams.isEmpty()) {
            LOGGER.info("[graph] Beam search returned empty, returning original chunks");
            return limit(safeChunks, topk);
        }

        int maxLength = 0;
        for (TripleBeam beam : beams) {
            maxLength = Math.max(maxLength, beam.size());
        }

        List<RetrievalResult> expandedTriples = new ArrayList<>();
        for (int col = 0; col < maxLength; col++) {
            for (TripleBeam beam : beams) {
                if (col >= beam.size()) {
                    continue;
                }
                expandedTriples.add(beam.get(col));
            }
        }

        List<RetrievalResult> newChunks = fetchChunks(expandedTriples, actualMode, safeOptions);
        LOGGER.info(
                "[graph] Graph expansion beam results: triples={} additional_chunks={}",
                expandedTriples.size(),
                newChunks.size()
        );

        List<RetrievalResult> fused = newChunks.isEmpty()
                ? safeChunks
                : FusionUtils.rrfFusionRetrieval(List.of(newChunks, safeChunks), 60);
        return limit(fused, topk);
    }

    public Retriever getRetrieverForMode(String mode, boolean isChunk) {
        String actualMode = normalizeMode(mode);
        ensureModeAllowed(actualMode);
        Retriever fixedRetriever = isChunk ? chunkRetriever : tripleRetriever;
        if (fixedRetriever != null) {
            if (!fixedRetriever.supportsMode(actualMode)) {
                throw ErrorHelper.buildError(
                        StatusCode.RETRIEVAL_RETRIEVER_CAPABILITY_NOT_SUPPORT,
                        "error_msg",
                        "Provided " + (isChunk ? "chunk" : "triple") + " retriever "
                                + fixedRetriever.getClass().getSimpleName()
                                + " does not support mode=" + actualMode
                );
            }
            return fixedRetriever;
        }

        if (vectorStore == null) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_RETRIEVER_VECTOR_STORE_NOT_FOUND,
                    "error_msg",
                    "vector_store is required for dynamic retriever creation"
            );
        }

        String collectionName = isChunk ? chunkCollection : tripleCollection;
        if (collectionName == null || collectionName.isBlank()) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_RETRIEVER_COLLECTION_NOT_FOUND,
                    "error_msg",
                    (isChunk ? "chunk" : "triple") + "_collection is required for dynamic retriever creation"
            );
        }

        Retriever retriever;
        if (MODE_VECTOR.equals(actualMode)) {
            if (embedModel == null) {
                throw ErrorHelper.buildError(
                        StatusCode.RETRIEVAL_RETRIEVER_EMBED_MODEL_NOT_FOUND,
                        "error_msg",
                        "embed_model is required for vector mode"
                );
            }
            retriever = new VectorRetriever(vectorStore, embedModel);
        } else if (MODE_SPARSE.equals(actualMode)) {
            retriever = new SparseRetriever(vectorStore);
        } else {
            retriever = new HybridRetriever(vectorStore, embedModel);
        }

        return new GraphScopedRetriever(retriever, collectionName, isChunk ? "chunk" : "triple", indexType, embedModel);
    }

    @Override
    public void close() {
        if (chunkRetriever != null) {
            chunkRetriever.close();
        }
        if (tripleRetriever != null) {
            tripleRetriever.close();
        }
    }

    protected List<RetrievalResult> fetchTriples(
            List<RetrievalResult> chunks,
            String mode,
            Map<String, Object> options
    ) {
        Set<String> chunkIds = collectChunkIds(chunks);
        if (chunkIds.isEmpty()) {
            return List.of();
        }

        Retriever retriever = getRetrieverForMode(mode, false);
        Map<String, Object> retrievalOptions = new LinkedHashMap<>(optionsOrEmpty(options));
        retrievalOptions.put("chunk_ids", List.copyOf(chunkIds));
        List<RetrievalResult> candidates = retriever.retrieve(
                String.join(" ", chunkIds),
                100,
                null,
                normalizeMode(mode),
                retrievalOptions
        );
        return filterByChunkIds(candidates, chunkIds);
    }

    protected List<RetrievalResult> fetchChunks(
            List<RetrievalResult> triples,
            String mode,
            Map<String, Object> options
    ) {
        Set<String> chunkIds = collectChunkIds(triples);
        if (chunkIds.isEmpty()) {
            return List.of();
        }

        Retriever retriever = getRetrieverForMode(mode, true);
        Map<String, Object> retrievalOptions = new LinkedHashMap<>(optionsOrEmpty(options));
        retrievalOptions.put("chunk_ids", List.copyOf(chunkIds));
        List<RetrievalResult> candidates = retriever.retrieve(
                String.join(" ", chunkIds),
                chunkIds.size(),
                null,
                normalizeMode(mode),
                retrievalOptions
        );
        return filterByChunkIds(candidates, chunkIds);
    }

    private void ensureModeAllowed(String mode) {
        if (indexType == null) {
            return;
        }
        Map<String, Set<String>> allowedModes = allowedModes();
        Set<String> allowed = allowedModes.get(indexType);
        if (allowed == null) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_RETRIEVER_INDEX_TYPE_NOT_SUPPORT,
                    "error_msg",
                    "Unsupported index_type=" + indexType
            );
        }
        if (!allowed.contains(mode)) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_RETRIEVER_MODE_INVALID,
                    "error_msg",
                    "mode=" + mode + " is incompatible with index_type=" + indexType
                            + "; allowed modes: " + allowed.stream().sorted().toList()
            );
        }
    }

    private static Map<String, Set<String>> allowedModes() {
        return Map.of(
                MODE_VECTOR, Set.of(MODE_VECTOR),
                MODE_BM25, Set.of(MODE_SPARSE),
                MODE_HYBRID, Set.of(MODE_VECTOR, MODE_SPARSE, MODE_HYBRID)
        );
    }

    private static Set<String> collectChunkIds(List<RetrievalResult> results) {
        Set<String> chunkIds = new LinkedHashSet<>();
        if (results == null) {
            return chunkIds;
        }
        for (RetrievalResult result : results) {
            if (result == null) {
                continue;
            }
            if (result.getChunkId() != null && !result.getChunkId().isBlank()) {
                chunkIds.add(result.getChunkId());
                continue;
            }
            Object metadataChunkId = result.getMetadata().get("chunk_id");
            if (metadataChunkId != null && !String.valueOf(metadataChunkId).isBlank()) {
                chunkIds.add(String.valueOf(metadataChunkId));
            }
        }
        return chunkIds;
    }

    private static List<RetrievalResult> filterByChunkIds(List<RetrievalResult> candidates, Set<String> chunkIds) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<RetrievalResult> filtered = new ArrayList<>();
        for (RetrievalResult candidate : candidates) {
            String chunkId = candidate.getChunkId();
            Object metadataChunkId = candidate.getMetadata().get("chunk_id");
            if (chunkId != null && chunkIds.contains(chunkId)) {
                filtered.add(candidate);
                continue;
            }
            if (metadataChunkId != null && chunkIds.contains(String.valueOf(metadataChunkId))) {
                filtered.add(candidate);
            }
        }
        return List.copyOf(filtered);
    }

    private static List<RetrievalResult> limit(List<RetrievalResult> results, Integer topk) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        if (topk == null || topk >= results.size()) {
            return List.copyOf(results);
        }
        return List.copyOf(results.subList(0, Math.max(topk, 0)));
    }

    private static String normalizeMode(String mode) {
        return mode == null || mode.isBlank() ? MODE_HYBRID : mode;
    }

    private static Map<String, Object> optionsOrEmpty(Map<String, Object> options) {
        return options == null ? Map.of() : options;
    }

    private static int optionAsInt(Map<String, Object> options, String key, int defaultValue) {
        Object value = options.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return defaultValue;
    }

    /**
     * Mirrors Python's dynamic retriever attribute injection in
     * {@code openjiuwen/core/retrieval/retriever/graph_retriever.py}.
     */
    private static final class GraphScopedRetriever implements Retriever, RetrieverEmbeddingProvider {

        private final Retriever delegate;
        private final String collectionName;
        private final String collectionType;
        private final String indexType;
        private final Embedding embedModel;

        private GraphScopedRetriever(
                Retriever delegate,
                String collectionName,
                String collectionType,
                String indexType,
                Embedding embedModel
        ) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.collectionName = collectionName;
            this.collectionType = collectionType;
            this.indexType = indexType;
            this.embedModel = embedModel;
        }

        @Override
        public List<RetrievalResult> retrieve(
                String query,
                int topK,
                Double scoreThreshold,
                String mode,
                Map<String, Object> options
        ) {
            return delegate.retrieve(query, topK, scoreThreshold, mode, scopedOptions(options));
        }

        @Override
        public List<List<RetrievalResult>> batchRetrieve(
                List<String> queries,
                int topK,
                String mode,
                Map<String, Object> options
        ) {
            return delegate.batchRetrieve(queries, topK, mode, scopedOptions(options));
        }

        @Override
        public boolean supportsMode(String mode) {
            return delegate.supportsMode(mode);
        }

        @Override
        public String getIndexType() {
            return indexType;
        }

        @Override
        public Embedding getEmbedModel() {
            return embedModel;
        }

        @Override
        public void close() {
            delegate.close();
        }

        private Map<String, Object> scopedOptions(Map<String, Object> options) {
            Map<String, Object> scoped = new LinkedHashMap<>(optionsOrEmpty(options));
            scoped.putIfAbsent("collection_name", collectionName);
            scoped.putIfAbsent("collection_type", collectionType);
            return scoped;
        }
    }
}

/**
 * Triple beam search.
 * <p>
 * Mirrors Python's {@code TripleBeamSearch} in
 * {@code openjiuwen/core/retrieval/retriever/graph_retriever.py}.
 * </p>
 */
class TripleBeamSearch {

    private static final Logger LOGGER = LoggerFactory.getLogger(TripleBeamSearch.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Object>> OBJECT_LIST = new TypeReference<>() {
    };
    private static final double EPSILON = 1e-12d;

    private final Retriever retriever;
    private final int numBeams;
    private final int numCandidatesPerBeam;
    private final int maxLength;
    private final int encoderBatchSize;
    private final Embedding embedModel;
    private final String retrieveMode;

    TripleBeamSearch(Retriever retriever) {
        this(retriever, 10, 100, 2, 256, null);
    }

    TripleBeamSearch(
            Retriever retriever,
            int numBeams,
            int numCandidatesPerBeam,
            int maxLength,
            int encoderBatchSize,
            String retrieveMode
    ) {
        if (maxLength < 1) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_RETRIEVER_MODE_INVALID,
                    "error_msg",
                    "expect max_length >= 1; got max_length=" + maxLength
            );
        }
        this.retriever = Objects.requireNonNull(retriever, "retriever");
        this.numBeams = numBeams;
        this.numCandidatesPerBeam = numCandidatesPerBeam;
        this.maxLength = maxLength;
        this.encoderBatchSize = encoderBatchSize;
        this.embedModel = resolveEmbedModel(retriever);
        this.retrieveMode = retrieveMode;
    }

    String getRetrieveMode() {
        return retrieveMode;
    }

    List<TripleBeam> beamSearch(String query, List<RetrievalResult> triples) {
        if (triples == null || triples.isEmpty()) {
            LOGGER.warn("beam search got empty input triples, query={}", query);
            return List.of();
        }
        if (embedModel == null) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_RETRIEVER_EMBED_MODEL_NOT_FOUND,
                    "error_msg",
                    "embed_model is required for beam search"
            );
        }

        List<String> texts = new ArrayList<>(triples.size() + 1);
        for (RetrievalResult triple : triples) {
            texts.add(triple.getText());
        }
        texts.add(query);

        List<List<Double>> embeddings = await(embedModel.embedDocuments(texts, encoderBatchSize));
        if (embeddings.size() != texts.size()) {
            throw new IllegalStateException("embedding count does not match input text count");
        }
        List<Double> queryEmbedding = embeddings.getLast();
        List<List<Double>> candidateEmbeddings = embeddings.subList(0, embeddings.size() - 1);

        double[] scores = cosineScores(queryEmbedding, candidateEmbeddings);
        List<ScoredIndex> topk = topK(scores, numBeams);
        List<TripleBeam> beams = new ArrayList<>();
        for (ScoredIndex scoredIndex : topk) {
            beams.add(new TripleBeam(List.of(triples.get(scoredIndex.index())), scoredIndex.score()));
        }

        for (int index = 0; index < maxLength - 1; index++) {
            List<List<RetrievalResult>> candidatesPerBeam = new ArrayList<>(beams.size());
            for (TripleBeam beam : beams) {
                candidatesPerBeam.add(searchCandidates(beam));
            }
            beams = expandBeams(queryEmbedding, beams, candidatesPerBeam);
        }

        return List.copyOf(beams);
    }

    List<RetrievalResult> searchCandidates(TripleBeam beam) {
        if (beam.size() < 1) {
            throw new IllegalStateException("unexpected empty beam");
        }

        Object tripleData = beam.get(beam.size() - 1).getMetadata().get("triple");
        List<String> triple = parseTripleSafe(tripleData);
        if (triple.isEmpty()) {
            LOGGER.warn("beam has no triple metadata");
            return List.of();
        }
        if (triple.size() < 2) {
            return List.of();
        }

        Set<String> entities = new LinkedHashSet<>();
        entities.add(triple.getFirst());
        entities.add(triple.getLast());
        String query = String.join(" ", entities);

        String mode = resolveRetrieverIndexType(retriever);
        if (mode == null) {
            mode = retrieveMode;
        }
        if (mode == null) {
            mode = "hybrid";
        }
        if ("bm25".equals(mode)) {
            mode = "sparse";
        }

        List<RetrievalResult> nodes = retriever.retrieve(
                query,
                numCandidatesPerBeam,
                null,
                mode,
                Map.of()
        );
        List<RetrievalResult> results = new ArrayList<>();
        for (RetrievalResult node : nodes) {
            if (beam.contains(node)) {
                continue;
            }
            List<String> nodeTriple = parseTripleStrict(node.getMetadata().get("triple"));
            if (nodeTriple.size() < 2) {
                continue;
            }
            if (!entities.contains(nodeTriple.getFirst()) && !entities.contains(nodeTriple.getLast())) {
                continue;
            }
            results.add(node);
        }

        if (results.isEmpty()) {
            LOGGER.warn("empty candidates for beam: {}", formatTriples(beam.getTriples()));
        }
        return List.copyOf(results);
    }

    private List<TripleBeam> expandBeams(
            List<Double> queryEmbedding,
            List<TripleBeam> beams,
            List<List<RetrievalResult>> candidatesPerBeam
    ) {
        List<String> texts = new ArrayList<>();
        List<CandidatePath> candidatePaths = new ArrayList<>();
        Set<String> existTriples = new LinkedHashSet<>();
        for (TripleBeam beam : beams) {
            for (RetrievalResult result : beam) {
                existTriples.add(result.getText());
            }
        }

        for (int index = 0; index < beams.size(); index++) {
            TripleBeam beam = beams.get(index);
            List<RetrievalResult> candidates = candidatesPerBeam.get(index);
            if (candidates == null || candidates.isEmpty()) {
                candidatePaths.add(new CandidatePath(beam, null));
                texts.add(formatTriples(beam.getTriples()));
                continue;
            }
            for (RetrievalResult triple : candidates) {
                if (existTriples.contains(triple.getText())) {
                    continue;
                }
                candidatePaths.add(new CandidatePath(beam, triple));
                List<RetrievalResult> newTriples = new ArrayList<>(beam.getTriples());
                newTriples.add(triple);
                texts.add(formatTriples(newTriples));
            }
        }

        if (texts.isEmpty() || embedModel == null) {
            return List.copyOf(beams);
        }

        List<List<Double>> embeddings = await(embedModel.embedDocuments(texts, encoderBatchSize));
        double[] nextScores = cosineScores(queryEmbedding, embeddings);
        List<ScoredIndex> topk = topK(nextScores, numBeams);

        List<TripleBeam> expandedBeams = new ArrayList<>();
        Set<String> seenBeamTexts = new LinkedHashSet<>();
        for (ScoredIndex scoredIndex : topk) {
            CandidatePath candidatePath = candidatePaths.get(scoredIndex.index());
            TripleBeam beam = candidatePath.beam();
            RetrievalResult nextTriple = candidatePath.nextTriple();
            if (nextTriple == null) {
                String beamText = formatTriples(beam.getTriples());
                if (seenBeamTexts.add(beamText)) {
                    expandedBeams.add(beam);
                }
                continue;
            }

            List<RetrievalResult> newTriples = new ArrayList<>(beam.getTriples());
            newTriples.add(nextTriple);
            String beamText = formatTriples(newTriples);
            if (seenBeamTexts.add(beamText)) {
                expandedBeams.add(new TripleBeam(newTriples, scoredIndex.score()));
            }
        }

        return List.copyOf(expandedBeams);
    }

    private static double[] cosineScores(List<Double> queryVector, List<List<Double>> candidateVectors) {
        double[] scores = new double[candidateVectors.size()];
        double queryNorm = norm(queryVector) + EPSILON;
        for (int index = 0; index < candidateVectors.size(); index++) {
            List<Double> candidateVector = candidateVectors.get(index);
            scores[index] = dot(queryVector, candidateVector) / (queryNorm * (norm(candidateVector) + EPSILON));
        }
        return scores;
    }

    private static double dot(List<Double> left, List<Double> right) {
        if (left.size() != right.size()) {
            throw new IllegalArgumentException("embedding dimensions do not match");
        }
        double value = 0.0d;
        for (int index = 0; index < left.size(); index++) {
            value += left.get(index) * right.get(index);
        }
        return value;
    }

    private static double norm(List<Double> vector) {
        double squared = 0.0d;
        for (Double value : vector) {
            squared += value * value;
        }
        return Math.sqrt(squared);
    }

    private static List<ScoredIndex> topK(double[] scores, int k) {
        int actualK = Math.min(k, scores.length);
        if (actualK <= 0) {
            return List.of();
        }
        List<ScoredIndex> indexes = new ArrayList<>(scores.length);
        for (int index = 0; index < scores.length; index++) {
            indexes.add(new ScoredIndex(index, scores[index]));
        }
        indexes.sort(Comparator.comparingDouble(ScoredIndex::score).reversed());
        return List.copyOf(indexes.subList(0, actualK));
    }

    private static String formatTriples(List<RetrievalResult> triples) {
        List<String> texts = new ArrayList<>();
        for (RetrievalResult triple : triples) {
            texts.add(triple.getText());
        }
        return String.join("; ", texts);
    }

    private static List<String> parseTripleSafe(Object value) {
        if (value == null) {
            return List.of();
        }
        try {
            return parseTripleStrict(value);
        } catch (RuntimeException exception) {
            LOGGER.warn("[graph] Failed to parse triple metadata: {}", exception.getMessage());
            return List.of();
        }
    }

    private static List<String> parseTripleStrict(Object value) {
        if (!(value instanceof CharSequence text)) {
            throw new IllegalArgumentException("triple metadata must be a JSON string");
        }
        try {
            List<Object> raw = OBJECT_MAPPER.readValue(text.toString(), OBJECT_LIST);
            List<String> parsed = new ArrayList<>(raw.size());
            for (Object item : raw) {
                parsed.add(String.valueOf(item));
            }
            return List.copyOf(parsed);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(exception.getMessage(), exception);
        }
    }

    private static Embedding resolveEmbedModel(Retriever retriever) {
        if (retriever instanceof RetrieverEmbeddingProvider provider) {
            return provider.getEmbedModel();
        }
        try {
            Method method = retriever.getClass().getMethod("getEmbedModel");
            Object value = method.invoke(retriever);
            if (value instanceof Embedding embedding) {
                return embedding;
            }
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
        return null;
    }

    private static String resolveRetrieverIndexType(Retriever retriever) {
        try {
            Method method = retriever.getClass().getMethod("getIndexType");
            if (Retriever.class.equals(method.getDeclaringClass())) {
                return null;
            }
            Object value = method.invoke(retriever);
            return value == null ? null : String.valueOf(value);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static <T> T await(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw exception;
        }
    }

    /**
     * Mirrors Python's {@code candidate_paths} tuples in
     * {@code openjiuwen/core/retrieval/retriever/graph_retriever.py}.
     */
    private record CandidatePath(TripleBeam beam, RetrievalResult nextTriple) {
    }

    /**
     * Mirrors Python's top-k index and score pair in
     * {@code openjiuwen/core/retrieval/retriever/graph_retriever.py}.
     */
    private record ScoredIndex(int index, double score) {
    }
}

/**
 * Mirrors Python's dynamic {@code embed_model} attribute lookup in
 * {@code openjiuwen/core/retrieval/retriever/graph_retriever.py}.
 */
interface RetrieverEmbeddingProvider {

    Embedding getEmbedModel();
}
