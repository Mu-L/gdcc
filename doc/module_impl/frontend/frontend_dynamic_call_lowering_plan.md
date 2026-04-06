# Frontend Dynamic Call Lowering Plan

> 本文档记录 frontend 对 `DYNAMIC` method-call route 的修复计划。
> 目标不是扩张新的语义面，而是修复当前 shared semantic / compile gate / CFG builder /
> body lowering / backend 之间已经存在的合同断裂。

- 当前状态：已完成（2026-04-05）

## 1. 背景

当前 frontend 已经把一类“编译期无法精确决议，但允许运行时动态分派”的方法调用发布为
`FrontendResolvedCall(status = DYNAMIC, callKind = DYNAMIC_FALLBACK)`。

这类调用在 GDScript 中非常常见，例如：

- 未声明类型或显式 `Variant` 接收者上的方法调用
- runtime-open object receiver 上的方法调用
- `PackedInt32Array`、`Array`、`Dictionary` 等实际运行时值通过无类型变量再调用成员方法

典型示例：

```gdscript
func ping(v):
    return v.size()
```

```gdscript
func ping(values):
    return values.size()
```

```gdscript
func ping(worker):
    return worker.ping()
```

这些写法在 frontend shared semantic 中已经不是“未知/暂缓”状态，而是明确的
runtime-open accepted route。

---

## 2. 当前问题成因

当前实现的真实问题不是 frontend 完全不会分析 dynamic call，而是不同阶段对
`DYNAMIC` 的合同不一致。

### 2.1 已经接受 `DYNAMIC` 的部分

- `FrontendChainReductionHelper` 已经可以发布 `FrontendResolvedCall.dynamic(...)`
- `FrontendExprTypeAnalyzer` 会把 `DYNAMIC` call 映射为 `FrontendExpressionType.dynamic(...)`
- `FrontendCompileCheckAnalyzer` 明确把 `DYNAMIC` 视为 non-blocking compile fact
- `FrontendCfgGraphBuilder` 也已经把 `RESOLVED` 与 `DYNAMIC` 都视为 lowering-ready call

因此，语义分析与 compile gate 的当前事实是：

- `DYNAMIC` 不是 compile blocker
- `DYNAMIC` 不是“尚未实现”的临时缺口
- `DYNAMIC` 是 frontend 已接受的 runtime-open call route

### 2.2 仍然错误拒绝 `DYNAMIC` 的部分

body lowering 仍按旧的 `RESOLVED-only` 合同实现，主要体现在两处：

1. `FrontendBodyLoweringSession.requireResolvedCall(...)`
   - 当前硬性要求 `resolvedCall.status() == RESOLVED`
   - 导致 compile gate 已放行的 `DYNAMIC` call 在 body lowering 直接 fail-fast

2. `FrontendSequenceItemInsnLoweringProcessors.FrontendCallInsnLoweringProcessor`
   - 当前把 `DYNAMIC_FALLBACK` 视为 “not lowering-ready”
   - 没有把它 materialize 成现有的 `CallMethodInsn`

### 2.3 更深一层的真源选择错误

`CallItem` 的结果类型当前由 `FrontendBodyLoweringSupport.requireCallReturnType(...)`
直接从 `resolvedCalls().returnType()` 读取。

这只对 exact `RESOLVED` call 成立，但对 `DYNAMIC` call 不成立。

`DYNAMIC` call 的稳定 published result 真源是：

- `analysisData.expressionTypes()` 中的 `DYNAMIC(Variant)`

而不是：

- `resolvedCall.returnType()`

因此当前 body lowering 对 call result type 的取值真源也选错了。

### 2.4 backend 实际已经具备承接能力

backend 当前并不存在能力缺口。

现有 `CALL_METHOD` 已经支持：

- `OBJECT_DYNAMIC`
- `VARIANT_DYNAMIC`

动态调用路径当前已经具备：

- 非 `Variant` 实参 pack 成 `Variant`
- 动态结果用 `Variant` 接收
- 目标为 concrete 时再走 unpack

因此本次修复的重点不是新增 backend 能力，而是让 frontend body lowering 正确接通
已经存在的 LIR/backend 路由。

---

## 3. 目标合同

本次修复完成后，`DYNAMIC` method-call route 的正式合同冻结为：

1. `FrontendResolvedCall(status = DYNAMIC, callKind = DYNAMIC_FALLBACK)` 属于 lowering-ready call
2. body lowering 允许把它 lower 成现有 `CallMethodInsn`
3. `DYNAMIC` call 的结果类型真源是对应 call anchor 的 published expression type
4. `DYNAMIC` call 的 published runtime type 固定为 `Variant`
5. frontend 不为 `DYNAMIC` call 重新推导 callable signature，也不在 body pass 重做 overload 选择
6. `DYNAMIC` call 的参数 pack 与结果 unpack 责任继续留给 backend dynamic dispatch 路径
7. 当前只开放 instance-style dynamic call route，不把 type-meta/static/global route 混入同一合同

---

## 4. 非目标

本计划明确不包含以下内容：

