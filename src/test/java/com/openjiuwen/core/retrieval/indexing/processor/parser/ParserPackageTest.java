/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the retrieval parser package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.retrieval.indexing.processor.parser}
 * package facade in
 * {@code openjiuwen/core/retrieval/indexing/processor/parser/__init__.py}.</p>
 */
class ParserPackageTest {

    @Test
    void exportedSymbolsMatchPythonAllOrder() {
        List<String> expected = List.of(
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

        assertEquals(
                "openjiuwen/core/retrieval/indexing/processor/parser/__init__.py",
                ParserPackage.PYTHON_MODULE
        );
        assertEquals(expected, ParserPackage.EXPORTED_SYMBOLS);
        assertSame(ParserPackage.EXPORTED_SYMBOLS, ParserPackage.all());
        assertEquals(expected, new ArrayList<>(ParserPackage.EXPORT_SOURCES.keySet()));
        assertEquals(expected, new ArrayList<>(ParserPackage.JAVA_SYMBOL_NAMES.keySet()));
    }

    @Test
    void sourceMapPreservesPythonReExportOrigins() {
        assertEquals(
                "openjiuwen.core.retrieval.indexing.processor.parser.auto_file_parser.AutoFileParser",
                ParserPackage.sourceFor("AutoFileParser")
        );
        assertEquals(
                "openjiuwen.core.retrieval.indexing.processor.parser.base.Parser",
                ParserPackage.sourceFor("Parser")
        );
        assertEquals(
                "openjiuwen.core.retrieval.indexing.processor.parser.wechat_article_parser.WeChatArticleParser",
                ParserPackage.sourceFor("WeChatArticleParser")
        );
        assertEquals(
                "openjiuwen.core.retrieval.indexing.processor.parser.wechat_article_parser.parse_wechat_article_url",
                ParserPackage.sourceFor("parse_wechat_article_url")
        );
        assertEquals(
                "openjiuwen.core.retrieval.indexing.processor.parser.web_page_parser.parse_web_page_url",
                ParserPackage.sourceFor("parse_web_page_url")
        );
    }

    @Test
    void javaSymbolNamesRepresentFutureClassesAndStaticFunctions() {
        assertEquals(
                "com.openjiuwen.core.retrieval.indexing.processor.parser.AutoFileParser",
                ParserPackage.javaSymbolNameFor("AutoFileParser")
        );
        assertEquals(
                "com.openjiuwen.core.retrieval.indexing.processor.parser.WeChatArticleParser#parseWechatArticleUrl",
                ParserPackage.javaSymbolNameFor("parse_wechat_article_url")
        );
        assertEquals(
                "com.openjiuwen.core.retrieval.indexing.processor.parser.WebPageParser#parseWebPageUrl",
                ParserPackage.javaSymbolNameFor("parse_web_page_url")
        );
    }

    @Test
    void resolveTypeIsLazyForUnmergedDependenciesAndFunctions() {
        ParserPackage.resolveType("AutoFileParser").ifPresent(type -> assertEquals(
                "com.openjiuwen.core.retrieval.indexing.processor.parser.AutoFileParser",
                type.getName()
        ));
        assertTrue(ParserPackage.resolveType("parse_wechat_article_url").isEmpty());
        assertTrue(ParserPackage.resolveType("parse_web_page_url").isEmpty());
    }

    @Test
    void jsonParserExportResolvesCanonicalJavaType() {
        Class<?> resolvedType = assertDoesNotThrow(
                () -> ParserPackage.resolveType("JSONParser").orElseThrow());

        assertSame(JsonParser.class, resolvedType);
    }

    @Test
    void unknownSymbolIsNotExported() {
        assertTrue(ParserPackage.exports("Parser"));
        assertFalse(ParserPackage.exports("MissingParser"));
        assertNull(ParserPackage.sourceFor("MissingParser"));
        assertNull(ParserPackage.javaSymbolNameFor("MissingParser"));
        assertTrue(ParserPackage.resolveType("MissingParser").isEmpty());
    }

    @Test
    void exportedCollectionsAreImmutable() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> ParserPackage.EXPORTED_SYMBOLS.add("Unexpected")
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> ParserPackage.EXPORT_SOURCES.put("Unexpected", "unexpected")
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> ParserPackage.JAVA_SYMBOL_NAMES.put("Unexpected", "Unexpected")
        );
    }
}
