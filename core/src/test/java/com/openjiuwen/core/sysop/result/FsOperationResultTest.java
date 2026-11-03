/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's fs-operation result surface in
 * {@code openjiuwen/core/sys_operation/result/fs_operation_result.py}.
 */
class FsOperationResultTest {

    @Test
    void readFileDataSupportsTextAndBinaryContent() {
        ReadFileData textData = ReadFileData.builder()
                .path("/tmp/readme.txt")
                .content("hello")
                .mode("text")
                .build();
        ReadFileData bytesData = ReadFileData.builder()
                .path("/tmp/blob.bin")
                .content(new byte[] {1, 2, 3})
                .mode("bytes")
                .build();

        assertEquals("/tmp/readme.txt", textData.getPath());
        assertEquals("hello", textData.getContent());
        assertEquals("text", textData.getMode());
        assertEquals("/tmp/blob.bin", bytesData.getPath());
        assertEquals("bytes", bytesData.getMode());
        assertArrayEquals(new byte[] {1, 2, 3}, (byte[]) bytesData.getContent());
    }

    @Test
    void chunkAndTransferModelsPreserveBoundaryFields() {
        ReadFileChunkData readChunk = ReadFileChunkData.builder()
                .path("/tmp/readme.txt")
                .chunkContent("part-1")
                .mode("text")
                .chunkSize(6)
                .chunkIndex(0)
                .isLastChunk(false)
                .build();
        UploadFileChunkData uploadChunk = UploadFileChunkData.builder()
                .localPath("input.bin")
                .targetPath("/remote/input.bin")
                .chunkSize(512)
                .chunkIndex(2)
                .isLastChunk(true)
                .build();
        DownloadFileChunkData downloadChunk = DownloadFileChunkData.builder()
                .sourcePath("/remote/output.bin")
                .localPath("output.bin")
                .chunkSize(1024)
                .chunkIndex(1)
                .isLastChunk(false)
                .build();

        assertEquals("part-1", readChunk.getChunkContent());
        assertFalse(readChunk.isLastChunk());
        assertTrue(uploadChunk.isLastChunk());
        assertEquals("/remote/input.bin", uploadChunk.getTargetPath());
        assertEquals("output.bin", downloadChunk.getLocalPath());
        assertEquals(1, downloadChunk.getChunkIndex());
    }

    @Test
    void fileSystemAndSearchModelsPreserveNestedItems() {
        FileSystemItem file = FileSystemItem.builder()
                .name("notes.txt")
                .path("/tmp/notes.txt")
                .size(12)
                .modifiedTime("2026-06-08T00:00:00Z")
                .isDirectory(false)
                .type("txt")
                .build();
        FileSystemData listing = FileSystemData.builder()
                .totalCount(1)
                .listItems(List.of(file))
                .rootPath("/tmp")
                .recursive(true)
                .maxDepth(null)
                .build();
        SearchFilesData search = SearchFilesData.builder()
                .totalMatches(1)
                .matchingFiles(List.of(file))
                .searchPath("/tmp")
                .searchPattern("*.txt")
                .excludePatterns(List.of("*.bak"))
                .build();

        assertEquals(List.of(file), listing.getListItems());
        assertNull(listing.getMaxDepth());
        assertEquals("*.txt", search.getSearchPattern());
        assertEquals(List.of("*.bak"), search.getExcludePatterns());
    }

    @Test
    void resultEnvelopesRemainTyped() {
        FileSystemData listing = FileSystemData.builder()
                .totalCount(0)
                .listItems(List.of())
                .rootPath("/tmp")
                .recursive(false)
                .build();
        SearchFilesData search = SearchFilesData.builder()
                .totalMatches(0)
                .matchingFiles(List.of())
                .searchPath("/tmp")
                .searchPattern("*.java")
                .build();

        ListFilesResult listFilesResult = new ListFilesResult();
        listFilesResult.setData(listing);
        SearchFilesResult searchFilesResult = new SearchFilesResult();
        searchFilesResult.setData(search);

        assertSame(listing, listFilesResult.getData());
        assertSame(search, searchFilesResult.getData());
    }
}
