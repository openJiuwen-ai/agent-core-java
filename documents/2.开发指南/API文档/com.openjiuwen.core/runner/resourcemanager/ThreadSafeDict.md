# com.openjiuwen.core.runner.resourcemanager.ThreadSafeDict

## 类 ThreadSafeDict

```java
public class ThreadSafeDict<K, V>
```

`ThreadSafeDict` 是基于 `ReentrantLock` 和 `HashMap` 的线程安全字典封装，补充了快照与便捷更新能力。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `lock` | `ReentrantLock` | `new ReentrantLock()` | - |
| `data` | `Map<K, V>` | `-` | - |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public ThreadSafeDict()` | - |
| `public ThreadSafeDict(Map<K, V> initialData)` | - |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public V get(K key)` | - |
| `public V getOrDefault(K key, V defaultValue)` | - |
| `public V put(K key, V value)` | - |
| `public V remove(K key)` | - |
| `public boolean containsKey(K key)` | - |
| `public int size()` | - |
| `public void clear()` | - |
| `public V getOrSet(K key, V defaultValue)` | 原子获取或写入：键不存在时写入默认值并返回；否则返回当前值。 |
| `public V getOrCreate(K key, Supplier<V> creator)` | 原子获取或创建：键不存在时调用 `creator` 生成并保存结果。 |
| `public void putAll(Map<K, V> m)` | - |
| `public Set<K> keys()` | 返回当前键集合的快照。 |
| `public Collection<V> values()` | 返回当前值集合的快照。 |
| `public Map<K, V> snapshot()` | 返回当前键值对的快照副本。 |
| `public Set<Map.Entry<K, V>> items()` | 返回当前条目集合的快照，以 `Set<Map.Entry<K, V>>` 形式表示。 |
| `public V setdefault(K key, V defaultValue)` | 当键不存在时写入并返回 `defaultValue`；否则返回当前值。 |
| `public V pop(K key, V defaultValue)` | 移除并返回指定键的值；若键不存在则返回 `defaultValue`。 |
| `public V pop(K key)` | 移除并返回指定键的值；若键不存在则抛出 `NoSuchElementException`。 |
| `public void update(Map<K, V> m)` | 批量写入另一份 `Map` 的条目；等价于调用 `putAll(Map)`。 |
| `public String toString()` | - |
