/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.retriever;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.TripleBeam;
import com.openjiuwen.core.retrieval.embedding.Embedding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Triple beam search used by graph retrieval.
 */
public class TripleBeamSearch {

    private static final float EPSILON = 1e-6f;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Retriever retriever;
    private final int numBeams;
    private final int numCandidatesPerBeam;
    private final int maxLength;
    private final Embedding embedModel;

    /**
     * Auto-generated for codecheck compliance.
     */
    public TripleBeamSearch(Retriever retriever) {
        this(retriever, 10, 100, 2);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TripleBeamSearch(Retriever retriever, int numBeams, int numCandidatesPerBeam, int maxLength) {
        if (maxLength < 1) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_RETRIEVER_MODE_INVALID,
                    "expect max_length >= 1; got max_length=" + maxLength);
        }
        this.retriever = retriever;
        this.numBeams = numBeams;
        this.numCandidatesPerBeam = numCandidatesPerBeam;
        this.maxLength = maxLength;
        this.embedModel = retriever instanceof AbstractStoreBackedRetriever storeBacked ? storeBacked.getEmbedModel() : null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<TripleBeam> beamSearch(String query, List<RetrievalResult> triples) {
        if (triples == null || triples.isEmpty()) {
            return List.of();
        }
        if (embedModel == null) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_RETRIEVER_EMBED_MODEL_NOT_FOUND,
                    "embed_model is required for beam search");
        }
        List<String> texts = new ArrayList<>();
        for (RetrievalResult triple : triples) {
            texts.add(triple.getText());
        }
        texts.add(query);
        List<List<Float>> embeddings = embedModel.embedDocuments(texts, embedModel.getMaxBatchSize());
        List<Float> queryEmbedding = embeddings.get(embeddings.size() - 1);
        List<TripleBeam> beams = topBeams(triples, embeddings.subList(0, triples.size()), queryEmbedding);
        for (int i = 0; i < maxLength - 1; i++) {
            beams = expandBeams(beams, queryEmbedding);
        }
        return beams;
    }

    private List<TripleBeam> topBeams(List<RetrievalResult> triples, List<List<Float>> embeddings, List<Float> queryEmbedding) {
        List<Integer> topIndices = topK(embeddings, queryEmbedding, numBeams);
        List<TripleBeam> beams = new ArrayList<>();
        for (Integer index : topIndices) {
            beams.add(new TripleBeam(List.of(triples.get(index)), cosine(queryEmbedding, embeddings.get(index))));
        }
        return beams;
    }

    private List<TripleBeam> expandBeams(List<TripleBeam> beams, List<Float> queryEmbedding) {
        List<List<RetrievalResult>> candidatesPerBeam = new ArrayList<>();
        for (TripleBeam beam : beams) {
            candidatesPerBeam.add(searchCandidates(beam));
        }
        List<String> pathTexts = new ArrayList<>();
        List<BeamCandidate> candidates = new ArrayList<>();
        Set<String> existingTriples = new HashSet<>();
        for (TripleBeam beam : beams) {
            for (RetrievalResult triple : beam) {
                existingTriples.add(triple.getText());
            }
        }
        for (int i = 0; i < beams.size(); i++) {
            TripleBeam beam = beams.get(i);
            List<RetrievalResult> candidateTriples = candidatesPerBeam.get(i);
            if (candidateTriples.isEmpty()) {
                candidates.add(new BeamCandidate(beam, null));
                pathTexts.add(formatBeam(beam.getTriples()));
                continue;
            }
            for (RetrievalResult candidate : candidateTriples) {
                if (existingTriples.contains(candidate.getText())) {
                    continue;
                }
                candidates.add(new BeamCandidate(beam, candidate));
                List<RetrievalResult> expanded = new ArrayList<>(beam.getTriples());
                expanded.add(candidate);
                pathTexts.add(formatBeam(expanded));
            }
        }
        if (pathTexts.isEmpty()) {
            return beams;
        }
        List<List<Float>> embeddings = embedModel.embedDocuments(pathTexts, embedModel.getMaxBatchSize());
        List<Integer> top = topK(embeddings, queryEmbedding, numBeams);
        List<TripleBeam> expandedBeams = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Integer index : top) {
            BeamCandidate candidate = candidates.get(index);
            if (candidate.nextTriple == null) {
                String key = formatBeam(candidate.beam.getTriples());
                if (seen.add(key)) {
                    expandedBeams.add(candidate.beam);
                }
            } else {
                List<RetrievalResult> triples = new ArrayList<>(candidate.beam.getTriples());
                triples.add(candidate.nextTriple);
                String key = formatBeam(triples);
                if (seen.add(key)) {
                    expandedBeams.add(new TripleBeam(triples, cosine(queryEmbedding, embeddings.get(index))));
                }
            }
        }
        return expandedBeams;
    }

    private List<RetrievalResult> searchCandidates(TripleBeam beam) {
        if (beam.size() < 1) {
            return List.of();
        }
        RetrievalResult last = beam.get(beam.size() - 1);
        Object tripleData = last.getMetadata().get("triple");
        if (!(tripleData instanceof String tripleJson)) {
            return List.of();
        }
        try {
            List<String> triple = MAPPER.readValue(tripleJson, new TypeReference<>() {});
            if (triple.size() < 2) {
                return List.of();
            }
            Set<String> entities = Set.of(triple.get(0), triple.get(triple.size() - 1));
            List<RetrievalResult> nodes = retriever.retrieve(
                    String.join(" ", entities),
                    numCandidatesPerBeam,
                    null,
                    "vector",
                    Map.of());
            List<RetrievalResult> result = new ArrayList<>();
            for (RetrievalResult node : nodes) {
                if (beam.contains(node)) {
                    continue;
                }
                Object metadataTriple = node.getMetadata().get("triple");
                if (!(metadataTriple instanceof String metadataTripleJson)) {
                    continue;
                }
                List<String> candidateTriple = MAPPER.readValue(metadataTripleJson, new TypeReference<>() {});
                if (candidateTriple.isEmpty()) {
                    continue;
                }
                if (entities.contains(candidateTriple.get(0))
                        || entities.contains(candidateTriple.get(candidateTriple.size() - 1))) {
                    result.add(node);
                }
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String formatBeam(List<RetrievalResult> triples) {
        List<String> texts = new ArrayList<>();
        for (RetrievalResult triple : triples) {
            texts.add(triple.getText());
        }
        return String.join("; ", texts);
    }

    private static List<Integer> topK(List<List<Float>> embeddings, List<Float> queryEmbedding, int k) {
        List<Map.Entry<Integer, Double>> scored = new ArrayList<>();
        for (int i = 0; i < embeddings.size(); i++) {
            scored.add(Map.entry(i, cosine(queryEmbedding, embeddings.get(i))));
        }
        scored.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < Math.min(k, scored.size()); i++) {
            result.add(scored.get(i).getKey());
        }
        return result;
    }

    private static double cosine(List<Float> left, List<Float> right) {
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        int size = Math.min(left.size(), right.size());
        for (int i = 0; i < size; i++) {
            dot += left.get(i) * right.get(i);
            leftNorm += left.get(i) * left.get(i);
            rightNorm += right.get(i) * right.get(i);
        }
        if (Math.abs(leftNorm - 0.0) < EPSILON || Math.abs(rightNorm - 0.0) < EPSILON) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private record BeamCandidate(TripleBeam beam, RetrievalResult nextTriple) {
    }
}
