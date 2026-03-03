package com.openjiuwen.core.common.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Singleton pattern.
 *
 * @see SingletonMeta
 */
class SingletonTest {

    private static AtomicInteger instanceCount;

    @BeforeAll
    static void setUpClass() {
        // 重置计数器
        GlobalCounter.reset();
    }

    @BeforeEach
    void setUp() {
        // 每个测试前重置计数器
        GlobalCounter.reset();
        // 清理之前测试创建的单例实例
        SingletonMeta.clearInstance(TestSingleton.class);
        SingletonMeta.clearInstance(AnotherSingleton.class);
    }

    /**
     * 测试单例模式 - 多次获取返回同一个实例
     */
    @Test
    void testSingletonReturnsSameInstance() {
        TestSingleton instance1 = TestSingleton.getInstance("test");
        TestSingleton instance2 = TestSingleton.getInstance("test");
        TestSingleton instance3 = TestSingleton.getInstance("test");

        // 验证所有实例都是同一个对象
        assertSame(instance1, instance2);
        assertSame(instance2, instance3);
        assertSame(instance1, instance3);
    }

    /**
     * 测试单例模式 - 构造函数只调用一次
     */
    @Test
    void testConstructorCalledOnlyOnce() {
        TestSingleton instance1 = TestSingleton.getInstance("test1");
        TestSingleton instance2 = TestSingleton.getInstance("test2");
        TestSingleton instance3 = TestSingleton.getInstance("test3");

        // 验证构造函数只被调用了一次
        assertEquals(1, instance1.getConstructorCallCount());
        assertEquals(1, instance2.getConstructorCallCount());
        assertEquals(1, instance3.getConstructorCallCount());
    }

    /**
     * 测试线程安全 - 多线程并发创建单例
     */
    @Test
    void testSingletonThreadSafety() throws InterruptedException {
        final int threadCount = 100;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(threadCount);
        final List<TestSingleton> instances = new ArrayList<>();

        // 创建多个线程同时获取单例
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    TestSingleton instance = TestSingleton.getInstance("concurrent");
                    instances.add(instance);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        // 同时启动所有线程
        startLatch.countDown();
        doneLatch.await();

        // 验证所有实例都是同一个
        TestSingleton firstInstance = instances.get(0);
        for (TestSingleton instance : instances) {
            assertSame(firstInstance, instance, "All instances should be the same object");
        }

        // 验证构造函数只被调用了一次
        assertEquals(1, firstInstance.getConstructorCallCount());
    }

    /**
     * 测试不同类的单例相互独立
     */
    @Test
    void testDifferentClassesHaveDifferentSingletons() {
        TestSingleton instance1 = TestSingleton.getInstance("test");
        AnotherSingleton instance2 = AnotherSingleton.getInstance("test");

        // 验证不同类的单例是不同的对象
        assertNotSame(instance1, instance2);
    }

    /**
     * 测试单例实例的状态
     */
    @Test
    void testSingletonInstanceState() {
        TestSingleton instance = TestSingleton.getInstance("initial");
        assertEquals("initial", instance.getValue());

        // 更新状态
        instance.setValue("updated");
        assertEquals("updated", instance.getValue());

        // 获取同一个实例，验证状态已被更新
        TestSingleton sameInstance = TestSingleton.getInstance("ignored");
        assertEquals("updated", sameInstance.getValue());
    }

    /**
     * 测试单例模式 - 使用不同的参数（第一个参数生效）
     */
    @Test
    void testSingletonWithDifferentParameters() {
        // 第一次创建使用参数 "first"
        TestSingleton instance1 = TestSingleton.getInstance("first");
        assertEquals("first", instance1.getValue());

        // 第二次获取忽略参数，返回已存在的实例
        TestSingleton instance2 = TestSingleton.getInstance("second");
        assertSame(instance1, instance2);
        assertEquals("first", instance2.getValue()); // 仍然是第一次的值
    }

    /**
     * 测试单例实例可以正常使用
     */
    @Test
    void testSingletonInstanceFunctionality() {
        TestSingleton instance = TestSingleton.getInstance("functional");
        assertNotNull(instance);

        // 测试实例方法
        instance.setValue("new value");
        assertEquals("new value", instance.getValue());

        String uppercased = instance.toUpperCase();
        assertEquals("NEW VALUE", uppercased);
    }

    /**
     * 测试单例的 hashcode 和 equals
     */
    @Test
    void testSingletonHashCodeAndEquals() {
        TestSingleton instance1 = TestSingleton.getInstance("test1");
        TestSingleton instance2 = TestSingleton.getInstance("test2");

        // 同一个实例的 hashCode 应该相同
        assertEquals(instance1.hashCode(), instance2.hashCode());

        // equals 应该返回 true（因为是同一个对象）
        assertEquals(instance1, instance2);
    }

    // ============ 测试用的单例类 ============

    /**
     * 用于测试的单例类
     */
    static class TestSingleton extends SingletonMeta<String> {
        private String value;
        private final int constructorCallCount;

        // 使用默认构造函数，参数由 getInstance 提供
        TestSingleton(String value) {
            super();
            this.value = value;
            this.constructorCallCount = GlobalCounter.increment();
        }

        public static TestSingleton getInstance(String value) {
            return SingletonMeta.getInstance(TestSingleton.class, value);
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String toUpperCase() {
            return value.toUpperCase();
        }

        public int getConstructorCallCount() {
            return constructorCallCount;
        }
    }

    /**
     * 另一个用于测试的单例类
     */
    static class AnotherSingleton extends SingletonMeta<String> {
        private final String value;

        AnotherSingleton(String value) {
            super();
            this.value = value;
        }

        public static AnotherSingleton getInstance(String value) {
            return SingletonMeta.getInstance(AnotherSingleton.class, value);
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 用于统计构造函数调用次数的辅助类
     */
    static class GlobalCounter {
        private static final AtomicInteger counter = new AtomicInteger(0);

        public static int increment() {
            return counter.incrementAndGet();
        }

        public static void reset() {
            counter.set(0);
        }
    }
}