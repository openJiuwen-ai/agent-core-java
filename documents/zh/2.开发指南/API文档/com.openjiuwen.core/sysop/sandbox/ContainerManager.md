# com.openjiuwen.core.sysop.sandbox.ContainerManager

## 类 ContainerManager

```java
public class ContainerManager
```

按隔离键缓存沙箱客户端、容器信息和生命周期记录的兼容管理器。该类已弃用，
新代码应使用 `com.openjiuwen.core.sysop.sandbox.gateway.SandboxGateway`。

## 说明

- `acquire` 按隔离键创建或复用客户端；`release` 支持 `delete`、`pause` 和 `keep`。
- `evictExpired` 清理过期记录及对应隔离键的客户端、容器缓存，物理 ID 与隔离键可以不同。
- AIO / JiuwenBox 的 `pre_deploy` 配置未显式指定 `extra_params.sandbox_id` 时，
  容器和 endpoint 的 `sandboxId` 可以为空，表示尚无已知物理 ID。不会用隔离键伪造物理 ID，
  也不会向 launcher 传入空 ID 执行删除或暂停。动态物理 ID 的跟踪和释放请使用新版 Gateway。
- `SandboxLifecycleCompatibilityTest` 覆盖生命周期缓存、过期清理及空物理 ID 的兼容行为。
