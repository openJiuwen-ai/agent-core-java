# com.openjiuwen.core.common.logging.LazyLogger

## 类 LazyLogger

```java
public class LazyLogger implements LoggerProtocol
```

`LazyLogger` 通过 `Supplier<LoggerProtocol>` 延迟解析真实 logger，适合模块级静态常量和启动阶段不希望立即初始化的日志入口。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `getter` | `java.util.function.Supplier<LoggerProtocol>` | 提供真实 logger 的工厂。 |
| `delegate` | `LoggerProtocol` | 首次访问后缓存的真实 logger。 |

## 构造与行为

| 签名 | 说明 |
| --- | --- |
| `public LazyLogger(java.util.function.Supplier<LoggerProtocol> getter)` | 创建延迟代理，但不会立即解析真实 logger。 |

## 说明

- 私有 `getDelegate()` 使用 `volatile delegate` 配合同步块做双重检查，保证真实 logger 最多初始化一次。
- 除构造函数外，所有 `LoggerProtocol` 方法都会先解析 `delegate`，再把调用原样转发给真实实现。
