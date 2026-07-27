/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Mirrors Python's {@code AutoFileParser} in
 * {@code openjiuwen/core/retrieval/indexing/processor/parser/auto_file_parser.py}.
 */
public class AutoFileParser extends Parser {

    private static final Logger LOGGER = Logger.getLogger(AutoFileParser.class.getName());
    private static final Map<String, Supplier<? extends Parser>> PARSER_REGISTRY = new LinkedHashMap<>();
    private static final List<ParserRegistration> BUILT_IN_REGISTRATIONS = List.of(
            new ParserRegistration("ExcelParser", List.of(".xlsx", ".xls", ".csv", ".tsv")),
            new ParserRegistration("HTMLFileParser", List.of(".html", ".htm")),
            new ParserRegistration("JsonParser", List.of(".json")),
            new ParserRegistration("PDFParser", List.of(".pdf")),
            new ParserRegistration("TxtMdParser", List.of(".txt", ".md", ".markdown")),
            new ParserRegistration("WordParser", List.of(".docx", ".doc")),
            new ParserRegistration("ImageParser", List.of(".png", ".jpg", ".jpeg", ".webp", ".gif", ".jfif"))
    );

    public AutoFileParser() {
        ensureParsersLoaded();
    }

    /**
     * Mirrors Python's {@code register_parser} decorator in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/auto_file_parser.py}.
     */
    public static void registerParser(List<String> fileExtensions, Supplier<? extends Parser> parserFactory) {
        if (fileExtensions == null || parserFactory == null) {
            return;
        }
        for (String extension : fileExtensions) {
            String normalized = normalizeExtension(extension);
            if (!normalized.isBlank() && !PARSER_REGISTRY.containsKey(normalized)) {
                PARSER_REGISTRY.put(normalized, parserFactory);
                LOGGER.info("Registered parser for " + normalized);
            }
        }
    }

    @Override
    public CompletableFuture<List<Document>> parseAsync(
            String doc,
            String docId,
            BaseModelClient llmClient,
            Map<String, Object> options
    ) {
        if (doc == null || !Files.exists(Path.of(doc))) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_INDEXING_FILE_NOT_FOUND,
                    "error_msg",
                    "File " + doc + " does not exist"
            );
        }
        String fileExt = extensionOf(doc);
        Supplier<? extends Parser> parserFactory = PARSER_REGISTRY.get(fileExt);
        if (parserFactory == null) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_INDEXING_FORMAT_NOT_SUPPORT,
                    "error_msg",
                    "Unsupported format: " + fileExt + ", only " + new ArrayList<>(PARSER_REGISTRY.keySet())
                            + " are supported"
            );
        }

        Parser parser = parserFactory.get();
        LOGGER.info("Using " + parser.getClass().getSimpleName() + " to parse " + doc);
        Map<String, Object> safeOptions = options == null ? Map.of() : new LinkedHashMap<>(options);
        return parser.parseAsync(doc, docId, llmClient, safeOptions)
                .thenApply(documents -> enrichDocuments(
                        documents == null ? List.of() : documents,
                        doc,
                        docId == null ? "" : docId,
                        fileExt,
                        safeOptions
                ));
    }

    @Override
    public boolean supports(String doc) {
        return doc != null && Files.exists(Path.of(doc)) && PARSER_REGISTRY.containsKey(extensionOf(doc));
    }

    @Override
    protected CompletableFuture<String> parseContent(
            String doc,
            BaseModelClient llmClient,
            Map<String, Object> options
    ) {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Mirrors Python's {@code register_new_parser} in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/auto_file_parser.py}.
     */
    public static void registerNewParser(String fileExtension, Supplier<? extends Parser> parserFactory) {
        if (parserFactory == null) {
            return;
        }
        String normalized = normalizeExtension(fileExtension);
        if (normalized.isBlank()) {
            return;
        }
        PARSER_REGISTRY.put(normalized, parserFactory);
        LOGGER.info("Dynamically registered parser for " + normalized);
    }

    /**
     * Mirrors Python's {@code get_supported_formats} in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/auto_file_parser.py}.
     */
    public static List<String> getSupportedFormats() {
        return new ArrayList<>(PARSER_REGISTRY.keySet());
    }

    static void clearRegisteredParsersForTest() {
        PARSER_REGISTRY.clear();
    }

    private static void ensureParsersLoaded() {
        for (ParserRegistration registration : BUILT_IN_REGISTRATIONS) {
            tryRegisterBuiltIn(registration);
        }
    }

    private static void tryRegisterBuiltIn(ParserRegistration registration) {
        try {
            Class<?> parserClass = Class.forName(AutoFileParser.class.getPackageName() + "." + registration.className());
            if (!Parser.class.isAssignableFrom(parserClass)) {
                return;
            }
            registerParser(registration.extensions(), () -> instantiateParser(parserClass));
        } catch (ClassNotFoundException error) {
            LOGGER.log(Level.FINE, "Parser class not yet translated: " + registration.className(), error);
        }
    }

    private static Parser instantiateParser(Class<?> parserClass) {
        try {
            return (Parser) parserClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Failed to create parser " + parserClass.getName(), error);
        }
    }

    private static List<Document> enrichDocuments(
            List<Document> documents,
            String doc,
            String docId,
            String fileExt,
            Map<String, Object> options
    ) {
        if (documents.isEmpty()) {
            return List.of();
        }
        String fileName = options.containsKey("file_name")
                ? String.valueOf(options.get("file_name"))
                : Path.of(doc).getFileName().toString();
        for (Document document : documents) {
            Map<String, Object> metadata = document.getMetadata() == null
                    ? new LinkedHashMap<>()
                    : document.getMetadata();
            metadata.put("doc_id", docId);
            metadata.put("title", fileName);
            metadata.put("file_path", doc);
            metadata.put("file_ext", fileExt);
            document.setMetadata(metadata);
        }
        return documents;
    }

    private static String extensionOf(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String fileName = Path.of(path).getFileName().toString().toLowerCase(Locale.ROOT);
        int index = fileName.lastIndexOf('.');
        return index < 0 ? "" : fileName.substring(index);
    }

    private static String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return "";
        }
        String normalized = extension.toLowerCase(Locale.ROOT);
        return normalized.startsWith(".") ? normalized : "." + normalized;
    }

    /**
     * Mirrors Python's built-in parser import list in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/auto_file_parser.py}.
     */
    private record ParserRegistration(String className, List<String> extensions) {
    }
}
