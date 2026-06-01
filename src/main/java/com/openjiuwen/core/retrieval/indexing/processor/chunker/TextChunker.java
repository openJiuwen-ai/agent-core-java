/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import com.openjiuwen.core.retrieval.common.TextChunk;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Composite chunker with preprocessing.
 *
 * <p>Mirrors Python's {@code TextChunker} in
 * {@code openjiuwen.core.retrieval.indexing.processor.chunker.chunking}.</p>
 */
public class TextChunker extends Chunker {

    private final Chunker chunker;
    private final PreprocessingPipeline pipeline;

    public TextChunker(int chunkSize, int chunkOverlap) {
        this(chunkSize, chunkOverlap, "char", (Object) null, (Map<String, Object>) null);
    }

    public TextChunker(int chunkSize, int chunkOverlap, String chunkUnit) {
        this(chunkSize, chunkOverlap, chunkUnit, (Object) null, (Map<String, Object>) null);
    }

    public TextChunker(int chunkSize,
                       int chunkOverlap,
                       String chunkUnit,
                       Map<String, Object> preprocessOptions) {
        this(chunkSize, chunkOverlap, chunkUnit, (Object) null, preprocessOptions);
    }

    public TextChunker(int chunkSize,
                       int chunkOverlap,
                       String chunkUnit,
                       Object embedModel,
                       Map<String, Object> preprocessOptions) {
        this(chunkSize, chunkOverlap, chunkUnit, embedModel, preprocessOptions, true);
    }

    public TextChunker(int chunkSize,
                       int chunkOverlap,
                       String chunkUnit,
                       Object embedModel,
                       Map<String, Object> preprocessOptions,
                       boolean tokenFallbackAvailable) {
        super(chunkSize, chunkOverlap);
        this.pipeline = new PreprocessingPipeline(buildPreprocessors(preprocessOptions));
        this.chunker = getChunker(chunkSize, chunkOverlap, chunkUnit, embedModel, tokenFallbackAvailable);
    }

    public TextChunker(int chunkSize,
                       int chunkOverlap,
                       String chunkUnit,
                       Function<String, List<String>> tokenizer,
                       String language) {
        super(chunkSize, chunkOverlap);
        this.chunker = isCharUnit(chunkUnit)
                ? new CharChunker(chunkSize, chunkOverlap)
                : new TokenizerChunker(chunkSize, chunkOverlap, tokenizer, language, null);
        this.pipeline = new PreprocessingPipeline(List.of());
    }

    @Override
    public List<String> chunkText(String text) {
        return chunker.chunkText(pipeline.process(text));
    }

    @Override
    public List<TextChunk> chunkDocuments(List<Document> documents) {
        List<Document> normalized = new ArrayList<>();
        if (documents != null) {
            for (Document document : documents) {
                normalized.add(new Document(document.getId(), pipeline.process(document.getText()), document.getMetadata()));
            }
        }
        return chunker.chunkDocuments(normalized);
    }

    public Chunker getChunker() {
        return chunker;
    }

    public PreprocessingPipeline getPipeline() {
        return pipeline;
    }

    public Chunker getChunker(int chunkSize, int chunkOverlap, String chunkUnit, Object embedModel) {
        return getChunker(chunkSize, chunkOverlap, chunkUnit, embedModel, true);
    }

    public Chunker getChunker(int chunkSize,
                              int chunkOverlap,
                              String chunkUnit,
                              Object embedModel,
                              boolean tokenFallbackAvailable) {
        if (isCharUnit(chunkUnit)) {
            return new CharChunker(chunkSize, chunkOverlap);
        }

        Object tokenizer = extractTokenizer(embedModel);
        if (tokenizer == null && !tokenFallbackAvailable) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_INDEXING_TOKENIZER_PROCESS_ERROR,
                    "chunk_unit='token' requires embed_model with tokenizer or tiktoken to be installed");
        }

        int effectiveChunkSize = adjustedChunkSize(chunkSize, tokenizer);
        return new TokenizerChunker(effectiveChunkSize, chunkOverlap, tokenizerFunction(tokenizer));
    }

    private static List<TextPreprocessor> buildPreprocessors(Map<String, Object> preprocessOptions) {
        List<TextPreprocessor> preprocessors = new ArrayList<>();
        Map<String, Object> options = preprocessOptions == null ? Map.of() : preprocessOptions;
        if (isTruthy(options.get("normalize_whitespace"))) {
            preprocessors.add(new WhitespaceNormalizer());
        }
        if (isTruthy(options.get("remove_url_email"))) {
            preprocessors.add(new URLEmailRemover());
        }
        return preprocessors;
    }

    private static boolean isCharUnit(String chunkUnit) {
        return chunkUnit == null || "char".equalsIgnoreCase(chunkUnit);
    }

    private static boolean isTruthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && !"false".equalsIgnoreCase(value.toString()) && !"0".equals(value.toString());
    }

    private static Object extractTokenizer(Object embedModel) {
        if (embedModel == null) {
            return null;
        }
        if (embedModel instanceof Map<?, ?> map) {
            return map.get("tokenizer");
        }
        Object value = readField(embedModel, "tokenizer");
        if (value != null) {
            return value;
        }
        return invokeNoArg(embedModel, "getTokenizer");
    }

    private static int adjustedChunkSize(int chunkSize, Object tokenizer) {
        Integer modelMaxLength = readModelMaxLength(tokenizer);
        if (modelMaxLength != null && chunkSize > modelMaxLength) {
            return modelMaxLength;
        }
        return chunkSize;
    }

    @SuppressWarnings("unchecked")
    private static Function<String, List<String>> tokenizerFunction(Object tokenizer) {
        if (tokenizer instanceof Function<?, ?> function) {
            return (Function<String, List<String>>) function;
        }
        return null;
    }

    private static Integer readModelMaxLength(Object tokenizer) {
        if (tokenizer == null) {
            return null;
        }
        if (tokenizer instanceof Map<?, ?> map) {
            Object value = map.containsKey("model_max_length") ? map.get("model_max_length") : map.get("modelMaxLength");
            return toFiniteInteger(value);
        }
        Object value = readField(tokenizer, "model_max_length");
        if (value == null) {
            value = readField(tokenizer, "modelMaxLength");
        }
        if (value == null) {
            value = invokeNoArg(tokenizer, "getModelMaxLength");
        }
        if (value == null) {
            value = invokeNoArg(tokenizer, "modelMaxLength");
        }
        return toFiniteInteger(value);
    }

    private static Object readField(Object target, String name) {
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            } catch (IllegalAccessException | RuntimeException e) {
                return null;
            }
        }
        return null;
    }

    private static Object invokeNoArg(Object target, String name) {
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }
        return null;
    }

    private static Integer toFiniteInteger(Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        double asDouble = number.doubleValue();
        if (!Double.isFinite(asDouble)) {
            return null;
        }
        return number.intValue();
    }
}
