/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.store.object;

import com.openjiuwen.core.foundation.store.object.S3ObjectStorageClient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for S3ObjectStorageClient.
 * <p>
 * Mirrors Python's {@code AioBotoClient} tests in
 * {@code tests/unit_tests/core/foundation/store/object/test_aioboto_client.py}.
 */
@DisplayName("AioBotoClient Tests")
class TestAiobotoClient {

    @TempDir
    Path tempDir;

    private S3ObjectStorageClient clientWith(S3Client s3Client) {
        return new S3ObjectStorageClient(s3Client);
    }

    @Test
    @DisplayName("create bucket success")
    void testCreateBucketSuccess() {
        S3Client s3Client = mock(S3Client.class);
        S3ObjectStorageClient client = clientWith(s3Client);

        assertTrue(client.createBucket("test-bucket", "ap-southeast-1"));

        verify(s3Client).createBucket(argThat((CreateBucketRequest request) ->
                "test-bucket".equals(request.bucket())
                        && "ap-southeast-1".equals(request.createBucketConfiguration().locationConstraintAsString())));
    }

    @Test
    @DisplayName("create bucket error")
    void testCreateBucketError() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.createBucket(any(CreateBucketRequest.class))).thenThrow(s3Error("create failed"));
        S3ObjectStorageClient client = clientWith(s3Client);

        assertFalse(client.createBucket("test-bucket", "ap-southeast-1"));
        verify(s3Client).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    @DisplayName("delete bucket success")
    void testDeleteBucketSuccess() {
        S3Client s3Client = mock(S3Client.class);
        S3ObjectStorageClient client = clientWith(s3Client);

        assertTrue(client.deleteBucket("test-bucket"));

        verify(s3Client).deleteBucket(argThat((DeleteBucketRequest request) ->
                "test-bucket".equals(request.bucket())));
    }

    @Test
    @DisplayName("delete bucket error")
    void testDeleteBucketError() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.deleteBucket(any(DeleteBucketRequest.class))).thenThrow(s3Error("delete failed"));
        S3ObjectStorageClient client = clientWith(s3Client);

        assertFalse(client.deleteBucket("test-bucket"));
        verify(s3Client).deleteBucket(any(DeleteBucketRequest.class));
    }

    @Test
    @DisplayName("upload file success")
    void testUploadFileSuccess() throws Exception {
        S3Client s3Client = mock(S3Client.class);
        S3ObjectStorageClient client = clientWith(s3Client);
        Path filePath = tempDir.resolve("test.txt");
        Files.writeString(filePath, "hello");

        assertTrue(client.uploadFile("bucket", "obj", filePath.toString()));

        verify(s3Client).putObject(argThat((PutObjectRequest request) ->
                "bucket".equals(request.bucket()) && "obj".equals(request.key())), eq(filePath));
    }

    @Test
    @DisplayName("upload file error")
    void testUploadFileError() throws Exception {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.putObject(any(PutObjectRequest.class), any(Path.class))).thenThrow(s3Error("upload failed"));
        S3ObjectStorageClient client = clientWith(s3Client);
        Path filePath = tempDir.resolve("test.txt");
        Files.writeString(filePath, "hello");

        assertFalse(client.uploadFile("bucket", "obj", filePath.toString()));
        verify(s3Client).putObject(any(PutObjectRequest.class), eq(filePath));
    }

    @Test
    @DisplayName("download file success")
    void testDownloadFileSuccess() throws Exception {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.getObject(any(GetObjectRequest.class), any(Path.class))).thenAnswer(invocation -> {
            Files.writeString(invocation.getArgument(1), "hello world");
            return GetObjectResponse.builder().build();
        });
        S3ObjectStorageClient client = clientWith(s3Client);
        Path filePath = tempDir.resolve("out.txt");

        assertTrue(client.downloadFile("bucket", "obj", filePath.toString()));

        verify(s3Client).getObject(argThat((GetObjectRequest request) ->
                "bucket".equals(request.bucket()) && "obj".equals(request.key())), eq(filePath));
        assertTrue(Files.size(filePath) > 0);
    }

    @Test
    @DisplayName("download file error")
    void testDownloadFileError() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.getObject(any(GetObjectRequest.class), any(Path.class))).thenThrow(s3Error("download failed"));
        S3ObjectStorageClient client = clientWith(s3Client);

        assertFalse(client.downloadFile("bucket", "obj", tempDir.resolve("out.txt").toString()));
        verify(s3Client).getObject(any(GetObjectRequest.class), any(Path.class));
    }

    @Test
    @DisplayName("delete object error")
    void testDeleteObjectError() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(s3Error("delete object failed"));
        S3ObjectStorageClient client = clientWith(s3Client);

        assertFalse(client.deleteObject("bucket", "obj"));
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("delete object success")
    void testDeleteObjectSuccess() {
        S3Client s3Client = mock(S3Client.class);
        S3ObjectStorageClient client = clientWith(s3Client);

        assertTrue(client.deleteObject("bucket", "obj"));

        verify(s3Client).deleteObject(argThat((DeleteObjectRequest request) ->
                "bucket".equals(request.bucket()) && "obj".equals(request.key())));
    }

    @Test
    @DisplayName("list objects success")
    void testListObjectsSuccess() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("a.txt").size(1L).build(),
                        S3Object.builder().key("b.txt").size(2L).build())
                .build());
        S3ObjectStorageClient client = clientWith(s3Client);

        List<Map<String, Object>> result = client.listObjects("bucket", "prefix", 100);

        assertNotNull(result);
        assertEquals(List.of("a.txt", "b.txt"), result.stream().map(item -> item.get("Key")).toList());
        verify(s3Client).listObjectsV2(argThat((ListObjectsV2Request request) ->
                "bucket".equals(request.bucket())
                        && "prefix".equals(request.prefix())
                        && request.maxKeys() == 100));
    }

    @Test
    @DisplayName("list objects error")
    void testListObjectsError() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenThrow(s3Error("list failed"));
        S3ObjectStorageClient client = clientWith(s3Client);

        assertNull(client.listObjects("bucket", "prefix", 100));
    }

    private static AwsServiceException s3Error(String message) {
        return S3Exception.builder().message(message).build();
    }
}