- callable-value invocation
- 新增 `CallDynamicMethodInsn` 或其他新 LIR 指令
- 放宽 compile gate 对 `FAILED` / `UNSUPPORTED` / `DEFERRED` 的阻断范围
- 扩张新的隐式类型转换规则
- static/type-meta dynamic fallback 的新语义设计

如果未来需要支持这些能力，必须单独立项，不得在本计划中顺手混入。

---

## 5. 修复步骤

每完成一项任务就将状态同步到文档中并为该项任务的产出添加全面但必要的注释（不要添加过多导致难以阅读）

## 5.1 第一步：统一 lowering-ready call 合同

- 状态：已完成（2026-04-05）

### 目标

把 body lowering 对 call 的“允许状态”判断与 compile gate / CFG builder 对齐。

### 实施内容

- 将 `FrontendBodyLoweringSession.requireResolvedCall(...)` 调整为接受：
  - `RESOLVED`
  - `DYNAMIC`
- 其他状态继续保持 fail-fast：
  - `BLOCKED`
  - `DEFERRED`
  - `FAILED`
  - `UNSUPPORTED`
- 同步更新该 helper 的注释与异常信息，明确它消费的是 lowering-ready published call fact，
  而不是 resolved-only fact

### 验收细则

- happy path：
  - `DYNAMIC` call 不再在 `requireResolvedCall(...)` 处抛出 “not lowering-ready” 异常
- negative path：
  - `FAILED` / `UNSUPPORTED` / `DEFERRED` 仍会稳定 fail-fast
  - 异常信息仍明确指出 compile gate 本应拦截的状态

---

## 5.2 第二步：改正 call result type 的 published 真源

- 状态：已完成（2026-04-05）

### 目标

让 `CallItem` 的结果类型不再错误依赖 `resolvedCall.returnType()`。

### 实施内容

- 调整 `FrontendBodyLoweringSupport` 对 `CallItem` result type 的收集逻辑
- 改为从 call anchor 对应的 `analysisData.expressionTypes()` 读取 lowering-ready published type
- 对 bare `CallExpression` 与 `AttributeCallStep` 使用同一套读取规则
- 保持以下合同：
  - `RESOLVED` call -> exact published type
  - `DYNAMIC` call -> published `Variant`

### 原因

`resolvedCalls()` 表保存的是 call route fact，不是 call result type 的唯一真源。
对 `DYNAMIC` call 来说，result type contract 本来就是通过 `expressionTypes()` 发布。

### 验收细则

- happy path：
  - `DYNAMIC` call 的 result value id 能稳定获得 `Variant` 类型
  - `RESOLVED` call 的 result value id 仍保持现有 exact type 行为
- negative path：
  - 如果 call anchor 缺失 published expression type，仍然 fail-fast
  - 不允许在 body lowering 中为 call result type 做二次推导

---

## 5.3 第三步：接通 `DYNAMIC_FALLBACK -> CallMethodInsn`

- 状态：已完成（2026-04-05）

### 目标

让 `FrontendCallInsnLoweringProcessor` 能把 dynamic instance call 正确 materialize 成现有
`CallMethodInsn`。

### 实施内容

- 在 `FrontendCallInsnLoweringProcessor` 中区分两类 route：
  - exact route：
    - `INSTANCE_METHOD`
    - `STATIC_METHOD`
    - `CONSTRUCTOR`
  - dynamic route：
    - `DYNAMIC_FALLBACK`
- 对 `DYNAMIC_FALLBACK`：
  - 当前只允许 instance receiver kind
  - 使用现有 receiver 解析逻辑得到 object slot
  - 发出普通 `CallMethodInsn`
  - `methodName` 继续使用 published `callableName`
- 明确禁止：
  - 把 `DYNAMIC_FALLBACK` 降成 `CallStaticMethodInsn`
  - 把 `DYNAMIC_FALLBACK` 降成 `CallGlobalInsn`
  - 在 body lowering 中新增新的 dynamic-call 指令种类

### 验收细则

- happy path：
  - `worker.ping()` 在 receiver 为 runtime-open route 时会生成 `CallMethodInsn`
  - result slot 类型为 `Variant`
- negative path：
  - 非 instance 的 `DYNAMIC_FALLBACK` 仍应作为 invariant violation fail-fast
  - constructor/static/global route 不得误入 dynamic instance lowering

---

## 5.4 第四步：保持 dynamic call 的参数 materialization 边界

- 状态：已完成（2026-04-05）

### 目标

避免 frontend body lowering 为 dynamic call 错误要求 exact callable signature。

### 实施内容

- `FrontendBodyLoweringSession.materializeCallArguments(...)` 继续只服务 exact route
- 对 `DYNAMIC_FALLBACK`：
  - frontend 不读取 fixed parameter signature
  - frontend 不做 fixed-parameter `Variant` boundary materialization
  - 直接把已有 argument value slot 透传给 `CallMethodInsn`
- pack/unpack 责任继续由 backend dynamic dispatch 路径承担

### 原因

