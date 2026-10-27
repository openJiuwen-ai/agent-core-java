# com.openjiuwen.core.sysop.sandbox.gateway.SandboxGateway

## 类 SandboxGateway

```java
public class SandboxGateway
```

负责获取沙箱 endpoint、路由 provider 调用和管理沙箱生命周期的异步网关。

## 说明

- `getSandbox` / `getEndpoint` 按隔离键获取或创建沙箱；隔离键与 provider 的物理 sandbox ID 独立。
- `releaseSandbox` 开始时摘除记录及对应 provider 缓存。同一隔离键的新请求等待物理释放完成，
  释放期间的重复释放返回未找到，其他隔离键不受影响。
- 删除成功后不会再次按隔离键删除记录；暂停成功后保存 `PAUSED` 状态，后续获取会恢复该沙箱。
- 释放失败时，在没有替代记录的情况下恢复原记录及 provider 缓存，允许调用方重试。
  取消返回的 future 不会中断已经开始的释放清理。
- `SandboxGatewayTest` 和 `JiuwenBoxGatewayLifecycleTest` 覆盖并发释放、失败重试与物理 ID 生命周期。
