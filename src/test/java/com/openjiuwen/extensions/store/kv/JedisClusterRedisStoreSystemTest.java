package com.openjiuwen.extensions.store.kv;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;
import redis.clients.jedis.CommandArguments;
import redis.clients.jedis.ConnectionPool;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("system-test")
class JedisClusterRedisStoreSystemTest {

    private static final Duration READY_TIMEOUT = Duration.ofSeconds(90);
    private static RedisClusterFixture fixture;

    @BeforeAll
    static void startContainer() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is required for Redis Cluster system test");
        fixture = RedisClusterFixture.start();
    }

    @AfterAll
    static void stopContainer() {
        if (fixture != null) {
            fixture.close();
        }
    }

    @Test
    void redisClusterFixtureUsesRuntimePorts() throws Exception {
        Set<Integer> ports = fixture.clusterNodes().stream()
                .map(HostAndPort::getPort)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Integer> busPorts = fixture.busPorts();

        assertEquals(6, ports.size());
        assertEquals(6, busPorts.size());
        assertEquals(3, RedisClusterFixture.MAX_START_ATTEMPTS);
        assertTrue(ports.stream().noneMatch(RedisClusterFixture::isFixedRedisClusterPort));
        assertTrue(busPorts.stream().noneMatch(RedisClusterFixture::isFixedRedisClusterPort));
        assertTrue(ports.stream().allMatch(RedisClusterFixture::isValidClusterDataPort));
        try (Jedis jedis = new Jedis("127.0.0.1", ports.iterator().next())) {
            assertTrue(jedis.clusterInfo().contains("cluster_state:ok"));
        }
    }

    @Test
    void supportsClusterPrefixScanDeleteAndCoreOperations() throws Exception {
        try (JedisCluster jedisCluster = new JedisCluster(clusterNodes())) {
            JedisClusterRedisStore store = new JedisClusterRedisStore(jedisCluster);
            String prefix = "cluster-system:" + System.nanoTime() + ":";
            try {
                store.set(prefix + "alpha", Map.of("name", "Ada", "count", 1));
                store.set(prefix + "beta", List.of("two", 2));
                store.set(prefix + "delete-one", "first");
                store.set(prefix + "delete-two", "second");
                store.set(prefix + "ttl", "expires");
                List<String> crossMasterKeys = keysAcrossMasters(jedisCluster, prefix, 2);
                Set<String> crossMasterOwners = mastersContainingKeys(jedisCluster, crossMasterKeys);
                assertTrue(crossMasterOwners.size() >= 2,
                        "Expected selected prefix keys on at least two cluster masters, but found "
                                + crossMasterOwners.size() + " masters for " + crossMasterKeys);

                assertEquals(Map.of("name", "Ada", "count", 1), store.get(prefix + "alpha"));
                assertEquals(List.of("two", 2), store.get(prefix + "beta"));
                assertEquals(Arrays.asList(
                        Map.of("name", "Ada", "count", 1),
                        null,
                        List.of("two", 2)
                ), store.mget(List.of(prefix + "alpha", prefix + "missing", prefix + "beta")));

                Map<String, Object> byPrefix = store.getByPrefix(prefix);
                assertEquals(Map.of("name", "Ada", "count", 1), byPrefix.get(prefix + "alpha"));
                assertEquals(List.of("two", 2), byPrefix.get(prefix + "beta"));
                assertEquals("first", byPrefix.get(prefix + "delete-one"));
                assertEquals("second", byPrefix.get(prefix + "delete-two"));
                for (String key : crossMasterKeys) {
                    assertEquals(key.substring(prefix.length()), byPrefix.get(key));
                }
                assertFalse(byPrefix.containsKey(prefix + "missing"));

                store.deleteByPrefix(prefix + "delete-", 2);
                assertNull(store.get(prefix + "delete-one"));
                assertNull(store.get(prefix + "delete-two"));
                assertTrue(store.exists(prefix + "alpha"));

                String specialPrefix = prefix + "literal[1]?*\\:";
                store.set(specialPrefix + "keep", "special");
                store.set(prefix + "literal1X-other", "unrelated");

                Map<String, Object> specialByPrefix = store.getByPrefix(specialPrefix);
                assertEquals(Map.of(specialPrefix + "keep", "special"), specialByPrefix);

                store.deleteByPrefix(specialPrefix, 100);
                assertNull(store.get(specialPrefix + "keep"));
                assertEquals("unrelated", store.get(prefix + "literal1X-other"));

                store.refreshTtl(List.of(prefix + "ttl"), 30);
                assertTrue(jedisCluster.ttl(prefix + "ttl") > 0);
            } finally {
                cleanupPrefix(jedisCluster, prefix);
            }
        }
    }

    private static Set<HostAndPort> clusterNodes() {
        return fixture.clusterNodes();
    }

    private static List<String> keysAcrossMasters(JedisCluster jedisCluster, String prefix, int targetMasterCount) {
        List<String> selectedKeys = new ArrayList<>();
        Set<String> selectedMasters = new LinkedHashSet<>();
        for (int index = 0; index < 500 && selectedMasters.size() < targetMasterCount; index++) {
            String key = prefix + "cross-master-" + index;
            jedisCluster.set(key, "\"" + key.substring(prefix.length()) + "\"");
            Set<String> owners = mastersContainingKeys(jedisCluster, List.of(key));
            if (!owners.isEmpty() && selectedMasters.add(owners.iterator().next())) {
                selectedKeys.add(key);
            }
        }
        if (selectedMasters.size() < targetMasterCount) {
            fail("Could not place prefix keys on " + targetMasterCount + " Redis Cluster masters; found "
                    + selectedMasters.size() + " masters: " + selectedMasters);
        }
        return selectedKeys;
    }

    private static Set<String> mastersContainingKeys(JedisCluster jedisCluster, List<String> keys) {
        Set<String> remainingKeys = new LinkedHashSet<>(keys);
        Set<String> masters = new LinkedHashSet<>();
        int masterIndex = 0;
        for (ConnectionPool pool : jedisCluster.getClusterNodes().values()) {
            try (Jedis jedis = new Jedis(pool.getResource())) {
                if (!jedis.info("replication").contains("role:master")) {
                    continue;
                }
                String masterName = "master-" + masterIndex++;
                String cursor = ScanParams.SCAN_POINTER_START;
                do {
                    ScanResult<String> scanResult = jedis.scan(cursor, new ScanParams().count(500));
                    for (String key : scanResult.getResult()) {
                        if (remainingKeys.contains(key)) {
                            masters.add(masterName);
                            remainingKeys.remove(key);
                        }
                    }
                    cursor = scanResult.getCursor();
                } while (!ScanParams.SCAN_POINTER_START.equals(cursor) && !remainingKeys.isEmpty());
            }
            if (remainingKeys.isEmpty()) {
                break;
            }
        }
        return masters;
    }

    private static void cleanupPrefix(JedisCluster jedisCluster, String prefix) {
        for (ConnectionPool pool : jedisCluster.getClusterNodes().values()) {
            try (Jedis jedis = new Jedis(pool.getResource())) {
                if (!jedis.info("replication").contains("role:master")) {
                    continue;
                }
                String cursor = ScanParams.SCAN_POINTER_START;
                do {
                    ScanResult<String> scanResult = jedis.scan(cursor, new ScanParams()
                            .match(prefix + "*")
                            .count(500));
                    for (String key : scanResult.getResult()) {
                        if (key.startsWith(prefix)) {
                            jedisCluster.del(key);
                        }
                    }
                    cursor = scanResult.getCursor();
                } while (!ScanParams.SCAN_POINTER_START.equals(cursor));
            }
        }
    }

    private static final class RedisClusterFixture implements AutoCloseable {
        private static final int NODE_COUNT = 6;
        private static final int MAX_START_RETRIES = 2;
        private static final int MAX_START_ATTEMPTS = 1 + MAX_START_RETRIES;
        private static final int MAX_CLUSTER_DATA_PORT = 55535;
        private static final String IMAGE = "redis:7.2-alpine";
        private static final Set<Integer> FIXED_REDIS_CLUSTER_PORTS = Set.of(7000, 7001, 7002, 7003, 7004, 7005);
        private final RedisClusterContainer container;
        private final List<Integer> dataPorts;
        private final List<Integer> busPorts;

        private RedisClusterFixture(RedisClusterContainer container, List<Integer> dataPorts,
                List<Integer> busPorts) {
            this.container = container;
            this.dataPorts = dataPorts;
            this.busPorts = busPorts;
        }

        static RedisClusterFixture start() {
            Throwable lastFailure = null;
            for (int attempt = 1; attempt <= MAX_START_ATTEMPTS; attempt++) {
                List<Integer> dataPorts = allocatePorts(NODE_COUNT, FIXED_REDIS_CLUSTER_PORTS, MAX_CLUSTER_DATA_PORT);
                Set<Integer> excludedBusPorts = new LinkedHashSet<>(FIXED_REDIS_CLUSTER_PORTS);
                excludedBusPorts.addAll(dataPorts);
                List<Integer> busPorts = allocatePorts(NODE_COUNT, excludedBusPorts, 65535);
                RedisClusterContainer container = new RedisClusterContainer(IMAGE, dataPorts, busPorts);
                try {
                    container.start();
                    RedisClusterFixture fixture = new RedisClusterFixture(container, dataPorts, busPorts);
                    fixture.startRedisServers();
                    fixture.createCluster();
                    fixture.waitUntilReady();
                    return fixture;
                } catch (RuntimeException | AssertionError e) {
                    lastFailure = e;
                    container.close();
                }
            }
            throw new IllegalStateException("Failed to start Redis Cluster fixture after "
                    + MAX_START_ATTEMPTS + " attempts", lastFailure);
        }

        Set<HostAndPort> clusterNodes() {
            return dataPorts.stream()
                    .map(port -> new HostAndPort("127.0.0.1", port))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        Set<Integer> busPorts() {
            return busPorts.stream()
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        static boolean isFixedRedisClusterPort(int port) {
            return FIXED_REDIS_CLUSTER_PORTS.contains(port);
        }

        static boolean isValidClusterDataPort(int port) {
            return port > 0 && port <= MAX_CLUSTER_DATA_PORT;
        }

        private void startRedisServers() {
            for (int index = 0; index < NODE_COUNT; index++) {
                int dataPort = dataPorts.get(index);
                int busPort = busPorts.get(index);
                String configPath = "/tmp/redis-" + dataPort + ".conf";
                copyConfig(configPath, redisConfig(dataPort, busPort));
                exec("redis-server", configPath);
                waitForPing(index);
            }
        }

        private void createCluster() {
            try (Jedis first = jedis(0)) {
                for (int index = 1; index < NODE_COUNT; index++) {
                    clusterMeet(first, dataPorts.get(index), busPorts.get(index));
                }
            }
            waitForKnownNodes();
            addSlots(0, 0, 5460);
            addSlots(1, 5461, 10922);
            addSlots(2, 10923, 16383);
            replicate(3, 0);
            replicate(4, 1);
            replicate(5, 2);
        }

        private void clusterMeet(Jedis jedis, int dataPort, int busPort) {
            Object rawReply = jedis.getConnection().executeCommand(
                    new CommandArguments(Protocol.Command.CLUSTER)
                            .add("MEET")
                            .add("127.0.0.1")
                            .add(dataPort)
                            .add(busPort));
            String reply = rawReply instanceof byte[] bytes
                    ? new String(bytes, StandardCharsets.UTF_8)
                    : String.valueOf(rawReply);
            if (!"OK".equals(reply)) {
                throw new IllegalStateException("Unexpected CLUSTER MEET reply: " + reply);
            }
        }

        private void waitUntilReady() {
            Instant deadline = Instant.now().plus(READY_TIMEOUT);
            String clusterInfo = "";
            while (Instant.now().isBefore(deadline)) {
                try (Jedis jedis = jedis(0)) {
                    clusterInfo = jedis.clusterInfo();
                    if (clusterInfo.contains("cluster_state:ok")) {
                        return;
                    }
                } catch (RuntimeException e) {
                    clusterInfo = e.getMessage();
                }
                sleepOneSecond();
            }
            throw new AssertionError("Redis Cluster was not ready within " + READY_TIMEOUT.toSeconds()
                    + " seconds. Cluster info:\n" + clusterInfo);
        }

        private static List<Integer> allocatePorts(int count, Set<Integer> excludedPorts, int maxPort) {
            Set<Integer> ports = new LinkedHashSet<>();
            while (ports.size() < count) {
                int port = allocatePort();
                if (port <= maxPort && !excludedPorts.contains(port)) {
                    ports.add(port);
                }
            }
            return List.copyOf(ports);
        }

        private static int allocatePort() {
            try (ServerSocket socket = new ServerSocket(0)) {
                socket.setReuseAddress(false);
                return socket.getLocalPort();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to allocate a Redis Cluster test port", e);
            }
        }

        private void copyConfig(String configPath, String content) {
            Path tempFile = null;
            try {
                tempFile = Files.createTempFile("openjiuwen-redis-cluster-", ".conf");
                Files.writeString(tempFile, content, StandardCharsets.UTF_8);
                container.copyFileToContainer(MountableFile.forHostPath(tempFile), configPath);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to create Redis config " + configPath, e);
            } finally {
                if (tempFile != null) {
                    try {
                        Files.deleteIfExists(tempFile);
                    } catch (IOException ignored) {
                        // Best effort cleanup for the host-side temporary config file.
                    }
                }
            }
        }

        private String redisConfig(int dataPort, int busPort) {
            return """
                    port %d
                    bind 0.0.0.0
                    protected-mode no
                    cluster-enabled yes
                    cluster-config-file nodes-%d.conf
                    cluster-node-timeout 5000
                    cluster-announce-ip 127.0.0.1
                    cluster-announce-port %d
                    cluster-announce-bus-port %d
                    cluster-port %d
                    appendonly no
                    daemonize yes
                    dir /tmp
                    logfile /tmp/redis-%d.log
                    """.formatted(dataPort, dataPort, dataPort, busPort, busPort, dataPort);
        }

        private void exec(String... command) {
            try {
                Container.ExecResult result = container.execInContainer(command);
                if (result.getExitCode() != 0) {
                    throw new IllegalStateException("Container command failed: " + String.join(" ", command)
                            + "\nstdout:\n" + result.getStdout() + "\nstderr:\n" + result.getStderr());
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to execute container command: "
                        + String.join(" ", command), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while executing container command: "
                        + String.join(" ", command), e);
            }
        }

        private Jedis jedis(int index) {
            return new Jedis("127.0.0.1", dataPorts.get(index));
        }

        private void waitForPing(int index) {
            Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
            RuntimeException lastFailure = null;
            while (Instant.now().isBefore(deadline)) {
                try (Jedis jedis = jedis(index)) {
                    if ("PONG".equals(jedis.ping())) {
                        return;
                    }
                } catch (RuntimeException e) {
                    lastFailure = e;
                }
                sleepOneSecond();
            }
            throw new IllegalStateException("Redis node on port " + dataPorts.get(index) + " did not accept PING",
                    lastFailure);
        }

        private void waitForKnownNodes() {
            Instant deadline = Instant.now().plus(READY_TIMEOUT);
            while (Instant.now().isBefore(deadline)) {
                boolean everyNodeKnowsCluster = true;
                for (int index = 0; index < NODE_COUNT; index++) {
                    try (Jedis jedis = jedis(index)) {
                        if (nodeIdsByPort(jedis.clusterNodes()).size() < NODE_COUNT) {
                            everyNodeKnowsCluster = false;
                            break;
                        }
                    } catch (RuntimeException e) {
                        everyNodeKnowsCluster = false;
                        break;
                    }
                }
                if (everyNodeKnowsCluster) {
                    return;
                }
                sleepOneSecond();
            }
            throw new IllegalStateException("Redis Cluster nodes did not discover all " + NODE_COUNT + " nodes");
        }

        private void addSlots(int nodeIndex, int startSlot, int endSlot) {
            int[] slots = new int[endSlot - startSlot + 1];
            for (int index = 0; index < slots.length; index++) {
                slots[index] = startSlot + index;
            }
            try (Jedis jedis = jedis(nodeIndex)) {
                jedis.clusterAddSlots(slots);
            }
        }

        private void replicate(int replicaIndex, int masterIndex) {
            String masterNodeId = nodeId(dataPorts.get(masterIndex));
            try (Jedis jedis = jedis(replicaIndex)) {
                jedis.clusterReplicate(masterNodeId);
            }
        }

        private String nodeId(int dataPort) {
            Instant deadline = Instant.now().plus(READY_TIMEOUT);
            while (Instant.now().isBefore(deadline)) {
                try (Jedis jedis = jedis(0)) {
                    String nodeId = nodeIdsByPort(jedis.clusterNodes()).get(dataPort);
                    if (nodeId != null) {
                        return nodeId;
                    }
                }
                sleepOneSecond();
            }
            throw new IllegalStateException("Could not find Redis Cluster node id for port " + dataPort);
        }

        private static Map<Integer, String> nodeIdsByPort(String clusterNodes) {
            Map<Integer, String> nodeIds = new LinkedHashMap<>();
            for (String line : clusterNodes.split("\\R")) {
                String[] columns = line.split("\\s+");
                if (columns.length < 2 || columns[1].isBlank()) {
                    continue;
                }
                Integer port = portFromNodeAddress(columns[1]);
                if (port != null) {
                    nodeIds.put(port, columns[0]);
                }
            }
            return nodeIds;
        }

        private static Integer portFromNodeAddress(String address) {
            int busSeparator = address.indexOf('@');
            String hostAndPort = busSeparator >= 0 ? address.substring(0, busSeparator) : address;
            int portSeparator = hostAndPort.lastIndexOf(':');
            if (portSeparator < 0 || portSeparator == hostAndPort.length() - 1) {
                return null;
            }
            return Integer.parseInt(hostAndPort.substring(portSeparator + 1));
        }

        private static void sleepOneSecond() {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for Redis Cluster readiness", e);
            }
        }

        @Override
        public void close() {
            container.close();
        }
    }

    private static final class RedisClusterContainer
            extends GenericContainer<RedisClusterContainer> {
        RedisClusterContainer(String image, List<Integer> dataPorts, List<Integer> busPorts) {
            super(image);
            withCommand("sh", "-lc", "touch /tmp/container-ready && sleep 1d");
            waitingFor(Wait.forSuccessfulCommand("test -f /tmp/container-ready"));
            for (int port : dataPorts) {
                addFixedExposedPort(port, port);
            }
            for (int port : busPorts) {
                addFixedExposedPort(port, port);
            }
        }
    }
}
