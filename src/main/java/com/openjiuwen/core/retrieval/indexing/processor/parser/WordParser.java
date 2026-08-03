/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Local file parser for DOCX/DOC word processor files.
 *
 * <p>Mirrors Python's {@code WordParser} in
 * {@code openjiuwen/core/retrieval/indexing/processor/parser/word_parser.py}.</p>
 */
public class WordParser extends Parser {

    private static final Logger LOGGER = LoggerFactory.getLogger(WordParser.class);

    /**
     * Mirrors Python's {@code WordParser._parse} in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/word_parser.py}.
     */
    @Override
    protected CompletableFuture<String> parseContent(
            String filePath,
            BaseModelClient llmClient,
            Map<String, Object> options
    ) {
        return CompletableFuture.supplyAsync(() -> readDocument(filePath))
                .thenCompose(plan -> {
                    if (plan == null) {
                        return CompletableFuture.completedFuture(null);
                    }
                    ImageCaptioner imageCaptioner = createImageCaptioner(llmClient);
                    CompletableFuture<List<String>> chain = CompletableFuture.completedFuture(new ArrayList<>());
                    for (ContentBlock block : plan.blocks()) {
                        chain = chain.thenCompose(content -> appendBlock(content, block, imageCaptioner));
                    }
                    return chain.thenApply(WordParser::joinContent);
                })
                .exceptionally(error -> {
                    LOGGER.error("Failed to parse DOCX {}: {}", filePath, rootMessage(error));
                    return null;
                });
    }

