/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core.utils;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * File URI <-> filesystem path conversion utilities.
 *
 * <p>Mirrors Python's {@code file_uri.py} in {@code openjiuwen.harness.lsp.core.utils}.
 */
public final class FileUriUtils {

    private FileUriUtils() {
    }

    public static String pathToFileUri(String filePath) {
        Path absPath = Paths.get(filePath).toAbsolutePath().normalize();
        return absPath.getRoot() != null && absPath.getRoot().toString().contains(":")
                ? "file:///" + absPath.toString().replace('\\', '/')
                : "file://" + URLEncoder.encode(absPath.toString().replace('\\', '/'), StandardCharsets.UTF_8);
    }

    public static String fileUriToPath(String uri) {
        if (uri == null || !uri.startsWith("file://")) {
            return uri;
        }
        String path = uri.substring(7);
        try {
            path = URLDecoder.decode(path, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            // keep raw when malformed
        }
        if (path.startsWith("/") && path.length() >= 3 && path.charAt(2) == ':') {
            path = path.substring(1);
        }
        return path.replace('/', '\\');
    }

    public static String normalizeUri(String uri) {
        try {
            return new URI(uri).normalize().toString();
        } catch (URISyntaxException e) {
            return uri;
        }
    }
}
