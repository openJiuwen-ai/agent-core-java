/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.workspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public class Workspace used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Workspace {
    @Builder.Default
    private String rootPath = "./";
    @Builder.Default
    private String language = "cn";
    @Builder.Default
    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, String> links = new LinkedHashMap<>();

    /**
     * root.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Path root() {
        return Path.of(rootPath).toAbsolutePath().normalize();
    }

    /**
     * getNodePath.
     * 
     * @param nodeName nodeName
     * @return the result
     * @since 0.1.7
     */
    public Path getNodePath(String nodeName) {
        return root().resolve(nodeName);
    }

    /**
     * linkTeam.
     * 
     * @param teamId teamId
     * @param targetPath targetPath
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    public Path linkTeam(String teamId, String targetPath) throws IOException {
        Path linkDir = root().resolve(".team");
        Files.createDirectories(linkDir);
        Path link = linkDir.resolve(teamId);
        if (!Files.exists(link)) {
            Files.createDirectories(Path.of(targetPath));
            try {
                Files.createSymbolicLink(link, Path.of(targetPath));
            } catch (UnsupportedOperationException | IOException ex) {
                Files.createDirectories(link);
            }
        }
        links.put("team:" + teamId, link.toString());
        return link;
    }

    /**
     * unlinkTeam.
     * 
     * @param teamId teamId
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    public boolean unlinkTeam(String teamId) throws IOException {
        Path link = root().resolve(".team").resolve(teamId);
        if (!Files.exists(link)) {
            return false;
        }
        Files.deleteIfExists(link);
        links.remove("team:" + teamId);
        return true;
    }
}
