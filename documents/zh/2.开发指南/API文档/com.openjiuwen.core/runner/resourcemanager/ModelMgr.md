# com.openjiuwen.core.runner.resourcemanager.ModelMgr

## 类 ModelMgr

```java
public class ModelMgr extends AbstractManager<Model>
```

`ModelMgr` 负责 `Model` 资源 provider 的注册、获取与移除。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void addModel(String modelId, Supplier<Model> model)` | - |
| `public Supplier<? extends Model> removeModel(String modelId)` | - |
| `public Model getModel(String modelId)` | - |
