/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.checkpointing;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Utilities for packing skill directories and managing skill identifiers.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.checkpointing.skill_package} in
 * {@code openjiuwen/agent_evolving/checkpointing/skill_package.py}.</p>
 */
public final class SkillPackage {

    private static final Set<String> EXCLUDE_DIR_NAMES = Set.of("evolution", "archive", "__pycache__", ".git");
    private static final Set<String> EXCLUDE_FILE_NAMES = Set.of("evolutions.json");
    private static final int TAR_BLOCK_SIZE = 512;

    private SkillPackage() {
    }

    public static String newSkillId() {
        return "sk_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public static String readSkillIdFromContent(String content) {
        return parseTopLevelFrontmatter(content).getOrDefault("skill_id", "").strip();
    }

    public static SkillIdContent ensureSkillIdInContent(String content) {
        String original = content == null ? "" : content;
        String existing = readSkillIdFromContent(original);
        if (!existing.isBlank()) {
            return new SkillIdContent(original, existing);
        }

        String skillId = newSkillId();
        String stripped = stripLeadingBom(original);
        if (stripped.startsWith("---")) {
            int closing = stripped.indexOf("---", 3);
            if (closing != -1) {
                String head = stripped.substring(0, closing);
                String tail = stripped.substring(closing);
                if (!head.endsWith("\n")) {
                    head += "\n";
                }
                String updated = head + "skill_id: " + skillId + "\n" + tail;
                if (!updated.equals(original)) {
                    return new SkillIdContent(updated, skillId);
                }
                return new SkillIdContent(original, skillId);
            }
        }

        String updated = "---\nskill_id: " + skillId + "\n---\n\n" + stripped.stripLeading();
        return new SkillIdContent(updated.stripTrailing() + "\n", skillId);
    }

    public static byte[] packSkillDirectory(Path skillDir) {
        return packSkillDirectory(skillDir, null, null);
    }

