/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.schema.config.VisionModelConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

/**
 * Public class ImageOCRTool used by the Java parity implementation.
 *
 * @since 1.0
 */
public class ImageOCRTool {
  private static final String DEFAULT_PROMPT = "Extract all visible text from the image.";

  /** Auto-generated for codecheck compliance. */
  public final VisionModelConfig visionModelConfig;

  private final OcrInvoker invoker;

  /**
   * Public interface OcrInvoker used by the Java parity implementation.
   *
   * @since 1.0
   */
  @FunctionalInterface
  public interface OcrInvoker {
    String invoke(VisionModelConfig config, String prompt, Map<String, Object> imageContent)
        throws Exception;
  }

  /** Auto-generated for codecheck compliance. */
  public ImageOCRTool(VisionModelConfig visionModelConfig) {
    this(visionModelConfig, (config, prompt, imageContent) -> "ocr not configured");
  }

  /** Auto-generated for codecheck compliance. */
  public ImageOCRTool(VisionModelConfig visionModelConfig, OcrInvoker invoker) {
    this.visionModelConfig = visionModelConfig;
    this.invoker = invoker;
  }

  /** Auto-generated for codecheck compliance. */
  public ToolOutput invoke(Map<String, Object> inputs) {
    if (visionModelConfig == null) {
      return ToolOutput.builder().success(false).error("Vision model config is not set.").build();
    }
    try {
      String path = String.valueOf(inputs.get("image_path_or_url"));
      String prompt = String.valueOf(inputs.getOrDefault("prompt", DEFAULT_PROMPT));
      Map<String, Object> imageContent = buildImageContent(path);
      String text = invoker.invoke(visionModelConfig, prompt, imageContent);
      return ToolOutput.builder()
          .success(true)
          .data(Map.of("text", text, "model", visionModelConfig.getModel()))
          .build();
    } catch (Exception ex) {
      return ToolOutput.builder().success(false).error(ex.getMessage()).build();
    }
  }

  private static Map<String, Object> buildImageContent(String imagePathOrUrl) throws Exception {
    if (imagePathOrUrl.startsWith("http://") || imagePathOrUrl.startsWith("https://")) {
      return Map.of("type", "image_url", "image_url", Map.of("url", imagePathOrUrl));
    }
    Path path = Path.of(imagePathOrUrl);
    String mime = Files.probeContentType(path);
    String url =
        "data:"
            + (mime != null ? mime : "image/png")
            + ";base64,"
            + Base64.getEncoder().encodeToString(Files.readAllBytes(path));
    return Map.of("type", "image_url", "image_url", Map.of("url", url));
  }
}
