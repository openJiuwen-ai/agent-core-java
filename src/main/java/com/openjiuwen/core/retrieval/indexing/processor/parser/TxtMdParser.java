/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Local file parser for TXT/MD format.
 *
 * <p>Mirrors Python's {@code TxtMdParser} in
 * {@code openjiuwen/core/retrieval/indexing/processor/parser/txt_md_parser.py}.</p>
 */
public class TxtMdParser extends Parser {

    private static final Logger LOGGER = LoggerFactory.getLogger(TxtMdParser.class);
    private static final Charset GB18030 = Charset.forName("GB18030");

    /**
     * Mirrors Python's {@code TxtMdParser._parse} in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/txt_md_parser.py}.
     */
    @Override
    protected CompletableFuture<String> parseContent(
            String filePath,
            BaseModelClient llmClient,
            Map<String, Object> options
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                byte[] rawData = Files.readAllBytes(Path.of(filePath));
                DecodingPlan decodingPlan = detectEncoding(rawData);
                String content = decodeIgnoringErrors(rawData, decodingPlan.charset(), decodingPlan.offset());
                return content == null ? null : content.strip();
            } catch (Exception exception) {
                LOGGER.error("Failed to parse TXT/MD {}: {}", filePath, exception.getMessage());
                return null;
            }
        });
    }

    @Override
    public boolean supports(String doc) {
        if (doc == null || doc.isBlank()) {
            return false;
        }
        String fileName = Path.of(doc).getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".txt") || fileName.endsWith(".md") || fileName.endsWith(".markdown");
    }

    private static DecodingPlan detectEncoding(byte[] rawData) {
        if (rawData == null || rawData.length == 0) {
            return new DecodingPlan(StandardCharsets.UTF_8, 0);
        }
        if (startsWith(rawData, (byte) 0xEF, (byte) 0xBB, (byte) 0xBF)) {
            return new DecodingPlan(StandardCharsets.UTF_8, 3);
        }
        if (startsWith(rawData, (byte) 0xFE, (byte) 0xFF)) {
            return new DecodingPlan(StandardCharsets.UTF_16BE, 2);
        }
        if (startsWith(rawData, (byte) 0xFF, (byte) 0xFE)) {
            return new DecodingPlan(StandardCharsets.UTF_16LE, 2);
        }
        if (canDecode(rawData, StandardCharsets.UTF_8)) {
            return new DecodingPlan(StandardCharsets.UTF_8, 0);
        }
        if (canDecode(rawData, GB18030)) {
            return new DecodingPlan(GB18030, 0);
        }
        return new DecodingPlan(StandardCharsets.UTF_8, 0);
    }

    private static boolean startsWith(byte[] rawData, byte... prefix) {
        if (rawData.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (rawData[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean canDecode(byte[] rawData, Charset charset) {
        try {
            charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(rawData));
            return true;
        } catch (CharacterCodingException exception) {
            return false;
        }
    }

    private static String decodeIgnoringErrors(byte[] rawData, Charset charset, int offset)
            throws CharacterCodingException {
        if (rawData == null || rawData.length <= offset) {
            return "";
        }
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.IGNORE)
                .onUnmappableCharacter(CodingErrorAction.IGNORE);
        return decoder.decode(ByteBuffer.wrap(rawData, offset, rawData.length - offset)).toString();
    }

    private record DecodingPlan(Charset charset, int offset) {
    }
}
