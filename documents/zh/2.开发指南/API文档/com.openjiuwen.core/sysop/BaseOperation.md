# com.openjiuwen.core.sysop.BaseOperation

## 类 BaseOperation

```java
public abstract class BaseOperation
```

`BaseOperation` 是所有系统操作的公共基类，统一保存操作名称、运行模式、说明文本和运行配置。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `name` | `String` | - | 当前操作名称，例如 `fs`、`shell`、`code`。 |
| `mode` | `OperationMode` | - | 当前操作运行模式。 |
| `description` | `String` | - | 当前操作的人类可读说明。 |
| `runConfig` | `Object` | - | 当前操作持有的运行配置对象，具体类型取决于运行模式。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getName()` | 返回操作名称。 |
| `public OperationMode getMode()` | 返回运行模式。 |
| `public String getDescription()` | 返回操作说明。 |
| `public abstract List<ToolCard> listTools()` | 返回当前操作对外暴露的工具卡片列表。 |

## 说明

- 源码中还包含运行配置访问、工具卡片生成、对象转 `Map` 和日志事件构造等受保护辅助方法，用于子类实现。
- `CustomOperationExtensionTest` 间接验证了该抽象基类可被注册中心及具体操作实现正常继承和复用。

## 相关测试

- `CustomOperationExtensionTest`
