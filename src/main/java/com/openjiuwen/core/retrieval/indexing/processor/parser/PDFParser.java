/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * PDF parser with optional image caption extraction.
 * 
 * @since 0.1.7
 */
public class PDFParser extends Parser {
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
        Path path = Path.of(doc);
        if (!Files.exists(path)) {
            return null;
        }
        try (PDDocument pdf = PDDocument.load(path.toFile())) {
            List<String> content = new ArrayList<>();
            String text = new PDFTextStripper().getText(pdf).trim();
            if (!text.isBlank()) {
                content.add(text);
            }
            List<String> images = new ArrayList<>();
            int pageNum = 1;
            for (PDPage page : pdf.getPages()) {
                extractImages(page.getResources(), pageNum, path.getFileName().toString(), images);
                pageNum++;
            }
            if (llmClient != null && !images.isEmpty()) {
                List<String> captions = new ImageCaptioner(llmClient).captionImages(images);
                for (String caption : captions) {
                    if (caption != null && !caption.isBlank()) {
                        content.add(caption);
                    }
                }
            }
            String result = String.join("\n", content).trim();
            return result.isBlank() ? null : result;
        } catch (IOException ex) {
            return null;
        }
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
        return doc != null && doc.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    /**
     * extractImages.
     * 
     * @param resources resources
     * @param pageNum pageNum
     * @param fileName fileName
     * @param outputPaths outputPaths
     * @throws IOException IOException
     * @since 0.1.7
     */
    private static void extractImages(PDResources resources, int pageNum, String fileName, List<String> outputPaths)
            throws IOException {
        if (resources == null) {
            return;
        }
        int imageIndex = outputPaths.size();
        for (COSName xObjectName : resources.getXObjectNames()) {
            PDXObject xObject = resources.getXObject(xObjectName);
            if (xObject instanceof PDImageXObject image) {
                Path outputDir = Path.of(ImageCaptioner.SAVED_IMAGE_DIR);
                Files.createDirectories(outputDir);
                String suffix = image.getSuffix();
                if (suffix == null || suffix.isBlank()) {
                    suffix = "png";
                }
                Path output = outputDir.resolve(fileName + "__page_" + pageNum + "__img_" + imageIndex + "." + suffix);
                ImageIO.write(image.getImage(), suffix, output.toFile());
                outputPaths.add(output.toString());
                imageIndex++;
            } else if (xObject instanceof PDFormXObject form) {
                extractImages(form.getResources(), pageNum, fileName, outputPaths);
                imageIndex = outputPaths.size();
            } else {
                // no-op
            }
        }
    }
}
