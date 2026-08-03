/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code tests/unit_tests/core/retrieval/indexing/processor/chunker/test_text_preprocessor.py}.
 */
class TextPreprocessorTest {

    private static final class ConcretePreprocessor implements TextPreprocessor {
        @Override
        public String process(String text) {
            return text.toUpperCase();
        }
    }

    private static final class OrderTracker implements TextPreprocessor {
        private final String name;

        private OrderTracker(String name) {
            this.name = name;
        }

        @Override
        public String process(String text) {
            return text;
        }
    }

    @Test
    void testProcess() {
        assertEquals("TEST", new ConcretePreprocessor().process("test"));
    }

    @Test
    void testCall() {
        assertEquals("TEST", new ConcretePreprocessor().call("test"));
    }

    @Test
    void testCannotInstantiateAbstractClass() {
        assertTrue(TextPreprocessor.class.isInterface());
    }

    @Test
    void testProcessNormalText() {
        assertEquals("This is a test", new WhitespaceNormalizer().process("This is a test"));
    }

    @Test
    void testProcessMultipleSpaces() {
        assertEquals("This is a test", new WhitespaceNormalizer().process("This   is    a     test"));
    }

    @Test
    void testProcessNewlines() {
        assertEquals("This is a test", new WhitespaceNormalizer().process("This\nis\na\ntest"));
    }

    @Test
    void testProcessTabs() {
        assertEquals("This is a test", new WhitespaceNormalizer().process("This\tis\ta\ttest"));
    }

    @Test
    void testProcessMixedWhitespace() {
        assertEquals("This is a test", new WhitespaceNormalizer().process("This  \n\t  is  \n\t  a  \n\t  test"));
    }

    @Test
    void testProcessLeadingTrailingWhitespace() {
        assertEquals("This is a test", new WhitespaceNormalizer().process("   This is a test   "));
    }

    @Test
    void testWhitespaceNormalizerProcessEmptyString() {
        assertEquals("", new WhitespaceNormalizer().process(""));
    }

    @Test
    void testWhitespaceNormalizerProcessNone() {
        assertNull(new WhitespaceNormalizer().process(null));
    }

    @Test
    void testInitDefaults() {
        URLEmailRemover remover = new URLEmailRemover();
        assertTrue(remover.isRemoveUrls());
        assertTrue(remover.isRemoveEmails());
        assertEquals("", remover.getReplacement());
    }

    @Test
    void testInitCustom() {
        URLEmailRemover remover = new URLEmailRemover(false, true, "[removed]");
        assertFalse(remover.isRemoveUrls());
        assertTrue(remover.isRemoveEmails());
        assertEquals("[removed]", remover.getReplacement());
    }

    @Test
    void testRemoveUrlsHttp() {
        assertFalse(new URLEmailRemover().process("Visit http://example.com for more info").contains("http://example.com"));
    }

    @Test
    void testRemoveUrlsHttps() {
        assertFalse(new URLEmailRemover().process("Visit https://example.com for more info").contains("https://example.com"));
    }

    @Test
    void testRemoveUrlsWww() {
        assertFalse(new URLEmailRemover().process("Visit www.example.com for more info").contains("www.example.com"));
    }

    @Test
    void testRemoveEmails() {
        assertFalse(new URLEmailRemover().process("Contact us at test@example.com for support").contains("test@example.com"));
    }

    @Test
    void testRemoveUrlsWithReplacement() {
        String result = new URLEmailRemover("[URL]").process("Visit http://example.com for more info");
        assertTrue(result.contains("[URL]"));
        assertFalse(result.contains("http://example.com"));
    }

    @Test
    void testRemoveEmailsWithReplacement() {
        String result = new URLEmailRemover("[EMAIL]").process("Contact test@example.com");
        assertTrue(result.contains("[EMAIL]"));
        assertFalse(result.contains("test@example.com"));
    }

    @Test
    void testDisableUrlRemoval() {
        assertTrue(new URLEmailRemover(false).process("Visit http://example.com for more info").contains("http://example.com"));
    }

    @Test
    void testUrlEmailRemoverProcessEmptyString() {
        assertEquals("", new URLEmailRemover().process(""));
    }

