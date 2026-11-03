# common

`com.openjiuwen.core.common` 汇总框架共享的基础能力。本页提供 `common` 子树的统一导航入口，并串联 `exception`、`logging`、`security`、`utils`、`schema` 与 `constants` 等子包。

## 模块

| 模块 | 说明 |
| --- | --- |
| [`exception`](./common/exception.README.md) | `com.openjiuwen.core.common.exception` 定义统一状态码、异常层级、模板生成工具，以及按领域细分的异常封装。 |
| [`logging`](./common/logging.README.md) | `com.openjiuwen.core.common.logging` 收录日志接口、日志实现与相关事件类型。 |
| [`security`](./common/security.README.md) | `com.openjiuwen.core.common.security` 收录安全校验、防护与敏感路径处理相关类型。 |
| [`utils`](./common/utils.README.md) | `com.openjiuwen.core.common.utils` 收录通用工具函数与辅助类。 |
| [`schema`](./common/schema.README.md) | `com.openjiuwen.core.common.schema` 定义共享 card、参数与内容数据结构。 |
| [`constants`](./common/constants.README.md) | `com.openjiuwen.core.common.constants` 定义公共常量与轻量枚举。 |

## 核心类型

| 类型 | 说明 |
| --- | --- |
| [`VirtualThreadSupport`](./common/VirtualThreadSupport.md) | Java 17 编译基线下的 JDK21 虚拟线程运行时兼容层。 |

## 说明

- 本页是 `common` 命名空间的共享导航页。
- `VirtualThreadSupport` 供 SDK 内部和需要对齐 SDK 运行时行为的业务侧复用；JDK21+ 下使用虚拟线程，JDK17 下回退到平台线程。
