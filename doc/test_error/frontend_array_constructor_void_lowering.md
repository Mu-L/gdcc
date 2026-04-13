# Frontend Array Void-Return Call Lowering Gap

## Summary

在补充 `test_suite` 端到端覆盖时，最初这条问题被记录成“executable-body `Array()` 构造在某些下游路径中被 lower 成 `void`”。

基于当前代码库与临时调查测试，这个表述已经过时：

- `Array()` 构造本身当前不会退化成 `void`
- 纯 local `Array()` + indexed store/load 路径已经恢复
- 当前仍然存在的真实问题是：
  - exact `Array` 上的 void-return method call
  - 当前已确认最小复现为 `Array.push_back(...)`
  - frontend/lowering/backend 交界处仍会发布并消费非法的 void result slot 链路

## 当前复现形状

### 1. 非回归形状：当前已通过

下面这条旧文档里的 indexed flow 现在已经不再复现：

```gdscript
func compute() -> int:
    var values: Array = Array()
    values[1] = 6
    var first: int = values[0]
    return first
```

### 2. 当前最小复现：`push_back` 后继续使用该数组

```gdscript
func compute() -> int:
    var plain: Array = Array()
    plain.push_back(1)
    return plain.size()
```

### 3. helper flow 仍会复现，但 helper 不是根因

```gdscript
func dynamic_size(value):
    return value.size()

func compute() -> int:
    var plain: Array = Array()
    plain.push_back(1)
    return dynamic_size(plain)
```

## Current Evidence

- 临时探针 `FrontendArrayConstructorVoidInvestigationTest` 已确认：
  - `ConstructBuiltinInsn` 对应的结果变量仍是 `GdArrayType`
  - 不是 `GdVoidType`
- 纯 indexed flow 当前 lower + codegen(fake compiler build) 已能通过
- `push_back` 相关路径仍会失败，而且：
  - 去掉 helper 后仍失败
  - 说明 helper 只是伴随路径，不是根因
- 当前 exact `Array.push_back(...)` lowering 结果仍会发布：
  - `CallMethodInsn.resultId() != null`
  - 且该 result variable 的类型为 `GdVoidType`

## Cause Chain

当前问题链路已经从“`Array()` constructor 退化成 `void`”收敛为“void-return call result slot 合同不一致”：

1. `extension_api_451.json` 中 `Array.push_back` 缺失 `return_type`
   - shared metadata consumer 会把缺失/空白 `return_type` 解释为 `void`
2. `FrontendBodyLoweringSupport.collectCfgValueMaterializations(...)`
   仍会为 `CallItem` 按 call return type 发布一个 `TEMP_SLOT`
3. `FrontendSequenceItemInsnLoweringProcessors`
   当前 exact instance call 仍无条件把 `resultSlotId` 传给 `CallMethodInsn`
4. 于是 LIR 中出现：
   - `CallMethodInsn(resultId != null, "push_back", ...)`
   - 但 `resultId` 对应变量类型是 `GdVoidType`
5. backend 第一处炸点在 `CCodegen.generateFunctionPrepareBlock()`
   - `__prepare__` 会为所有非参数、非 ref 变量自动注入默认初始化
   - `GdVoidType` 当前没有 special-case
   - 因而会落到默认分支并被注入 `ConstructBuiltinInsn(voidTemp, [])`
6. `ConstructInsnGen` / `CBuiltinBuilder` 随后在 `construct_builtin(void)` 处 fail-fast：
   - `Builtin constructor validation failed: 'void' with args [] is not defined in ExtensionBuiltinClass`
7. 但这还不是唯一问题：
   - `CallMethodInsnGen` 已明确要求：
     - 若 resolved return type 为 `void`
     - 则 `instruction.resultId()` 必须为 `null`
   - 所以即使只修 `__prepare__`，后续仍会在 void method call 本体处继续失败

## Impact

当前受影响的不是“所有 executable-body `Array()` 构造”，而是更窄也更准确的一条 surface：

- executable-body 中构造 exact `Array`
- 随后对其执行 void-return method call
- 当前已确认的最小触发点是 `push_back`

因此：

- `Array()` constructor 本身不是当前根因
- indexed store/load 不应继续作为“仍然失败”的问题描述
- helper flow 只是放大器，不是根因

## Suggested Fix Direction

最小可靠修复不是重做 `Array()` 构造 lowering，而是两段式修复 void-call 合同：

1. backend 先跳过 `GdVoidType` 变量在 `__prepare__` 中的自动初始化
   - 避免继续生成 `construct_builtin(void, [])`
   - 先切断当前最早、最误导的炸点
2. frontend body lowering 对 exact void-return call 不再发出带 resultId 的 call instruction
   - 当前已确认至少包括 exact instance route
   - global/utility void call 也应对齐同一合同
3. 保留 backend 现有 invalid-IR 防线
   - `CallMethodInsnGen` / `CallGlobalInsnGen` 对“void call 仍携带 resultId”的拒绝应继续存在
   - 这类测试属于 backend guard rail，不应因为 frontend 修复而删除

## Regression Anchors After Fix

修复后至少要继续覆盖：

- local `Array()` + indexed store/load
  - 作为“旧文档现象已过时”的正向非回归
- local `Array()` + `push_back()` + direct `size()`
  - 作为当前最小复现的正向回归
- local `Array()` + `push_back()` + helper flow
  - 作为“helper 不是根因，但这条 surface 也必须恢复”的正向回归
