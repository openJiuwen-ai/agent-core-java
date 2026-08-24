/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Local file parser for PDF format.
 *
 * <p>Mirrors Python's {@code PDFParser} in
 * {@code openjiuwen/core/retrieval/indexing/processor/parser/pdf_parser.py}.</p>
 */
public class PDFParser extends Parser {

    private static final Logger LOGGER = LoggerFactory.getLogger(PDFParser.class);
    private final Path allowedDocDir;

    public PDFParser() {
        this(null);
    }

    public PDFParser(Path allowedDocDir) {
        this.allowedDocDir = allowedDocDir == null
                ? null
                : allowedDocDir.toAbsolutePath().normalize();
    }

    Path resolveSafeDocument(String doc) throws IOException {
        Path requestedPath = Path.of(doc);
        if (allowedDocDir == null) {
            Path documentPath = requestedPath.toAbsolutePath().normalize();
            if (!Files.isRegularFile(documentPath)) {
                throw new IOException("PDF path is not a regular file: " + doc);
            }
            return documentPath.toRealPath();
        }

        Path realAllowedDir = allowedDocDir.toRealPath();
        Path documentPath = requestedPath.isAbsolute()
                ? requestedPath.toAbsolutePath().normalize()
                : allowedDocDir.resolve(requestedPath).normalize();
        if (!documentPath.startsWith(allowedDocDir)) {
            throw new SecurityException("PDF path is outside the allowed document directory.");
        }

        Path realDocumentPath = documentPath.toRealPath();
        if (!realDocumentPath.startsWith(realAllowedDir)) {
            throw new SecurityException("PDF path is outside the allowed document directory.");
        }
        if (!Files.isRegularFile(realDocumentPath)) {
            throw new IOException("PDF path is not a regular file: " + doc);
        }
        return realDocumentPath;
    }

    /**
     * Mirrors Python's {@code PDFParser._parse} in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/pdf_parser.py}.
     */
    @Override
    protected CompletableFuture<String> parseContent(
            String filePath,
            BaseModelClient llmClient,
            Map<String, Object> options
    ) {
        return CompletableFuture.supplyAsync(() -> extractPdfContent(filePath, llmClient))
                .thenCompose(result -> {
                    if (result == null) {
                        return CompletableFuture.completedFuture(null);
                    }
                    if (result.captionFutures().isEmpty()) {
                        return CompletableFuture.completedFuture(joinContent(result.content()));
                    }
                    CompletableFuture<?>[] futures = result.captionFutures().toArray(CompletableFuture[]::new);
                    return CompletableFuture.allOf(futures)
                            .thenApply(ignored -> {
                                for (CompletableFuture<List<String>> captionFuture : result.captionFutures()) {
                                    List<String> captions = captionFuture.join();
                                    if (captions != null) {
                                        for (String caption : captions) {
                                            if (caption != null && !caption.isBlank()) {
                                                result.content().add(caption);
                                            }
                                        }
                                    }
                                }
                                return joinContent(result.content());
                            });
                })
                .exceptionally(error -> {
                    LOGGER.error("Failed to parse PDF {}: {}", filePath, rootMessage(error));
                    return null;
                });
    }

    @Override
    public boolean supports(String doc) {
        if (doc == null || doc.isBlank()) {
            return false;
        }
        String fileName = Path.of(doc).getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".pdf");
    }

    /**
     * Mirrors Python's {@code ImageCaptioner(llm_client=llm_client)} construction in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/pdf_parser.py}.
     */
    protected ImageCaptioner createImageCaptioner(BaseModelClient llmClient) {
        return new ImageCaptioner(llmClient);
    }

    private ParseResult extractPdfContent(String filePath, BaseModelClient llmClient) {
        try {
            Path path = resolveSafeDocument(filePath);
            try (PDDocument document = PDDocument.load(path.toFile())) {
            List<String> content = new ArrayList<>();
            List<CompletableFuture<List<String>>> captionFutures = new ArrayList<>();
            ImageCaptioner imageCaptioner = createImageCaptioner(llmClient);
            PDFTextStripper stripper = new PDFTextStripper();
            String filename = path.getFileName() == null ? filePath : path.getFileName().toString();

            int pageNumber = 1;
            for (PDPage page : document.getPages()) {
                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);
                String pageText = stripper.getText(document);
                if (pageText != null && !pageText.isBlank()) {
                    content.add(pageText.trim());
                }
                List<String> images = extractImagesFromPdfPage(page, pageNumber, filename, ImageCaptioner.SAVED_IMAGE_DIR);
                if (!images.isEmpty()) {
                    captionFutures.add(imageCaptioner.captionImages(images));
                }
                pageNumber++;
            }
            return new ParseResult(content, captionFutures);
            }
        } catch (SecurityException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CompletionException(exception);
        }
    }

    /**
     * Mirrors Python's {@code PDFParser._extract_images_from_pdf_page} in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/pdf_parser.py}.
     */
    static List<String> extractImagesFromPdfPage(PDPage page, int pdfPageNum, String filename, String outputDir)
            throws IOException {
        List<String> images = new ArrayList<>();
        extractImages(page == null ? null : page.getResources(), pdfPageNum, filename, outputDir, images);
        return images;
    }

    private static void extractImages(
            PDResources resources,
            int pdfPageNum,
            String filename,
            String outputDir,
            List<String> images
    ) throws IOException {
        if (resources == null) {
            return;
        }
        for (COSName xObjectName : resources.getXObjectNames()) {
            PDXObject xObject = resources.getXObject(xObjectName);
            if (xObject instanceof PDImageXObject image) {
                Path outputDirectory = Path.of(outputDir == null || outputDir.isBlank()
                        ? ImageCaptioner.SAVED_IMAGE_DIR
                        : outputDir);
                Files.createDirectories(outputDirectory);
                Path imagePath = outputDirectory.resolve(filename + "__page_" + pdfPageNum
                        + "__img_" + images.size() + ".png");
                ImageIO.write(image.getImage(), "png", imagePath.toFile());
                images.add(imagePath.toString());
            } else if (xObject instanceof PDFormXObject form) {
                extractImages(form.getResources(), pdfPageNum, filename, outputDir, images);
            }
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

    private record ParseResult(
            List<String> content,
            List<CompletableFuture<List<String>>> captionFutures
    ) {
    }
}
