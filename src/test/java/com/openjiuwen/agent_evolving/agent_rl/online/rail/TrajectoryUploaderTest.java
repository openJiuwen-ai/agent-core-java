package com.openjiuwen.agent_evolving.agent_rl.online.rail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TrajectoryUploader} in
 * {@code openjiuwen/agent_evolving/agent_rl/online/rail/uploader.py}.
 */
class TrajectoryUploaderTest {

    @TempDir
    Path tempDir;

    @Test
    void queueDropKeepsNewestPayloadWhenCapacityIsExceeded() throws Exception {
        CountDownLatch firstRequestStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstRequest = new CountDownLatch(1);
        List<String> postedIds = new ArrayList<>();
        TrajectoryUploader.BatchPoster poster = (url, payload, headers) -> {
            postedIds.add(String.valueOf(payload.get("id")));
            if ("one".equals(payload.get("id"))) {
                firstRequestStarted.countDown();
                assertThat(releaseFirstRequest.await(5, TimeUnit.SECONDS)).isTrue();
            }
            return new TrajectoryUploader.ResponseSnapshot(202, "");
        };
        TrajectoryUploader uploader = new TrajectoryUploader(
                "http://gateway.local/",
                1,
                0,
                0.0d,
                tempDir.resolve("wal"),
                "",
                poster,
                false
        );

        uploader.enqueue(new FakeBatch("one")).join();
        assertThat(firstRequestStarted.await(5, TimeUnit.SECONDS)).isTrue();
        uploader.enqueue(new FakeBatch("two")).join();
        uploader.enqueue(new FakeBatch("three")).join();
        releaseFirstRequest.countDown();

        uploader.shutdown().join();

        assertThat(postedIds).containsExactly("one", "three");
        assertThat(uploader.getQueueDropTotal()).isEqualTo(1);
    }

    @Test
    void http4xxIsCountedAndNotPersistedToWal() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        TrajectoryUploader uploader = new TrajectoryUploader(
                "http://gateway.local",
                8,
                3,
                0.0d,
                tempDir.resolve("wal"),
                "secret",
                (url, payload, headers) -> {
                    attempts.incrementAndGet();
                    assertThat(url).isEqualTo("http://gateway.local/v1/gateway/upload/batch");
                    assertThat(headers).containsEntry("Authorization", "Bearer secret");
                    return new TrajectoryUploader.ResponseSnapshot(422, "{\"detail\":\"bad input\"}");
                },
                false
        );

        uploader.enqueue(new FakeBatch("bad")).join();
        uploader.shutdown().join();

        assertThat(attempts.get()).isEqualTo(1);
        assertThat(uploader.getHttp4xxTotal()).isEqualTo(1);
        assertThat(listWalFiles(tempDir.resolve("wal"))).isEmpty();
    }

    @Test
    void exhaustedRetriesWriteWalPayload() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        TrajectoryUploader uploader = new TrajectoryUploader(
                "http://gateway.local",
                8,
                2,
                0.0d,
                tempDir.resolve("wal"),
                "",
                (url, payload, headers) -> {
                    attempts.incrementAndGet();
                    return new TrajectoryUploader.ResponseSnapshot(503, "{\"detail\":\"retry later\"}");
                },
                false
        );

        uploader.enqueue(new FakeBatch("retry-me")).join();
        uploader.shutdown().join();

        assertThat(attempts.get()).isEqualTo(3);
        List<Path> walFiles = listWalFiles(tempDir.resolve("wal"));
        assertThat(walFiles).hasSize(1);
        assertThat(Files.readString(walFiles.get(0), StandardCharsets.UTF_8)).contains("\"id\":\"retry-me\"");
    }

    @Test
    void replayWalResendsStoredPayloadAndDeletesWalFile() throws Exception {
        List<String> postedIds = new ArrayList<>();
        Path walDir = tempDir.resolve("wal");
        Files.createDirectories(walDir);
        Files.writeString(walDir.resolve("0001.json"), "{\"id\":\"from-wal\"}", StandardCharsets.UTF_8);
        TrajectoryUploader uploader = new TrajectoryUploader(
                "http://gateway.local",
                8,
                0,
                0.0d,
                walDir,
                "",
                (url, payload, headers) -> {
                    postedIds.add(String.valueOf(payload.get("id")));
                    return new TrajectoryUploader.ResponseSnapshot(202, "");
                },
                false
        );

        uploader.replayWal().join();
        uploader.shutdown().join();

        assertThat(postedIds).containsExactly("from-wal");
        assertThat(listWalFiles(walDir)).isEmpty();
    }

    private static List<Path> listWalFiles(Path walDir) throws Exception {
        if (!Files.exists(walDir)) {
            return List.of();
        }
        try (var stream = Files.list(walDir)) {
            return stream.sorted().toList();
        }
    }

    private record FakeBatch(String id) {
        Map<String, Object> toDict() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", id);
            return payload;
        }
    }
}