dynamic call 的合同本来就是：

- 编译期只冻结“方法名 + receiver route + runtime-open”
- 运行时再根据 receiver 的真实类型完成分派

如果 frontend 在 body pass 再要求 callable signature，等于重新把 dynamic route 收紧回 exact route，
这与 shared semantic 已冻结的合同冲突。

### 验收细则

- happy path：
  - `DYNAMIC` call 不会再因为缺少 callable signature metadata 失败
  - dynamic call 的参数能够透传到 backend
- negative path：
  - exact route 仍继续使用现有 fixed-parameter materialization helper
  - frontend 不得为 dynamic route 臆造 parameter type

---

## 5.5 第五步：补齐 targeted tests

- 状态：已完成（2026-04-05）
- 已新增 `FrontendLoweringBodyInsnPassTest.runLowersDynamicInstanceCallsIntoCallMethodInsnWithVariantResultSlot`
- 已新增 `FrontendLoweringBodyInsnPassTest.runLetsDynamicCallResultsCrossTypedCallBoundariesThroughOrdinaryUnpack`
- 已新增 `FrontendLoweringBodyInsnPassTest.runFailsFastWhenSyntheticDynamicFallbackDoesNotUseInstanceReceiverRoute`
- 已新增 `FrontendLoweringToCProjectBuilderIntegrationTest.lowerFrontendDynamicInstanceCallRoutesBuildNativeLibraryAndRunInGodot`

### 目标

通过 frontend lowering 与 engine/runtime 两层测试把行为锚定住。

### 建议测试内容

1. frontend lowering 单测：dynamic instance call emits `CallMethodInsn`
   - 输入：
     - receiver 为无类型局部或参数
     - 调用普通 instance method
   - 断言：
     - 存在 `CallMethodInsn`
     - result slot type 为 `Variant`

2. frontend lowering 单测：dynamic call result can cross later typed boundary
   - 输入：
     - `take_i(worker.size())`
   - 断言：
     - dynamic call 结果先为 `Variant`
     - 进入 typed fixed parameter 前再由既有 ordinary boundary helper 做 unpack

3. frontend lowering 单测：exact route remains exact
   - 输入：
     - typed `PackedInt32Array` / typed object receiver 上的方法调用
   - 断言：
     - 仍按原 exact route lowering
     - 不误走 dynamic route

4. negative test：non-instance dynamic route still fails closed
   - 构造人为破坏的 published call fact
   - 断言 body lowering 明确 fail-fast，而不是 silent fallback

5. engine integration test：runtime dynamic builtin/object method dispatch
   - 例如：
     - `var v`
     - `v = PackedInt32Array([1, 2, 3])`
     - `return v.size()`
   - 断言：
     - 能在 Godot 中正常执行并返回正确结果

### 验收细则

- happy path：
  - frontend lowering 与 engine runtime 两层都覆盖到
- negative path：
  - invariant violation 仍有单测锁定
  - exact route 不被回归破坏

---

## 5.6 第六步：同步文档

- 状态：已完成（2026-04-05）

### 目标

把 `DYNAMIC` call 的长期合同明确写入实现文档，避免未来再次出现“前半段接受，后半段拒绝”。

### 需要同步的文档

- 本文档
- `frontend_rules.md`
- `frontend_lowering_cfg_pass_implementation.md`
- 如有必要，补充 call / chain / compile-check 相关文档中的对应描述

### 文档必须明确说明

1. `DYNAMIC` call 是 compile-ready runtime-open fact
2. `DYNAMIC` call 的结果类型真源是 `expressionTypes()`，不是 `resolvedCall.returnType()`
3. body lowering 对 `DYNAMIC` call 不重做 overload 选择
4. dynamic call 继续复用现有 `CallMethodInsn`
5. 参数 pack 与结果 unpack 继续由 backend dynamic dispatch 路径负责

---

## 6. 最终验收标准

本计划完成后，以下条件必须同时成立：

1. shared semantic、compile gate、CFG builder、body lowering 对 `DYNAMIC` call 的 lowering-ready 合同一致
2. `DYNAMIC_FALLBACK` instance route 可以稳定 lower 成 `CallMethodInsn`
3. `DYNAMIC` call 的 result value type 稳定发布为 `Variant`
4. exact route 与 dynamic route 的参数 materialization 边界清晰且互不污染
5. backend 继续作为 dynamic dispatch 的唯一 pack/unpack 承担方
6. frontend lowering 单测与 engine integration test 同步锚定该行为

---

## 7. 实施顺序建议

建议按以下最小可提交顺序推进：

1. 调整 lowering-ready call helper
2. 修正 call result type 真源
3. 接通 `DYNAMIC_FALLBACK -> CallMethodInsn`
4. 收口 dynamic call 参数 materialization
5. 补测试
6. 同步文档

理由：

- 先修合同入口，避免测试还没走到真正逻辑就被早期 guard 拦住
- 再修 value type 真源，保证 call result slot 能先稳定下来
- 最后再接 instruction emission 与测试，改动面最小，也最容易定位回归