    public static byte[] packSkillDirectory(Path skillDir, String skillMarkdownRelativePath, String skillMarkdownContent) {
        Path root = skillDir.toAbsolutePath().normalize();
        String overrideArcname = skillMarkdownRelativePath == null
                ? null
                : skillMarkdownRelativePath.replace('\\', '/');
        try {
            ByteArrayOutputStream rawTar = new ByteArrayOutputStream();
            for (Path file : listPackableFiles(root)) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                byte[] content = relative.equals(overrideArcname) && skillMarkdownContent != null
                        ? skillMarkdownContent.getBytes(StandardCharsets.UTF_8)
                        : Files.readAllBytes(file);
                long modifiedAt = relative.equals(overrideArcname) && skillMarkdownContent != null
                        ? 0
                        : Files.readAttributes(file, BasicFileAttributes.class).lastModifiedTime().toMillis() / 1000;
                writeTarEntry(rawTar, relative, content, modifiedAt);
            }
            rawTar.write(new byte[TAR_BLOCK_SIZE * 2]);

            ByteArrayOutputStream gzipped = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(gzipped)) {
                gzip.write(rawTar.toByteArray());
            }
            return gzipped.toByteArray();
        } catch (IOException exception) {
            throw storeError("failed to pack skill directory: " + exception.getMessage(), exception);
        }
    }

    public static void unpackSkillPackage(byte[] packageBytes, Path destDir) throws IOException {
        Path destination = destDir.toAbsolutePath().normalize();
        Files.createDirectories(destination);
        for (TarEntryData entry : readTarGz(packageBytes)) {
            Path output = destination.resolve(entry.name()).normalize();
            if (!output.startsWith(destination)) {
                continue;
            }
            if (entry.directory()) {
                Files.createDirectories(output);
                continue;
            }
            if (output.getParent() != null) {
                Files.createDirectories(output.getParent());
            }
            Files.write(output, entry.content());
        }
    }

    public static List<Path> listPackableFiles(Path skillDir) throws IOException {
        Path root = skillDir.toAbsolutePath().normalize();
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> shouldPackRelative(root.relativize(path)))
                    .sorted(Comparator.comparing(path -> root.relativize(path).toString().replace('\\', '/')))
                    .toList();
        }
    }

    static boolean shouldPackRelative(Path relative) {
        if (relative == null || relative.getNameCount() == 0) {
            return false;
        }
        String first = relative.getName(0).toString();
        if (EXCLUDE_DIR_NAMES.contains(first)) {
            return false;
        }
        String fileName = relative.getFileName().toString();
        return !EXCLUDE_FILE_NAMES.contains(fileName) && !fileName.startsWith(".");
    }

    private static Map<String, String> parseTopLevelFrontmatter(String content) {
        String text = (content == null ? "" : content).strip();
        if (!text.startsWith("---")) {
            return Map.of();
        }
        int end = text.indexOf("---", 3);
        if (end == -1) {
            return Map.of();
        }
        Map<String, String> frontmatter = new LinkedHashMap<>();
        for (String line : text.substring(3, end).strip().split("\\n")) {
            if (line.isEmpty() || Character.isWhitespace(line.charAt(0)) || line.startsWith("-")) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            frontmatter.put(line.substring(0, colon).strip(), line.substring(colon + 1).strip());
        }
        return frontmatter;
    }

    private static String stripLeadingBom(String value) {
        String result = value;
        while (result.startsWith("\uFEFF")) {
            result = result.substring(1);
        }
        return result;
    }

    private static BaseError storeError(String message, Throwable cause) {
        return ErrorHelper.buildError(
                StatusCode.TOOLCHAIN_EVOLVING_SKILL_STORE_EXECUTION_ERROR,
                null,
                null,
                cause,
                Map.of("error_msg", message)
        );
    }

    private static void writeTarEntry(ByteArrayOutputStream output, String name, byte[] content, long modifiedAt)
            throws IOException {
        byte[] header = new byte[TAR_BLOCK_SIZE];
        String entryName = name;
        String prefix = "";
        if (entryName.getBytes(StandardCharsets.UTF_8).length > 100) {
            int slash = entryName.lastIndexOf('/');
            if (slash > 0) {
                prefix = entryName.substring(0, slash);
                entryName = entryName.substring(slash + 1);
            }
        }
        writeString(header, 0, 100, entryName);
        writeOctal(header, 100, 8, 0644);
        writeOctal(header, 108, 8, 0);
        writeOctal(header, 116, 8, 0);
        writeOctal(header, 124, 12, content.length);
        writeOctal(header, 136, 12, modifiedAt);
        Arrays.fill(header, 148, 156, (byte) ' ');
        header[156] = '0';
        writeString(header, 257, 6, "ustar");
        writeString(header, 263, 2, "00");
        writeString(header, 345, 155, prefix);
        long checksum = 0;
        for (byte item : header) {
            checksum += Byte.toUnsignedInt(item);
        }
        writeChecksum(header, checksum);
        output.write(header);
        output.write(content);
        int padding = TAR_BLOCK_SIZE - (content.length % TAR_BLOCK_SIZE);
        if (padding < TAR_BLOCK_SIZE) {
            output.write(new byte[padding]);
        }
    }

    private static void writeString(byte[] header, int offset, int length, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int count = Math.min(bytes.length, length);
        System.arraycopy(bytes, 0, header, offset, count);
    }

    private static void writeOctal(byte[] header, int offset, int length, long value) {
        String text = Long.toOctalString(value);
        String padded = "0".repeat(Math.max(0, length - text.length() - 1)) + text;
        writeString(header, offset, length - 1, padded);
        header[offset + length - 1] = 0;
    }

    private static void writeChecksum(byte[] header, long checksum) {
        String text = Long.toOctalString(checksum);
        String padded = "0".repeat(Math.max(0, 6 - text.length())) + text;
        writeString(header, 148, 6, padded);
        header[154] = 0;
        header[155] = (byte) ' ';
    }

    private static List<TarEntryData> readTarGz(byte[] packageBytes) throws IOException {
        List<TarEntryData> entries = new ArrayList<>();
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(packageBytes))) {
            byte[] header = new byte[TAR_BLOCK_SIZE];
            while (readFully(gzip, header) == TAR_BLOCK_SIZE) {
                if (isZeroBlock(header)) {
                    break;
                }
                String name = readString(header, 0, 100);
                String prefix = readString(header, 345, 155);
                if (!prefix.isEmpty()) {
                    name = prefix + "/" + name;
                }
                long size = readOctal(header, 124, 12);
                boolean directory = header[156] == '5';
                byte[] content = gzip.readNBytes(Math.toIntExact(size));
                long padding = (TAR_BLOCK_SIZE - (size % TAR_BLOCK_SIZE)) % TAR_BLOCK_SIZE;
                if (padding > 0) {
                    gzip.skipNBytes(padding);
                }
                entries.add(new TarEntryData(name, directory, content));
                Arrays.fill(header, (byte) 0);
            }
        }
        return entries;
    }

    private static int readFully(GZIPInputStream input, byte[] buffer) throws IOException {
        int total = 0;
        while (total < buffer.length) {
            int read = input.read(buffer, total, buffer.length - total);
            if (read < 0) {
                break;
            }
            total += read;
        }
        return total;
    }

    private static boolean isZeroBlock(byte[] block) {
        for (byte item : block) {
            if (item != 0) {
                return false;
            }
        }
        return true;
    }

    private static String readString(byte[] header, int offset, int length) {
        int end = offset;
        int max = offset + length;
        while (end < max && header[end] != 0) {
            end++;
        }
        return new String(header, offset, end - offset, StandardCharsets.UTF_8).strip();
    }

    private static long readOctal(byte[] header, int offset, int length) {
        String text = readString(header, offset, length).strip();
        return text.isEmpty() ? 0 : Long.parseLong(text, 8);
    }

    public record SkillIdContent(String content, String skillId) {
    }

    private record TarEntryData(String name, boolean directory, byte[] content) {
    }
}
