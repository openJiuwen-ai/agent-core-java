package com.openjiuwen.core.common.utils;

import java.util.Objects;

/**
 * 简单的泛型Pair工具类
 * 用于存储两个值的键值对
 *
 * @param <K> 键的类型
 * @param <V> 值的类型
 */
public class Pair<K, V> {
    private final K key;
    private final V value;

    /**
     * 构造一个新的Pair
     *
     * @param key   键
     * @param value 值
     */
    public Pair(K key, V value) {
        this.key = key;
        this.value = value;
    }

    /**
     * 获取键
     *
     * @return 键
     */
    public K getKey() {
        return key;
    }

    /**
     * 获取值
     *
     * @return 值
     */
    public V getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pair<?, ?> pair = (Pair<?, ?>) o;
        return Objects.equals(key, pair.key) && Objects.equals(value, pair.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public String toString() {
        return "Pair{" + "key=" + key + ", value=" + value + '}';
    }

    /**
     * 创建一个新的Pair实例（静态工厂方法）
     *
     * @param key   键
     * @param value 值
     * @param <K>   键类型
     * @param <V>   值类型
     * @return 新的Pair实例
     */
    public static <K, V> Pair<K, V> of(K key, V value) {
        return new Pair<>(key, value);
    }
}


