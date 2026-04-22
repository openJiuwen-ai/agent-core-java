/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.retrieval.TestModelClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parseImageReturnsCaptionDocument() throws IOException {
        Path image = tempDir.resolve("sample.png");
        Files.write(image, new byte[]{(byte) 0x89, 'P', 'N', 'G'});

        ImageParser parser = new ImageParser();
        var docs = parser.parse(image.toString(), "img-1", new TestModelClient("gpt-4o", "a cat sitting on a mat"), Map.of());

        assertEquals(1, docs.size());
        assertTrue(docs.get(0).getText().contains("a cat sitting on a mat"));
    }

    @Test
    void parseMissingImageReturnsEmpty() {
        ImageParser parser = new ImageParser();

        assertTrue(parser.parse(tempDir.resolve("missing.png").toString(), "img-1", new TestModelClient("gpt-4o", "caption"), Map.of()).isEmpty());
    }
}
