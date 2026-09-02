/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers.jiuwenbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Utility class that handles uploading preserve files to a newly created sandbox.
 * 
 * @version 1.0
 * @since 0.1.7
 */
public final class PreserveFilesUpload {
    private static final Logger logger = LoggerFactory.getLogger(PreserveFilesUpload.class);

    /**
     * PreserveFilesUpload.
     * 
     * @since 0.1.7
     */
    private PreserveFilesUpload() {
    }

    /**
     * Immutable record representing a (hostPath, sandboxPath) file pair for upload.
     * 
     * @since 0.1.7
     */
    public record FilePair(String hostPath, String sandboxPath) {
    }

    /**
     * Upload preserve files to a sandbox and return the count of successful uploads.
     * 
     * @param client the JiuwenBox HTTP client for API calls
     * @param sandboxId the target sandbox ID
     * @param uploadEntries the list of upload entry maps (each with host_path, sandbox_path, kind)
     * @return the number of successfully uploaded files
     * @since 0.1.7
     */
    public static int uploadPreserveFiles(JiuwenBoxClient client, String sandboxId, Object uploadEntries) {
        if (uploadEntries == null || !(uploadEntries instanceof List)) {
            return 0;
        }
        List<FilePair> pairs = iterHostFilesForUpload(uploadEntries);
        if (pairs.isEmpty()) {
            return 0;
        }
        int uploaded = 0;
        for (FilePair pair : pairs) {
            try {
                byte[] content = Files.readAllBytes(Paths.get(pair.hostPath()));
                client.uploadBytes(sandboxId, pair.sandboxPath(), content);
                uploaded++;
            } catch (IOException exc) {
                logger.warn("[jiuwenbox] preserve_files read host file failed ({})", pair.hostPath(), exc);
            } catch (Exception exc) {
                logger.warn("[jiuwenbox] preserve_files upload failed ({}) -> {})", pair.hostPath(), pair.sandboxPath(),
                        exc);
            }
        }
        logger.info("[jiuwenbox] preserve_files uploaded {}/{} files to sandbox={}", uploaded, pairs.size(), sandboxId);
        return uploaded;
    }

    /**
     * Expand upload entries into (hostPath, sandboxPath) pairs for upload.
     * 
     * @param uploadEntries the list of upload entry maps to expand
     * @return the list of FilePair records ready for upload; empty list if no valid entries
     * @since 0.1.7
     */
    public static List<FilePair> iterHostFilesForUpload(Object uploadEntries) {
        if (!(uploadEntries instanceof List)) {
            return Collections.emptyList();
        }
        List<FilePair> pairs = new ArrayList<>();
        List<?> entries = (List<?>) uploadEntries;
        for (Object entryObj : entries) {
            if (!(entryObj instanceof Map)) {
                continue;
            }
            Map<?, ?> entry = (Map<?, ?>) entryObj;
            Object hostPathRaw = entry.get("host_path");
            String hostPath = hostPathRaw != null ? String.valueOf(hostPathRaw).trim() : "";
            Object sandboxPathRaw = entry.get("sandbox_path");
            String sandboxPath = sandboxPathRaw != null ? String.valueOf(sandboxPathRaw).trim() : "";
            Object kindRaw = entry.get("kind");
            String kind = kindRaw != null ? String.valueOf(kindRaw).trim().toLowerCase(Locale.ROOT) : "";
            if (hostPath.isEmpty() || sandboxPath.isEmpty()) {
                continue;
            }
            Path hostRoot = Paths.get(hostPath);
            if ("directory".equals(kind) || Files.isDirectory(hostRoot)) {
                if (!Files.isDirectory(hostRoot)) {
                    continue;
                }
                try (Stream<Path> walkStream = Files.walk(hostRoot)) {
                    List<Path> subFiles = walkStream.filter(Files::isRegularFile).toList();
                    for (Path sub : subFiles) {
                        Path rel = hostRoot.relativize(sub);
                        String subSandboxPath = sandboxPath + "/" + rel.toString().replace('\\', '/');
                        pairs.add(new FilePair(sub.toString(), subSandboxPath));
                    }
                } catch (IOException exc) {
                    logger.warn("[jiuwenbox] preserve_files walk directory failed ({})", hostPath, exc);
                }
            } else {
                if (Files.isRegularFile(hostRoot)) {
                    pairs.add(new FilePair(hostRoot.toString(), sandboxPath));
                }
            }
        }
        return pairs;
    }
}
