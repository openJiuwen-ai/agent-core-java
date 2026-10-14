/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.multimodal;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Judges the effective image MIME type from actual material bytes.
 *
 * <p>Magic-byte detection wins over file names and caller declarations; an
 * unrecognized payload yields empty so callers must fail or require an
 * explicit declaration instead of defaulting to {@code image/png}.</p>
 *
 * @since 0.1.17
 */
public final class ImageMimeSniffer {
    private static final byte[] PNG_MAGIC = {
            (byte) 0x89, 'P', 'N', 'G', (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A
    };
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] GIF87A_MAGIC = "GIF87a".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] GIF89A_MAGIC = "GIF89a".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] BMP_MAGIC = "BM".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] RIFF_MAGIC = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP_MAGIC = "WEBP".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] TIFF_LE_MAGIC = {'I', 'I', 0x2A, 0x00};
    private static final byte[] TIFF_BE_MAGIC = {'M', 'M', 0x00, 0x2A};
    private static final byte[] FTYP_MAGIC = "ftyp".getBytes(StandardCharsets.US_ASCII);
    private static final List<String> HEIC_BRANDS = List.of("heic", "heix", "heim", "heis", "mif1", "msf1");
    private static final int SVG_SCAN_LIMIT = 1024;
    private static final int FTYP_OFFSET = 4;
    private static final int BRAND_OFFSET = 8;
    private static final int BRAND_LENGTH = 4;
    private static final int WEBP_OFFSET = 8;

    private ImageMimeSniffer() {
    }

    /**
     * Detect the image MIME type carried by the given bytes.
     *
     * @param data material bytes; may be {@code null} or empty
     * @return detected MIME type, or empty when the bytes are not a
     *         recognizable image container
     */
    public static Optional<String> sniff(byte[] data) {
        if (data == null || data.length == 0) {
            return Optional.empty();
        }
        if (startsWith(data, PNG_MAGIC)) {
            return Optional.of("image/png");
        }
        if (startsWith(data, JPEG_MAGIC)) {
            return Optional.of("image/jpeg");
        }
        if (startsWith(data, GIF87A_MAGIC) || startsWith(data, GIF89A_MAGIC)) {
            return Optional.of("image/gif");
        }
        if (isWebp(data)) {
            return Optional.of("image/webp");
        }
        if (startsWith(data, BMP_MAGIC)) {
            return Optional.of("image/bmp");
        }
        if (isTiff(data)) {
            return Optional.of("image/tiff");
        }
        if (isHeic(data)) {
            return Optional.of("image/heic");
        }
        if (looksLikeSvg(data)) {
            return Optional.of("image/svg+xml");
        }
        return Optional.empty();
    }

    private static boolean isWebp(byte[] data) {
        return startsWith(data, RIFF_MAGIC) && matchesAt(data, WEBP_MAGIC, WEBP_OFFSET);
    }

    private static boolean isTiff(byte[] data) {
        return startsWith(data, TIFF_LE_MAGIC) || startsWith(data, TIFF_BE_MAGIC);
    }

    private static boolean isHeic(byte[] data) {
        if (!matchesAt(data, FTYP_MAGIC, FTYP_OFFSET) || data.length < BRAND_OFFSET + BRAND_LENGTH) {
            return false;
        }
        String brand = new String(data, BRAND_OFFSET, BRAND_LENGTH, StandardCharsets.US_ASCII)
                .toLowerCase(Locale.ROOT);
        return HEIC_BRANDS.contains(brand);
    }

    private static boolean looksLikeSvg(byte[] data) {
        int limit = Math.min(data.length, SVG_SCAN_LIMIT);
        String head = new String(data, 0, limit, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        return head.contains("<svg");
    }

    private static boolean startsWith(byte[] data, byte[] magic) {
        return matchesAt(data, magic, 0);
    }

    private static boolean matchesAt(byte[] data, byte[] magic, int offset) {
        if (data.length < offset + magic.length) {
            return false;
        }
        for (int index = 0; index < magic.length; index++) {
            if (data[offset + index] != magic[index]) {
                return false;
            }
        }
        return true;
    }
}
