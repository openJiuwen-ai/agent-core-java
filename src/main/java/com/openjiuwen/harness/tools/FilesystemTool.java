/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Minimal local filesystem tool surface aligned with the Python harness tool set.
 * 
 * @since 0.1.7
 */
public class FilesystemTool {
    private final Path root;

    /**
     * FilesystemTool.
     * 
     * @param rootPath rootPath
     * @since 0.1.7
     */
    public FilesystemTool(String rootPath) {
        this.root = Path.of(rootPath).toAbsolutePath().normalize();
    }

    /**
     * readFile.
     * 
     * @param relativePath relativePath
     * @return the result
     * @since 0.1.7
     */
    public ToolOutput readFile(String relativePath) {
        try {
            Path target = resolve(relativePath);
            return ToolOutput.builder().success(true).data(Files.readString(target)).build();
        } catch (IOException ex) {
            return ToolOutput.builder().success(false).error(ex.getMessage()).build();
        }
    }

    /**
     * writeFile.
     * 
     * @param relativePath relativePath
     * @param content content
     * @return the result
     * @since 0.1.7
     */
    public ToolOutput writeFile(String relativePath, String content) {
        try {
            Path target = resolve(relativePath);
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
            return ToolOutput.builder().success(true).data(target.toString()).build();
        } catch (IOException ex) {
            return ToolOutput.builder().success(false).error(ex.getMessage()).build();
        }
    }

    /**
     * listFiles.
     * 
     * @param relativeDir relativeDir
     * @return the result
     * @since 0.1.7
     */
    public ToolOutput listFiles(String relativeDir) {
        try (Stream<Path> stream = Files.list(resolve(relativeDir))) {
            List<String> files = stream.sorted(Comparator.comparing(Path::toString))
                    .map(path -> root.relativize(path).toString()).toList();
            return ToolOutput.builder().success(true).data(files).build();
        } catch (IOException ex) {
            return ToolOutput.builder().success(false).error(ex.getMessage()).build();
        }
    }

    /**
     * searchText.
     * 
     * @param relativeDir relativeDir
     * @param needle needle
     * @return the result
     * @since 0.1.7
     */
    public ToolOutput searchText(String relativeDir, String needle) {
        try (Stream<Path> stream = Files.walk(resolve(relativeDir))) {
            List<String> matches = stream.filter(Files::isRegularFile).filter(path -> contains(path, needle))
                    .sorted(Comparator.comparing(Path::toString)).map(path -> root.relativize(path).toString())
                    .toList();
            return ToolOutput.builder().success(true).data(matches).build();
        } catch (IOException ex) {
            return ToolOutput.builder().success(false).error(ex.getMessage()).build();
        }
    }

    /**
     * contains.
     * 
     * @param path path
     * @param needle needle
     * @return the result
     * @since 0.1.7
     */
    private boolean contains(Path path, String needle) {
        try {
            return Files.readString(path).contains(needle);
        } catch (IOException ex) {
            return false;
        }
    }

    /**
     * resolve.
     * 
     * @param relativePath relativePath
     * @return the result
     * @since 0.1.7
     */
    private Path resolve(String relativePath) {
        return root.resolve(relativePath).normalize();
    }
}
