/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Package bridge for retrieval parser exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.retrieval.indexing.processor.parser}
 * package facade in
 * {@code openjiuwen/core/retrieval/indexing/processor/parser/__init__.py}.</p>
 */
public final class ParserPackage {

    public static final String PYTHON_MODULE =
            "openjiuwen/core/retrieval/indexing/processor/parser/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "AutoFileParser",
            "AutoLinkParser",
            "AutoParser",
            "ExcelParser",
            "HTMLFileParser",
            "Parser",
            "JSONParser",
            "PDFParser",
            "TxtMdParser",
            "WebPageParser",
            "WordParser",
            "WeChatArticleParser",
            "ImageParser",
            "parse_wechat_article_url",
            "parse_web_page_url"
    );

    public static final Map<String, String> EXPORT_SOURCES = buildExportSources();
    public static final Map<String, String> JAVA_SYMBOL_NAMES = buildJavaSymbolNames();

    private ParserPackage() {
    }

    /**
     * Mirrors Python's {@code __all__}.
     *
     * @return exported symbol names in Python order
     */
    public static List<String> all() {
        return EXPORTED_SYMBOLS;
    }

    /**
     * Checks whether a symbol is re-exported by the Python package facade.
     *
     * @param symbolName symbol name
     * @return {@code true} when the symbol is part of Python {@code __all__}
     */
    public static boolean exports(String symbolName) {
        return EXPORTED_SYMBOLS.contains(symbolName);
    }

    /**
     * Returns the Python source object imported by the package facade.
     *
     * @param symbolName symbol name
     * @return dotted Python source object, or {@code null} when absent
     */
    public static String sourceFor(String symbolName) {
        return EXPORT_SOURCES.get(symbolName);
    }

    /**
     * Returns the expected Java class or static member name for an exported symbol.
     *
     * @param symbolName symbol name
     * @return fully qualified Java symbol name, or {@code null} when absent
     */
    public static String javaSymbolNameFor(String symbolName) {
        return JAVA_SYMBOL_NAMES.get(symbolName);
    }

    /**
     * Resolves the Java class for class-like exported symbols when the translated dependency is present.
     *
     * @param symbolName symbol name
     * @return resolved class, or empty for unknown symbols, static function exports, or unmerged classes
     */
    public static Optional<Class<?>> resolveType(String symbolName) {
        String javaSymbolName = JAVA_SYMBOL_NAMES.get(symbolName);
        if (javaSymbolName == null || javaSymbolName.contains("#")) {
            return Optional.empty();
        }
        try {
            return Optional.of(Class.forName(javaSymbolName));
        } catch (ClassNotFoundException ignored) {
            return Optional.empty();
        }
    }

    private static Map<String, String> buildExportSources() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("AutoFileParser",
                "openjiuwen.core.retrieval.indexing.processor.parser.auto_file_parser.AutoFileParser");
        sources.put("AutoLinkParser",
                "openjiuwen.core.retrieval.indexing.processor.parser.auto_link_parser.AutoLinkParser");
        sources.put("AutoParser", "openjiuwen.core.retrieval.indexing.processor.parser.auto_parser.AutoParser");
        sources.put("ExcelParser", "openjiuwen.core.retrieval.indexing.processor.parser.excel_parser.ExcelParser");
        sources.put("HTMLFileParser",
                "openjiuwen.core.retrieval.indexing.processor.parser.html_file_parser.HTMLFileParser");
        sources.put("Parser", "openjiuwen.core.retrieval.indexing.processor.parser.base.Parser");
        sources.put("JSONParser", "openjiuwen.core.retrieval.indexing.processor.parser.json_parser.JSONParser");
        sources.put("PDFParser", "openjiuwen.core.retrieval.indexing.processor.parser.pdf_parser.PDFParser");
        sources.put("TxtMdParser", "openjiuwen.core.retrieval.indexing.processor.parser.txt_md_parser.TxtMdParser");
        sources.put("WebPageParser",
                "openjiuwen.core.retrieval.indexing.processor.parser.web_page_parser.WebPageParser");
        sources.put("WordParser", "openjiuwen.core.retrieval.indexing.processor.parser.word_parser.WordParser");
        sources.put("WeChatArticleParser",
                "openjiuwen.core.retrieval.indexing.processor.parser.wechat_article_parser.WeChatArticleParser");
        sources.put("ImageParser", "openjiuwen.core.retrieval.indexing.processor.parser.image_parser.ImageParser");
        sources.put("parse_wechat_article_url",
                "openjiuwen.core.retrieval.indexing.processor.parser.wechat_article_parser.parse_wechat_article_url");
        sources.put("parse_web_page_url",
                "openjiuwen.core.retrieval.indexing.processor.parser.web_page_parser.parse_web_page_url");
        return Collections.unmodifiableMap(sources);
    }

    private static Map<String, String> buildJavaSymbolNames() {
        Map<String, String> javaSymbols = new LinkedHashMap<>();
        javaSymbols.put("AutoFileParser",
                "com.openjiuwen.core.retrieval.indexing.processor.parser.AutoFileParser");
        javaSymbols.put("AutoLinkParser",
                "com.openjiuwen.core.retrieval.indexing.processor.parser.AutoLinkParser");
        javaSymbols.put("AutoParser",
                "com.openjiuwen.core.retrieval.indexing.processor.parser.AutoParser");
        javaSymbols.put("ExcelParser",
                "com.openjiuwen.core.retrieval.indexing.processor.parser.ExcelParser");
        javaSymbols.put("HTMLFileParser",
                "com.openjiuwen.core.retrieval.indexing.processor.parser.HTMLFileParser");
        javaSymbols.put("Parser",
                "com.openjiuwen.core.retrieval.indexing.processor.parser.Parser");
        javaSymbols.put("JSONParser",
                "com.openjiuwen.core.retrieval.indexing.processor.parser.JSONParser");
        javaSymbols.put("PDFParser",
                "com.openjiuwen.core.retrieval.indexing.processor.parser.PDFParser");
        javaSymbols.put("TxtMdParser",
                "com.openjiuwen.core.retrieval.indexing.processor.parser.TxtMdParser");
        javaSymbols.put("WebPageParser",
                "com.openjiuwen.core.retrieval.indexing.processor.parser.WebPageParser");
        javaSymbols.put("WordParser",
                "com.openjiuwen.core.retrieval.indexing.processor.parser.WordParser");
        javaSymbols.put("WeChatArticleParser",
                "com.openjiuwen.core.retrieval.indexing.processor.parser.WeChatArticleParser");
        javaSymbols.put("ImageParser",
                "com.openjiuwen.core.retrieval.indexing.processor.parser.ImageParser");
        javaSymbols.put("parse_wechat_article_url",
                "com.openjiuwen.core.retrieval.indexing.processor.parser.WeChatArticleParser#parseWechatArticleUrl");
        javaSymbols.put("parse_web_page_url",
                "com.openjiuwen.core.retrieval.indexing.processor.parser.WebPageParser#parseWebPageUrl");
        return Collections.unmodifiableMap(javaSymbols);
    }
}
