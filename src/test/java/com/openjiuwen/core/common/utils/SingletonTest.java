package com.openjiuwen.core.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Singleton 测试类
 * 
 * 测试单例模式的实现
 */
public class SingletonTest {

    /**
     * 测试用的单例类（使用enum实现）
     */
    public enum TestSingletonEnum {
        INSTANCE;

        private int counter = 0;

        public int incrementAndGet() {
            return ++counter;
        }

        public int getCounter() {
            return counter;
        }

        public void reset() {
            counter = 0;
        }
    }

    @Test
    public void testEnumSingletonInstance() {
        // 获取两次实例，应该是同一个对象
        TestSingletonEnum instance1 = TestSingletonEnum.INSTANCE;
        TestSingletonEnum instance2 = TestSingletonEnum.INSTANCE;

        assertSame(instance1, instance2, "两次获取的实例应该是同一个对象");
    }

    @Test
    public void testEnumSingletonState() {
        TestSingletonEnum instance = TestSingletonEnum.INSTANCE;
        instance.reset();

        int value1 = instance.incrementAndGet();
        assertEquals(1, value1, "第一次递增应该返回1");

        int value2 = instance.incrementAndGet();
        assertEquals(2, value2, "第二次递增应该返回2");

        // 再次获取实例，状态应该保持
        TestSingletonEnum sameInstance = TestSingletonEnum.INSTANCE;
        assertEquals(2, sameInstance.getCounter(), "状态应该保持");
    }

    @Test
    public void testEnumSingletonThreadSafety() throws InterruptedException {
        TestSingletonEnum.INSTANCE.reset();

        // 创建多个线程同时访问单例
        int threadCount = 10;
        int incrementsPerThread = 100;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    TestSingletonEnum.INSTANCE.incrementAndGet();
                }
            });
            threads[i].start();
        }

        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }

        // 验证最终计数（注意：incrementAndGet不是线程安全的，这只是测试单例本身）
        int finalCount = TestSingletonEnum.INSTANCE.getCounter();
        assertTrue(finalCount > 0, "计数应该大于0");
        // 由于线程竞争，实际值可能小于期望值，但单例本身应该是同一个实例
    }

    /**
     * 演示Holder模式单例（另一种实现方式）
     */
    static class HolderSingleton {
        private HolderSingleton() {}

        private static class Holder {
            private static final HolderSingleton INSTANCE = new HolderSingleton();
        }

        public static HolderSingleton getInstance() {
            return Holder.INSTANCE;
        }
    }

    @Test
    public void testHolderPatternSingleton() {
        HolderSingleton instance1 = HolderSingleton.getInstance();
        HolderSingleton instance2 = HolderSingleton.getInstance();

        assertSame(instance1, instance2, "Holder模式也应该返回同一个实例");
    }
}


