# Frontend Array Void-Return Call Lowering Gap

## Summary

在补充 `test_suite` 端到端覆盖时，最初这条问题被记录成“executable-body `Array()` 构造在某些下游路径中被 lower 成 `void`”。

基于当前代码库与临时调查测试，这个表述已经过时：

- `Array()` 构造本身当前不会退化成 `void`
- 纯 local `Array()` + indexed store/load 路径已经恢复
- 当时真正的问题是：
  - exact `Array` 上的 void-return method call
  - 已确认最小复现为 `Array.push_back(...)`
  - frontend/lowering/backend 交界处会发布并消费非法的 void result slot 链路

## Current Status

截至 2026-04-13，Phase A-D 已完成，这条 build/codegen/CFG 缺陷已经闭环：

- `push_back + size()` 当前已恢复成功
- `push_back + dynamic helper` 当前已恢复成功
- exact instance / utility-global 的 void-return call 现在都会 emitted 为 `resultId = null`
- statement-position `RESOLVED(void)` call 不再发布 `CallItem.resultValueId`，也不再声明对应 `cfg_tmp_*`
- backend 现有 `void + resultId` guard rail 继续保留，用于拦截未来坏 LIR
- former investigation probe 已收敛为正式回归 `FrontendArrayVoidReturnCallRegressionTest`
- 计划文档与长期事实源已同步到最终合同

## 历史复现形状（Phase B 前）

### 1. 非回归形状：当前已通过

下面这条旧文档里的 indexed flow 现在已经不再复现：

```gdscript
func compute() -> int:
    var values: Array = Array()
    values[1] = 6
    var first: int = values[0]
    return first
```

### 2. 历史最小复现：`push_back` 后继续使用该数组

```gdscript
func compute() -> int:
    var plain: Array = Array()
    plain.push_back(1)
    return plain.size()
```

### 3. helper flow 在修复前同样会复现，但 helper 不是根因

```gdscript
func dynamic_size(value):
    return value.size()

func compute() -> int:
    var plain: Array = Array()
    plain.push_back(1)
    return dynamic_size(plain)
```

## Historical Evidence

- 正式回归 `FrontendArrayVoidReturnCallRegressionTest` 当前继续覆盖并确认：
  - `ConstructBuiltinInsn` 对应的结果变量仍是 `GdArrayType`
  - 不是 `GdVoidType`
- 纯 indexed flow 当前 lower + codegen(fake compiler build) 已能通过
- backend Phase A 已完成：
  - `__prepare__` 不再为 `GdVoidType` temp 注入 `construct_builtin(void, [])`
- `push_back` 相关路径在 Phase A-B 中间态仍会失败，而且：
  - 去掉 helper 后仍失败
  - 说明 helper 只是伴随路径，不是根因
- Phase B 前 exact `Array.push_back(...)` lowering 结果仍会发布：
  - `CallMethodInsn.resultId() != null`
  - 且该 result variable 的类型为 `GdVoidType`
- 当时失败已后移到 `CallMethodInsnGen` 的 guard rail：
  - void method `push_back` 仍携带 `resultId`
  - backend 现在直接报 `has no return value but resultId is provided`

## Historical Cause Chain

修复前的问题链路已经从“`Array()` constructor 退化成 `void`”收敛为“void-return call result slot 合同不一致”：

1. `extension_api_451.json` 中 `Array.push_back` 缺失 `return_type`
   - shared metadata consumer 会把缺失/空白 `return_type` 解释为 `void`
2. 在旧实现中，`FrontendBodyLoweringSupport.collectCfgValueMaterializations(...)`
   会为 `CallItem` 按 call return type 发布一个 `TEMP_SLOT`
3. 在旧实现中，`FrontendSequenceItemInsnLoweringProcessors`
   会无条件把 exact instance call 的 `resultSlotId` 传给 `CallMethodInsn`
4. 于是旧 LIR 中出现：
   - `CallMethodInsn(resultId != null, "push_back", ...)`
   - 但 `resultId` 对应变量类型是 `GdVoidType`
5. backend Phase A 已修复 `CCodegen.generateFunctionPrepareBlock()`
   - `__prepare__` 现在会跳过 `GdVoidType` 变量
   - 不再提前注入 `ConstructBuiltinInsn(voidTemp, [])`
6. 在 Phase A-B 中间态，实际失败点后移到 `CallMethodInsnGen`
   - `push_back` 的 resolved return type 是 `void`
   - 但 frontend 仍传入了 non-null `resultId`
7. `CallMethodInsnGen` 因此按既有 invalid-IR guard rail 继续 fail-fast：
   - `void` method 禁止提供 `resultId`
   - 当时报错为 `has no return value but resultId is provided`

## Historical Impact

修复前受影响的不是“所有 executable-body `Array()` 构造”，而是更窄也更准确的一条 surface：

- executable-body 中构造 exact `Array`
- 随后对其执行 void-return method call
- 当前已确认的最小触发点是 `push_back`

因此：

- `Array()` constructor 本身不是当前根因
- indexed store/load 不应继续作为“仍然失败”的问题描述
- helper flow 只是放大器，不是根因

## Resolution

最小可靠修复已经按三段式完成：

1. backend Phase A：
   - `GdVoidType` 变量在 `__prepare__` 中的自动初始化已被跳过
   - `construct_builtin(void, [])` 这条误导性炸点已被切断
2. frontend Phase B：
   - exact instance void call 不再发出带 resultId 的 `CallMethodInsn`
   - utility/global void call 不再发出带 resultId 的 `CallGlobalInsn`
3. frontend Phase C：
   - statement-position `RESOLVED(void)` call 不再发布 `CallItem.resultValueId`
   - `collectCfgValueMaterializations(...)` / `declareCfgValueSlots()` 不再为这类 call 发布 temp slot
4. backend invalid-IR 防线保持不变：
   - `CallMethodInsnGen` / `CallGlobalInsnGen` 继续拒绝“void call 仍携带 resultId”的坏形态
5. Phase D 已完成：
   - 调查探针已转成长期回归
   - 错误记录与长期事实源已同步

## Regression Anchors After Fix

修复后至少要继续覆盖：

- 正式回归类：`src/test/java/dev/superice/gdcc/backend/c/build/FrontendArrayVoidReturnCallRegressionTest.java`
- broader end-to-end 合同类：`src/test/java/dev/superice/gdcc/backend/c/build/FrontendVoidReturnCallIntegrationTest.java`
  - discarded global `print(...)`
  - non-bare attribute void call
  - property-backed writable-route writeback
  - `Node.new()` constructor boundary
  - static type-meta head 当前 backend gap 的 negative build baseline

- local `Array()` + indexed store/load
  - 作为“旧文档现象已过时”的正向非回归
- local `Array()` + `push_back()` + direct `size()`
  - 作为历史最小复现的正向回归
- local `Array()` + `push_back()` + helper flow
  - 作为“helper 不是根因，但这条 surface 也必须恢复”的正向回归
