/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import com.openjiuwen.core.retrieval.provider.ParserProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * File parser router based on file extension.
 * 
 * @since 0.1.7
 */
public class AutoFileParser extends Parser {
    private static final Map<String, Supplier<? extends Parser>> PARSER_REGISTRY = new LinkedHashMap<>();

    static {
        registerNewParser(".txt", TxtMdParser::new);
        registerNewParser(".md", TxtMdParser::new);
        registerNewParser(".markdown", TxtMdParser::new);
        registerNewParser(".htm", HTMLFileParser::new);
        registerNewParser(".html", HTMLFileParser::new);
        registerNewParser(".json", JsonParser::new);
        registerNewParser(".csv", TextFileParser::new);
        registerNewParser(".tsv", TextFileParser::new);
        registerNewParser(".png", ImageParser::new);
        registerNewParser(".jpg", ImageParser::new);
        registerNewParser(".jpeg", ImageParser::new);
        registerNewParser(".webp", ImageParser::new);
        registerNewParser(".gif", ImageParser::new);
        registerNewParser(".jfif", ImageParser::new);
        for (ParserProvider provider : ServiceLoader.load(ParserProvider.class)) {
            for (String extension : provider.extensions()) {
                registerNewParser(extension, provider::create);
            }
        }
    }

    /**
     * registerNewParser.
     * 
     * @param extension extension
     * @param supplier supplier
     * @since 0.1.7
     */
    public static void registerNewParser(String extension, Supplier<? extends Parser> supplier) {
        if (extension == null || extension.isBlank() || supplier == null) {
            return;
        }
        PARSER_REGISTRY.put(normalizeExtension(extension), supplier);
    }

    /**
     * getSupportedFormats.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static List<String> getSupportedFormats() {
        return new ArrayList<>(new TreeSet<>(PARSER_REGISTRY.keySet()));
    }

    /**
     * parse.
     * 
     * @param doc doc
     * @param docId docId
     * @param llmClient llmClient
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options) {
        Path path = Path.of(doc);
        if (!Files.exists(path)) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_INDEXING_FILE_NOT_FOUND,
                    "file does not exist: " + doc);
        }
        Parser parser = parserFor(doc, options);
        if (parser == null) {
            throw RetrievalExceptions.validation("Unsupported format: " + extensionOf(doc));
        }
        List<Document> documents = parser.parse(doc, docId, llmClient, options);
        List<Document> enriched = new ArrayList<>(documents.size());
        String fileName = options != null && options.containsKey("file_name")
                ? String.valueOf(options.get("file_name"))
                : path.getFileName().toString();
        for (Document document : documents) {
            Map<String, Object> metadata = new LinkedHashMap<>(document.getMetadata());
            metadata.put("doc_id", docId);
            metadata.put("title", fileName);
            metadata.put("file_path", doc);
            metadata.put("file_ext", extensionOf(doc));
            enriched.add(new Document(document.getId(), document.getText(), metadata));
        }
        return enriched;
    }

    /**
     * parseContent.
     * 
     * @param doc doc
     * @param llmClient llmClient
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    protected String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options) {
        return null;
    }

    /**
     * supports.
     * 
     * @param doc doc
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean supports(String doc) {
        return doc != null && Files.exists(Path.of(doc)) && parserFor(doc, Map.of()) != null;
    }

    /**
     * parserFor.
     * 
     * @param path path
     * @return the result
     * @since 0.1.7
     */
    private static Parser parserFor(String path, Map<String, Object> options) {
        Supplier<? extends Parser> supplier = PARSER_REGISTRY.get(extensionOf(path));
        return supplier == null ? null : supplier.get();
    }

    /**
     * extensionOf.
     * 
     * @param path path
     * @return the result
     * @since 0.1.7
     */
    private static String extensionOf(String path) {
        if (path == null) {
            return "";
        }
        String fileName = Path.of(path).getFileName().toString().toLowerCase(Locale.ROOT);
        int idx = fileName.lastIndexOf('.');
        return idx >= 0 ? fileName.substring(idx) : "";
    }

    /**
     * normalizeExtension.
     * 
     * @param extension extension
     * @return the result
     * @since 0.1.7
     */
    private static String normalizeExtension(String extension) {
        return extension.startsWith(".")
                ? extension.toLowerCase(Locale.ROOT)
                : "." + extension.toLowerCase(Locale.ROOT);
    }
}
