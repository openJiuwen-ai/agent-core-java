/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core.utils;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Mirrors Python's {@code path_to_file_uri} and {@code file_uri_to_path} in
 * {@code openjiuwen/harness/lsp/core/utils/file_uri.py}.
 */
public final class FileUriUtils {

    private static final boolean WINDOWS = System.getProperty("os.name", "")
            .toLowerCase()
            .contains("win");

    private FileUriUtils() {
    }

    public static String pathToFileUri(String filePath) {
        Path absolute = Paths.get(filePath).toAbsolutePath().normalize();
        String posixPath = absolute.toString().replace('\\', '/');
        if (WINDOWS) {
            return "file:///" + posixPath;
        }
        return "file://" + URLEncoder.encode(posixPath, StandardCharsets.UTF_8).replace("%2F", "/");
    }

    public static String fileUriToPath(String uri) {
        if (uri == null || !uri.startsWith("file://")) {
            return uri;
        }

        String path = uri.substring(7);
        try {
            path = URLDecoder.decode(path, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            // Keep the raw path when percent encoding is malformed.
        }

        if (WINDOWS) {
            if (path.length() >= 3 && path.charAt(0) == '/' && path.charAt(2) == ':') {
                path = path.substring(1);
            }
            path = path.replace('/', '\\');
            if (path.length() >= 2 && path.charAt(1) == ':') {
                path = Character.toUpperCase(path.charAt(0)) + path.substring(1);
            }
        }
        return path;
    }
}
