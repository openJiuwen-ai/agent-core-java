/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

class RedisOptionalDependencyTest {
    @Test
    void coreWorksWithoutJedisAndOnlyLoadsRedisWhenRequested() throws Exception {
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        URL[] urls = Arrays.stream(classpath.split(java.io.File.pathSeparator))
                .filter(p -> !p.contains("/redis/clients/"))
                .map(p -> {
                    try {
                        return Path.of(p).toUri().toURL();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .toArray(URL[]::new);
        try (IsolatedLoader loader = new IsolatedLoader(urls)) {
            Thread thread = Thread.currentThread();
            ClassLoader previous = thread.getContextClassLoader();
            thread.setContextClassLoader(loader);
            try {
                Class<?> factory = loader.loadClass("com.openjiuwen.spi.store.KVStoreFactory");
                Object store = factory.getMethod("create", String.class, Map.class).invoke(null, "in_memory", Map.of());
                store.getClass().getMethod("set", String.class, Object.class).invoke(store, "k", "value");
                assertEquals("value", store.getClass().getMethod("get", String.class).invoke(store, "k"));
                Class<?> cp = loader.loadClass("com.openjiuwen.core.session.checkpointer.CheckpointerFactory");
                cp.getMethod("create", String.class, Map.class).invoke(null, "in_memory", Map.of());
                // DriverManager may already be initialized by another test's class loader.
                Class.forName("org.sqlite.JDBC", true, loader);
                Object sqlite = factory.getMethod("create", String.class, Map.class).invoke(null, "sqlite",
                        Map.of("db_path", ":memory:"));
                sqlite.getClass().getMethod("set", String.class, Object.class).invoke(sqlite, "k", "sqlite");
                assertEquals("sqlite", sqlite.getClass().getMethod("get", String.class).invoke(sqlite, "k"));
                sqlite.getClass().getMethod("close").invoke(sqlite);
                assertFalse(loader.redisLoaded);
                assertThrows(ClassNotFoundException.class, () -> loader.loadClass("redis.clients.jedis.Jedis"));
                loader.allowRedis = true;
                Object external = factory.getMethod("create", String.class, Map.class).invoke(null, "redis",
                        Map.of("redis_client", new ExternalClient()));
                external.getClass().getMethod("set", String.class, Object.class).invoke(external, "k", "external");
                assertEquals("external", external.getClass().getMethod("get", String.class).invoke(external, "k"));
                var failure = assertThrows(java.lang.reflect.InvocationTargetException.class,
                        () -> factory.getMethod("create", String.class, Map.class)
                                .invoke(null, "redis", Map.of("connection", Map.of("url", "redis://localhost:1"))));
                assertTrue(failure.getCause().getMessage().contains("jedis"));
            } finally {
                thread.setContextClassLoader(previous);
            }
        }
    }

    public static class ExternalClient {
        Object value;

        public void set(String key, Object value) {
            this.value = value;
        }

        public Object get(String key) {
            return value;
        }
    }

    static class IsolatedLoader extends URLClassLoader {
        boolean allowRedis;
        boolean redisLoaded;

        IsolatedLoader(URL[] urls) {
            super(urls, ClassLoader.getPlatformClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("redis.clients.")) {
                throw new ClassNotFoundException(name);
            }
            if (name.startsWith("com.openjiuwen.extensions.store.kv.Redis")
                    || name.startsWith("com.openjiuwen.extensions.checkpointer.redis.")) {
                redisLoaded = true;
                if (!allowRedis) {
                    throw new ClassNotFoundException("Unexpected Redis class load: " + name);
                }
            }
            return super.loadClass(name, resolve);
        }
    }
}
