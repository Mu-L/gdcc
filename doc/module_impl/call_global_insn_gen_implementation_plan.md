# CallGlobalInsnGen（Utility-Only）实施计划（整理版）

## 1. 目标与范围

### 1.1 本期目标

为 `CALL_GLOBAL` 指令提供稳定的 C 后端生成能力，第一阶段只支持 **utility function**。

### 1.2 覆盖能力

- `call_global "<name>" ...` -> `godot_<name>(...)` 的代码发射。
- utility 名称解析（前缀/无前缀统一）。
- 参数数量与类型校验。
- 返回值数量与类型校验。
- vararg utility 的 `argv/argc` 组包。
- 错误定位（函数、基本块、指令索引）。

### 1.3 非目标

- 非 utility 全局符号调用。
- 自动隐式 `pack_variant`（本期要求上游先显式 pack）。
- 构建脚本改造。

## 2. 语义与事实基线

### 2.1 外部规范

- `doc/gdcc_c_backend.md`
- `doc/gdextension-lite.md`
- `doc/gdcc_low_ir.md`

### 2.2 命名与元数据事实

- utility C 符号命名：`godot_<function_name>`。
- utility 元数据来源：`extension_api_451.json` 的 `utility_functions`。
- registry 查询 key 使用无前缀名（如 `print`）。
- vararg utility C 形参为：固定参数 + `argv` + `argc`。

## 3. 架构设计（实现应保持）

### 3.1 生成器职责

`CallGlobalInsnGen` 负责：

- 指令层面的 operand 解析（必须是变量 operand）。
- result 变量可写性与类型兼容性校验。
- 将调用发射委托给 `CBodyBuilder.callUtility*`。

`CBodyBuilder` 负责：

- utility 名称解析与签名查询。
- 参数校验、vararg 组包、默认参数补全。
- 调用结果赋值语义（destroy/release/own/转换）。

### 3.2 名称解析规范

- 输入允许 `print` 或 `godot_print`。
- lookup 名统一归一化为无前缀；C 调用符号统一为 `godot_` 前缀。
- 解析失败错误应包含：原始名 + lookup key。

### 3.3 参数校验规范

设：

- `provided = args.size()`
- `fixed = signature.parameterCount()`
- `isVararg = signature.isVararg()`

规则：

- 非 vararg：参数数目需满足固定参数规则（含 default 参数时允许缺省）。
- vararg：`provided >= fixed`。
- fixed 参数逐位 `checkAssignable(argType, paramType)`。
- vararg extra 参数本期必须可赋给 `Variant`。

### 3.4 返回值校验规范

- utility 返回 void/null：`resultId` 必须为空。
- utility 返回 non-void：
  - 若提供 `resultId`，必须存在、可写（非 ref）、且类型兼容。
  - 若省略 `resultId`，允许显式丢弃返回值（走 discard target 路径）。

### 3.5 vararg 渲染规范

- `extraCount == 0`：发射 `NULL, (godot_int)0`。
- `extraCount > 0`：发射局部 `argv` 数组并传 `(godot_int)extraCount`。
- `argv/argc/NULL` 必须作为 Raw 参数片段处理，不能走普通参数渲染。
- vararg utility 禁止误走 `callVoid/callAssign` 通用路径。

### 3.6 默认参数规范

- utility fixed 参数可由默认值补齐。
- 默认值补齐必须在 Builder 内完成：
  - 生成临时变量承载默认值表达式。
  - 调用完成后按临时变量生命周期销毁。
- 通用 `callVoid/callAssign` 路径不承担 default 补全能力。

## 4. 实施清单（后续开发按此执行）

### 4.1 生成器侧

- 保持 `CALL_GLOBAL` opcode 注册与分发。
- operand 非变量时立即报错。
- result 变量规则在生成器入口做前置校验。
- 统一走 `callUtilityVoid` / `callUtilityAssign`（或其 resolution 重载）。

### 4.2 Builder 侧

- 统一 utility 解析入口，避免重复解析实现。
- 保持 vararg 组包与 Raw 参数通道。
- 维护 `emitCallResultAssignment(...)` 作为唯一结果赋值实现。
- 维持防误用守卫：vararg utility 不能走非 utility 路径。

### 4.3 错误消息规范

- 统一用词：`utility function`。
- 必含关键信息：函数名、参数序号、期望/实际类型、期望/实际数量。
- 统一经 `invalidInsn(...)` 输出，确保定位信息完整。

## 5. 测试矩阵

### 5.1 成功路径

- non-vararg non-void（如 `deg_to_rad`）。
- non-vararg void。
- vararg `extra=0`（`NULL,(godot_int)0`）。
- vararg `extra>0`（argv 数组路径）。
- 前缀/无前缀 utility 名均可调用。
- non-void 返回值丢弃路径。
- default 参数补全路径。

### 5.2 失败路径

- utility 不存在。
- 参数变量不存在。
- 非变量 operand。
- 参数数量不匹配（含 vararg fixed 不足）。
- fixed 参数类型不匹配。
- vararg extra 非 Variant。
- void utility 提供了 `resultId`。
- non-void utility 的 `resultId` 非法（不存在/ref/类型不兼容）。

### 5.3 协同回归

- `CALL_GLOBAL` 不再触发 `Unsupported instruction opcode`。
- utility 路径错误消息风格一致。
- vararg 相关临时变量命名唯一且生命周期正确。

### 5.4 建议命令

```bash
./gradlew test --tests CallGlobalInsnGenTest --no-daemon --info --console=plain
./gradlew test --tests CBodyBuilderCallUtilityTest --no-daemon --info --console=plain
./gradlew test --tests CGenHelperUtilityResolutionTest --no-daemon --info --console=plain
./gradlew classes --no-daemon --info --console=plain
```

## 6. 遗留技术债（按价值排序）

1. `CallGlobalInsn.args` 仍为 `List<Operand>`，建议未来收窄到 `List<VariableOperand>`，将校验前置到 IR 构造期。
2. 测试中对 utility 返回 Object/GDCC Object 的覆盖不足，建议补齐指针转换与 own/release 路径断言。
3. 非 utility global call 尚未引入分流通道，后续扩展时需明确 utility path / symbol path 的错误边界。

## 7. 风险与防护

- 风险：路径绕过 Builder 导致生命周期语义丢失。  
  防护：统一调用 `callUtility*`，禁止生成器手拼调用结果赋值。
- 风险：vararg extra 来源不稳定导致地址生命周期问题。  
  防护：限制 extra 为变量引用；若将来支持表达式，必须同步引入 temp 托管策略。
- 风险：default 参数规则与渲染实现漂移。  
  防护：default 补全只保留一处实现（Builder），并用测试固定行为。

## 8. 完成标准（DoD）

- `CALL_GLOBAL`（utility-only）在后端稳定可用。
- 参数/返回值/vararg/default 语义均有测试守护。
- 错误信息可定位且风格统一。
- 文档、代码、测试三者行为一致，无半成品路径。
