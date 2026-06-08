/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.indexer;

import com.openjiuwen.core.retrieval.common.BaseCallback;
import com.openjiuwen.core.retrieval.common.MultimodalDocument;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.embedding.Embedding;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Mirrors Python's {@code compute_chunk_embeddings} in
 * {@code openjiuwen/core/retrieval/indexing/indexer/embed_chunks.py}.
 */
public final class EmbedChunks {

    private EmbedChunks() {
    }

    public static CompletableFuture<Void> computeChunkEmbeddings(
            List<TextChunk> chunks,
            Embedding embedModel,
            Class<? extends BaseCallback> docIndexCallback,
            boolean useCaptionForImages) {
        Method embedMultimodal = findEmbedMultimodalMethod(embedModel);
        if (embedMultimodal == null || useCaptionForImages) {
            return embedTextOnly(chunks, embedModel, docIndexCallback);
        }

        List<Integer> imageIndices = new ArrayList<>();
        List<IndexedChunk> textOnly = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            TextChunk chunk = chunks.get(i);
            String imagePath = resolveImagePath(chunk);
            if (imagePath != null && Files.isRegularFile(Path.of(imagePath))) {
                imageIndices.add(i);
            } else {
                textOnly.add(new IndexedChunk(i, chunk));
            }
        }

        CompletableFuture<Void> imageFuture = CompletableFuture.completedFuture(null);
        for (Integer index : imageIndices) {
            imageFuture = imageFuture.thenCompose(ignored -> {
                TextChunk chunk = chunks.get(index);
                MultimodalDocument multimodalDocument = new MultimodalDocument()
                        .addField("text", chunk.getText() == null ? "" : chunk.getText())
                        .addField("image", Path.of(resolveImagePath(chunk)));
                return invokeEmbedMultimodal(embedModel, embedMultimodal, multimodalDocument)
                        .thenAccept(chunk::setEmbedding);
            });
        }

        return imageFuture.thenCompose(ignored -> {
            if (textOnly.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            List<String> texts = textOnly.stream()
                    .map(indexedChunk -> indexedChunk.chunk().getText())
                    .toList();
            return embedDocuments(chunks, embedModel, docIndexCallback, textOnly, texts);
        });
    }

    public static CompletableFuture<Void> computeChunkEmbeddings(
            List<TextChunk> chunks,
            Embedding embedModel) {
        return computeChunkEmbeddings(chunks, embedModel, null, false);
    }

    private static CompletableFuture<Void> embedTextOnly(
            List<TextChunk> chunks,
            Embedding embedModel,
            Class<? extends BaseCallback> docIndexCallback) {
        List<String> texts = chunks.stream().map(TextChunk::getText).toList();
        List<IndexedChunk> indexedChunks = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            indexedChunks.add(new IndexedChunk(i, chunks.get(i)));
        }
        return embedDocuments(chunks, embedModel, docIndexCallback, indexedChunks, texts);
    }

    private static CompletableFuture<Void> embedDocuments(
            List<TextChunk> chunks,
            Embedding embedModel,
            Class<? extends BaseCallback> docIndexCallback,
            List<IndexedChunk> indexedChunks,
            List<String> texts) {
        Map<String, Object> kwargs = docIndexCallback == null
                ? Map.of()
                : Map.of("callback_cls", docIndexCallback);
        return embedModel.embedDocuments(texts, null, kwargs)
                .thenAccept(embeddings -> {
                    for (int i = 0; i < indexedChunks.size(); i++) {
                        chunks.get(indexedChunks.get(i).index()).setEmbedding(embeddings.get(i));
                    }
                });
    }

    private static CompletableFuture<List<Double>> invokeEmbedMultimodal(
            Embedding embedModel,
            Method method,
            MultimodalDocument document) {
        try {
            Object result;
            if (method.getParameterCount() == 1) {
                result = method.invoke(embedModel, document);
            } else {
                result = method.invoke(embedModel, document, Map.of());
            }
            if (result instanceof CompletableFuture<?> future) {
                return future.thenApply(EmbedChunks::coerceEmbeddingList);
            }
            return CompletableFuture.completedFuture(coerceEmbeddingList(result));
        } catch (IllegalAccessException | InvocationTargetException ex) {
            throw new CompletionException(ex.getCause() == null ? ex : ex.getCause());
        }
    }

    private static List<Double> coerceEmbeddingList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            throw new IllegalStateException("embedMultimodal must return List<?> or CompletableFuture<List<?>>");
        }
        List<Double> converted = new ArrayList<>(rawList.size());
        for (Object item : rawList) {
            if (!(item instanceof Number number)) {
                throw new IllegalStateException("Embedding vector values must be numeric");
            }
            converted.add(number.doubleValue());
        }
        return converted;
    }

    private static Method findEmbedMultimodalMethod(Embedding embedModel) {
        Method singleArg = null;
        Method twoArg = null;
        for (Method method : embedModel.getClass().getMethods()) {
            if (!"embedMultimodal".equals(method.getName())) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length == 1
                    && parameterTypes[0].isAssignableFrom(MultimodalDocument.class)) {
                singleArg = method;
            } else if (parameterTypes.length == 2
                    && parameterTypes[0].isAssignableFrom(MultimodalDocument.class)
                    && Map.class.isAssignableFrom(parameterTypes[1])) {
                twoArg = method;
            } else if (parameterTypes.length == 1 && parameterTypes[0] == Object.class) {
                singleArg = method;
            } else if (parameterTypes.length == 2
                    && parameterTypes[0] == Object.class
                    && Map.class.isAssignableFrom(parameterTypes[1])) {
                twoArg = method;
            }
        }
        return singleArg != null ? singleArg : twoArg;
    }

    private static String resolveImagePath(TextChunk chunk) {
        Map<String, Object> metadata = chunk.getMetadata();
        Object imagePath = metadata.get("image_path");
        return imagePath instanceof String path ? path : null;
    }

    private record IndexedChunk(int index, TextChunk chunk) {
    }
}
