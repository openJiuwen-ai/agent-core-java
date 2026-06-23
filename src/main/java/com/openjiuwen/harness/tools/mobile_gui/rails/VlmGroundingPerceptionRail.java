/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.rails;

import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.tools.mobile_gui.MobileGuiRuntimeSettings;
import com.openjiuwen.harness.tools.mobile_gui.rails.VlmRailUtils;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Captures VLM observation metadata for grounding prompts.
 *
 * <p>Mirrors Python's {@code VlmGroundingPerceptionRail} in
 * {@code openjiuwen/harness/tools/mobile_gui/rails/vlm_grounding_perception_rail.py}.</p>
 */
public class VlmGroundingPerceptionRail extends DeepAgentRail {

    public static final String VLM_OBSERVATION_META_EXTRA_KEY = "_vlm_observation_meta";

    private final MobileGuiRuntimeSettings settings;
    private final int maxWidth;
    private final int jpegQuality;
    private final int coordinateScale;
    private final int claudeImageWidth;
    private final int claudeImageHeight;
    private final int opusMaxDimension;
    private final boolean useAdaptiveResize;
    private final boolean useClaudeSize;
    private final boolean useUnitScale;

    public VlmGroundingPerceptionRail(MobileGuiRuntimeSettings settings) {
        this(settings, "");
    }

    public VlmGroundingPerceptionRail(MobileGuiRuntimeSettings settings, String modelName) {
        this.settings = settings == null ? MobileGuiRuntimeSettings.fromEnv() : settings;
        this.maxWidth = this.settings.getVlmGroundingMaxWidth();
        this.jpegQuality = this.settings.getVlmGroundingJpegQuality();
        this.coordinateScale = this.settings.getVlmCoordinateScale();
        this.claudeImageWidth = this.settings.getVlmClaudeImageWidth();
        this.claudeImageHeight = this.settings.getVlmClaudeImageHeight();
        this.opusMaxDimension = this.settings.getVlmClaudeOpusMaxDimension();
        String normalizedModelName = modelName == null ? "" : modelName.toLowerCase();
        this.useAdaptiveResize = normalizedModelName.contains("opus-4") || normalizedModelName.contains("opus_4");
        this.useClaudeSize = normalizedModelName.contains("claude") && !useAdaptiveResize;
        this.useUnitScale = normalizedModelName.contains("kimi-k");
    }

    @Override
    public void beforeModelCall(CallbackContext ctx) {
        if (ctx != null) {
            ctx.put(VLM_OBSERVATION_META_EXTRA_KEY, Map.of(
                    "coordinate_scale", settings.getVlmCoordinateScale(),
                    "max_width", settings.getVlmGroundingMaxWidth(),
                    "jpeg_quality", settings.getVlmGroundingJpegQuality()
            ));
        }
    }

    public String appendObservationFooter(String text, Map<String, Object> meta) {
        Object foregroundApp = meta == null ? null : meta.get("foreground_app");
        return VlmRailUtils.appendVlmObservationMetaFooter(text, foregroundApp == null ? "" : String.valueOf(foregroundApp));
    }

    BufferedImage prepareImageForModel(BufferedImage image) {
        if (useAdaptiveResize) {
            return adaptiveResize(image, opusMaxDimension);
        }
        if (useClaudeSize) {
            return resize(image, claudeImageWidth, claudeImageHeight);
        }
        return resizeToMaxWidth(image);
    }

    int[] activeCoordinateScale(BufferedImage displayedImage) {
        if (displayedImage != null && (useAdaptiveResize || useClaudeSize)) {
            return new int[]{displayedImage.getWidth(), displayedImage.getHeight()};
        }
        if (useUnitScale) {
            return new int[]{1, 1};
        }
        return new int[]{coordinateScale, coordinateScale};
    }

    String coordinateInstruction(int scaleX, int scaleY) {
        if (scaleX == scaleY) {
            return "Coordinates are normalized numbers in [0, " + scaleX + "] for both axes; "
                    + "(0, 0) is top-left and max scale is bottom-right.";
        }
        return "Coordinates are pixel positions on the screenshot sent to the model: "
                + "x in [0, " + scaleX + "], y in [0, " + scaleY + "].";
    }

    String pilToBase64(BufferedImage image) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            writeJpeg(toRgb(image), output);
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception exception) {
            throw new IllegalStateException("failed to encode screenshot as JPEG", exception);
        }
    }

    String getForegroundApp(Object device) {
        try {
            Method method = device.getClass().getDeclaredMethod("app_current");
            method.setAccessible(true);
            Object info = method.invoke(device);
            if (info instanceof Map<?, ?> map) {
                Object packageName = map.get("package");
                return packageName == null ? "Unknown" : String.valueOf(packageName);
            }
            return "Unknown";
        } catch (Exception exception) {
            return "Unknown";
        }
    }

    private BufferedImage resizeToMaxWidth(BufferedImage image) {
        BufferedImage rgb = toRgb(image);
        if (rgb.getWidth() <= maxWidth) {
            return rgb;
        }
        double ratio = (double) maxWidth / rgb.getWidth();
        return resize(rgb, maxWidth, (int) (rgb.getHeight() * ratio));
    }

    private BufferedImage adaptiveResize(BufferedImage image, int maxDimension) {
        int maxImageDimension = Math.max(image.getWidth(), image.getHeight());
        if (maxImageDimension <= maxDimension) {
            return image;
        }
        double scale = (double) maxDimension / maxImageDimension;
        return resize(image, (int) (image.getWidth() * scale), (int) (image.getHeight() * scale));
    }

    private static BufferedImage resize(BufferedImage image, int width, int height) {
        Image scaled = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(scaled, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    private static BufferedImage toRgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rgb;
    }

    private void writeJpeg(BufferedImage image, ByteArrayOutputStream output) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            ImageIO.write(image, "jpeg", output);
            return;
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(Math.max(0.0f, Math.min(1.0f, jpegQuality / 100.0f)));
            }
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
    }
}