    @Test
    void testUrlEmailRemoverProcessNone() {
        assertNull(new URLEmailRemover().process(null));
    }

    @Test
    void testSpecialInitDefaults() {
        SpecialCharacterNormalizer normalizer = new SpecialCharacterNormalizer();
        assertEquals("", normalizer.getCharsToRemove());
        assertEquals(Map.of(), normalizer.getCharsToReplace());
    }

    @Test
    void testInitWithCharsToRemove() {
        assertEquals("!@#", new SpecialCharacterNormalizer("!@#").getCharsToRemove());
    }

    @Test
    void testInitWithCharsToReplace() {
        assertEquals(
                Map.of("&", "and", "@", "at"),
                new SpecialCharacterNormalizer(Map.of("&", "and", "@", "at")).getCharsToReplace());
    }

    @Test
    void testRemoveControlCharacters() {
        String result = new SpecialCharacterNormalizer().process("Test\u0000text\u001fwith\u007fcontrol");
        assertFalse(result.contains("\u0000"));
        assertFalse(result.contains("\u001f"));
        assertFalse(result.contains("\u007f"));
    }

    @Test
    void testRemoveRedundantSymbols() {
        assertEquals("text", new SpecialCharacterNormalizer().process("text!!!@@"));
    }

    @Test
    void testReplaceCharacters() {
        String result = new SpecialCharacterNormalizer(Map.of("&", "and", "@", "at"))
                .process("Tom & Jerry @ home");
        assertTrue(result.contains("and"));
        assertTrue(result.contains("at"));
        assertFalse(result.contains("&"));
        assertFalse(result.contains("@"));
    }

    @Test
    void testRemoveSpecifiedCharacters() {
        String result = new SpecialCharacterNormalizer("!@#").process("Test!text@with#special");
        assertFalse(result.contains("!"));
        assertFalse(result.contains("@"));
        assertFalse(result.contains("#"));
    }

    @Test
    void testSpecialProcessEmptyString() {
        assertEquals("", new SpecialCharacterNormalizer().process(""));
    }

    @Test
    void testSpecialProcessNone() {
        assertNull(new SpecialCharacterNormalizer().process(null));
    }

    @Test
    void testInitEmpty() {
        assertEquals(0, new PreprocessingPipeline().getPreprocessors().size());
    }

    @Test
    void testInitWithPreprocessors() {
        assertEquals(2, new PreprocessingPipeline(List.of(new WhitespaceNormalizer(), new URLEmailRemover()))
                .getPreprocessors().size());
    }

    @Test
    void testAddPreprocessor() {
        PreprocessingPipeline pipeline = new PreprocessingPipeline();
        WhitespaceNormalizer preprocessor = new WhitespaceNormalizer();
        pipeline.addPreprocessor(preprocessor);
        assertEquals(1, pipeline.getPreprocessors().size());
        assertSame(preprocessor, pipeline.getPreprocessors().get(0));
    }

    @Test
    void testProcessSinglePreprocessor() {
        assertEquals("This is a test",
                new PreprocessingPipeline(List.of(new WhitespaceNormalizer())).process("This   is   a   test"));
    }

    @Test
    void testProcessOrder() {
        OrderTracker tracker1 = new OrderTracker("first");
        OrderTracker tracker2 = new OrderTracker("second");
        PreprocessingPipeline pipeline = new PreprocessingPipeline(List.of(tracker1, tracker2));
        pipeline.process("test");
        assertEquals("first", tracker1.name);
        assertEquals("second", tracker2.name);
        assertSame(tracker1, pipeline.getPreprocessors().get(0));
        assertSame(tracker2, pipeline.getPreprocessors().get(1));
    }

    @Test
    void testPipelineCall() {
        assertEquals("This is a test",
                new PreprocessingPipeline(List.of(new WhitespaceNormalizer())).call("This   is   a   test"));
    }

    @Test
    void testLen() {
        assertEquals(2, new PreprocessingPipeline(List.of(new WhitespaceNormalizer(), new URLEmailRemover())).size());
    }

    @Test
    void testPipelineProcessEmptyString() {
        assertEquals("", new PreprocessingPipeline(List.of(new WhitespaceNormalizer())).process(""));
    }
}