    @Override
    public boolean supports(String doc) {
        if (doc == null || doc.isBlank()) {
            return false;
        }
        String fileName = Path.of(doc).getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".docx") || fileName.endsWith(".doc");
    }

    /**
     * Mirrors Python's {@code ImageCaptioner(llm_client=llm_client)} construction in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/word_parser.py}.
     */
    protected ImageCaptioner createImageCaptioner(BaseModelClient llmClient) {
        return new ImageCaptioner(llmClient);
    }

    protected String savedImageDir() {
        return ImageCaptioner.SAVED_IMAGE_DIR;
    }

    private ParsedContentPlan readDocument(String filePath) {
        try (InputStream inputStream = Files.newInputStream(Path.of(filePath));
             XWPFDocument document = new XWPFDocument(inputStream)) {
            List<ContentBlock> blocks = new ArrayList<>();
            String fileName = Path.of(filePath).getFileName() == null
                    ? filePath
                    : Path.of(filePath).getFileName().toString();
            int blockIndex = 0;
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    List<String> textLines = new ArrayList<>();
                    String paragraphText = paragraphToMarkdown(paragraph);
                    if (!paragraphText.isBlank()) {
                        textLines.add(paragraphText);
                    }
                    List<String> images = extractImagesFromParagraph(paragraph, blockIndex, fileName, savedImageDir());
                    blocks.add(new ContentBlock(textLines, images));
                } else if (element instanceof XWPFTable table) {
                    String tableText = tableToMarkdown(table);
                    blocks.add(new ContentBlock(tableText.isBlank() ? List.of() : List.of(tableText), List.of()));
                }
                blockIndex++;
            }
            return new ParsedContentPlan(blocks);
        } catch (Exception exception) {
            throw new CompletionException(exception);
        }
    }

    private static CompletableFuture<List<String>> appendBlock(
            List<String> content,
            ContentBlock block,
            ImageCaptioner imageCaptioner
    ) {
        content.addAll(block.textLines());
        if (block.imagePaths().isEmpty()) {
            return CompletableFuture.completedFuture(content);
        }
        return imageCaptioner.captionImages(block.imagePaths()).thenApply(captions -> {
            if (captions != null) {
                for (String caption : captions) {
                    if (caption != null && !caption.isBlank()) {
                        content.add(caption);
                    }
                }
            }
            return content;
        });
    }

    /**
     * Mirrors Python's module-level {@code _paragraph_to_markdown} in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/word_parser.py}.
     */
    static String paragraphToMarkdown(XWPFParagraph paragraph) {
        String text = paragraph == null ? "" : paragraph.getText();
        if (text == null || text.trim().isBlank()) {
            return "";
        }
        String trimmedText = text.trim();
        String style = paragraph.getStyle();
        if (style == null || style.isBlank()) {
            style = paragraph.getStyleID();
        }
        if (style == null) {
            return trimmedText;
        }
        style = style.trim();
        if ("Title".equals(style)) {
            return "# " + trimmedText;
        }
        Integer headingLevel = headingLevel(style);
        if (headingLevel != null && headingLevel >= 1 && headingLevel <= 9) {
            return "#".repeat(headingLevel + 1) + " " + trimmedText;
        }
        return trimmedText;
    }

    /**
     * Mirrors Python's module-level {@code _table_to_markdown} in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/word_parser.py}.
     */
    static String tableToMarkdown(XWPFTable table) {
        if (table == null || table.getRows().isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                String cellText = cell == null ? "" : cell.getText();
                cells.add((cellText == null ? "" : cellText).trim().replace("|", "\\|"));
            }
            lines.add("| " + String.join(" | ", cells) + " |");
            if (lines.size() == 1) {
                lines.add("| " + String.join(" | ", cells.stream().map(ignored -> "---").toList()) + " |");
            }
        }
        lines.add("");
        return String.join("\n", lines);
    }

    /**
     * Mirrors Python's {@code WordParser._extract_images_from_paragraph} in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/word_parser.py}.
     */
    static List<String> extractImagesFromParagraph(
            XWPFParagraph paragraph,
            int paragraphNum,
            String filename,
            String outputDir
    ) throws IOException {
        if (paragraph == null) {
            return List.of();
        }
        List<String> images = new ArrayList<>();
        Path outputDirectory = Path.of(outputDir == null || outputDir.isBlank()
                ? ImageCaptioner.SAVED_IMAGE_DIR
                : outputDir);
        int imageIndex = 0;
        for (XWPFRun run : paragraph.getRuns()) {
            for (XWPFPicture picture : run.getEmbeddedPictures()) {
                XWPFPictureData pictureData = picture.getPictureData();
                if (pictureData == null) {
                    continue;
                }
                Files.createDirectories(outputDirectory);
                Path imagePath = outputDirectory.resolve(filename + "__para_" + paragraphNum
                        + "__img_" + imageIndex + ".png");
                writePngImage(pictureData.getData(), imagePath);
                images.add(imagePath.toString());
                imageIndex++;
            }
        }
        return images;
    }

    private static void writePngImage(byte[] imageBytes, Path imagePath) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (image == null) {
            Files.write(imagePath, imageBytes);
            return;
        }
        ImageIO.write(image, "png", imagePath.toFile());
    }

    private static Integer headingLevel(String style) {
        String normalized = style.replace(" ", "");
        if (!normalized.startsWith("Heading")) {
            return null;
        }
        try {
            return Integer.parseInt(normalized.substring("Heading".length()));
        } catch (NumberFormatException exception) {
            LOGGER.error("Error while parsing docx paragraph with style {}: {}", style, exception.toString());
            return null;
        }
    }

    private static String joinContent(List<String> content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        String joined = String.join("\n", content.stream()
                .filter(line -> line != null && !line.isBlank())
                .toList());
        return joined.isBlank() ? null : joined;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    /**
     * Mirrors Python's per-block output from {@code WordParser._parse_block} in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/word_parser.py}.
     */
    private record ContentBlock(List<String> textLines, List<String> imagePaths) {
    }

    /**
     * Mirrors Python's ordered {@code doc.iter_inner_content()} traversal in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/word_parser.py}.
     */
    private record ParsedContentPlan(List<ContentBlock> blocks) {
    }
}
