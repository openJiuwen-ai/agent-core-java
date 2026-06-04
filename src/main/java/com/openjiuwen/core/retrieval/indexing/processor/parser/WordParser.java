/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DOCX parser with optional image caption support.
 *
 * <p>Mirrors Python's {@code WordParser} in
 * {@code openjiuwen.core.retrieval.indexing.processor.parser.word_parser}.</p>
 */
public class WordParser extends Parser {

    @Override
    public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options) {
        try {
            String content = parseContent(doc, llmClient, options);
            if (content == null) {
                return List.of();
            }
            return List.of(new Document(docId, content, Map.of()));
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    @Override
    protected String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options) {
        Path path = Path.of(doc);
        if (!Files.exists(path)) {
            return null;
        }
        try (InputStream inputStream = Files.newInputStream(path); XWPFDocument document = new XWPFDocument(inputStream)) {
            List<String> content = new ArrayList<>();
            int blockIndex = 0;
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = paragraphToMarkdown(paragraph);
                    if (!text.isBlank()) {
                        content.add(text);
                    }
                    if (llmClient != null) {
                        List<String> images = extractPictures(paragraph, path.getFileName().toString(), blockIndex);
                        if (!images.isEmpty()) {
                            List<String> captions = createImageCaptioner(llmClient).captionImages(images);
                            for (String caption : captions) {
                                if (caption != null && !caption.isBlank()) {
                                    content.add(caption);
                                }
                            }
                        }
                    }
                } else if (element instanceof XWPFTable table) {
                    String tableText = tableToText(table);
                    if (!tableText.isBlank()) {
                        content.add(tableText);
                    }
                }
                blockIndex++;
            }
            String result = String.join("\n", content).trim();
            return result.isBlank() ? null : result;
        } catch (IOException ex) {
            return null;
        }
    }

    @Override
    public boolean supports(String doc) {
        return doc != null && doc.toLowerCase().endsWith(".docx");
    }

    private static String paragraphToMarkdown(XWPFParagraph paragraph) {
        String text = paragraph.getText();
        if (text == null || text.trim().isBlank()) {
            return "";
        }
        String style = paragraph.getStyle();
        if (style == null || style.isBlank()) {
            style = paragraph.getStyleID();
        }
        if (style == null) {
            return text.trim();
        }
        style = style.trim();
        if ("Title".equalsIgnoreCase(style)) {
            return "# " + text.trim();
        }
        Integer headingLevel = headingLevel(style);
        if (headingLevel != null && headingLevel >= 1 && headingLevel <= 9) {
            return "#".repeat(headingLevel + 1) + " " + text.trim();
        }
        return text.trim();
    }

    private static Integer headingLevel(String style) {
        String normalized = style.replace(" ", "");
        if (!normalized.toLowerCase().startsWith("heading")) {
            return null;
        }
        try {
            return Integer.parseInt(normalized.substring("Heading".length()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String tableToText(XWPFTable table) {
        List<String> rows = new ArrayList<>();
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                cells.add(cell.getText().trim().replace("|", "\\|"));
            }
            rows.add("| " + String.join(" | ", cells) + " |");
            if (rows.size() == 1) {
                rows.add("| " + String.join(" | ", cells.stream().map(ignored -> "---").toList()) + " |");
            }
        }
        return String.join("\n", rows).trim();
    }

    private static List<String> extractPictures(XWPFParagraph paragraph, String fileName, int paragraphIndex) throws IOException {
        List<String> imagePaths = new ArrayList<>();
        Path outputDir = Path.of(ImageCaptioner.SAVED_IMAGE_DIR);
        Files.createDirectories(outputDir);
        int pictureIndex = 0;
        for (XWPFRun run : paragraph.getRuns()) {
            for (XWPFPicture picture : run.getEmbeddedPictures()) {
                XWPFPictureData pictureData = picture.getPictureData();
                if (pictureData == null) {
                    continue;
                }
                String extension = pictureData.suggestFileExtension();
                if (extension == null || extension.isBlank()) {
                    extension = "png";
                }
                Path output = outputDir.resolve(fileName + "__para_" + paragraphIndex + "__img_" + pictureIndex + "." + extension);
                Files.write(output, pictureData.getData());
                imagePaths.add(output.toString());
                pictureIndex++;
            }
        }
        return imagePaths;
    }

    protected ImageCaptioner createImageCaptioner(BaseModelClient llmClient) {
        return new ImageCaptioner(llmClient);
    }
}
