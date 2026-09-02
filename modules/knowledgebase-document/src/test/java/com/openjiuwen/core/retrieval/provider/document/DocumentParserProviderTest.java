/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.provider.document;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.retrieval.indexing.processor.parser.AutoFileParser;
import com.openjiuwen.core.retrieval.indexing.processor.parser.ExcelParser;
import com.openjiuwen.core.retrieval.indexing.processor.parser.PDFParser;
import com.openjiuwen.core.retrieval.indexing.processor.parser.WordParser;
import com.openjiuwen.core.retrieval.provider.ParserProvider;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ServiceLoader;

class DocumentParserProviderTest {
    @Test
    void discoversAllDocumentParserProviders() {
        List<ParserProvider> providers = ServiceLoader.load(ParserProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .toList();

        assertTrue(providers.stream().anyMatch(provider -> provider.create() instanceof PDFParser));
        assertTrue(providers.stream().anyMatch(provider -> provider.create() instanceof WordParser));
        assertTrue(providers.stream().anyMatch(provider -> provider.create() instanceof ExcelParser));
        assertTrue(AutoFileParser.getSupportedFormats().containsAll(List.of(".pdf", ".docx", ".xlsx")));
    }

    @Test
    void providersCreateTheirOwnedParserTypes() {
        assertInstanceOf(PDFParser.class, new PdfParserProvider().create());
        assertInstanceOf(WordParser.class, new WordParserProvider().create());
        assertInstanceOf(ExcelParser.class, new ExcelParserProvider().create());
    }
}
