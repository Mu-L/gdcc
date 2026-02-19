# CallGlobalInsnGen（仅 Utility Function）实施方案

## 1. 目标与范围

本方案用于落地 `CALL_GLOBAL` 指令的 C 后端生成，第一阶段仅支持调用 GDExtension 的 **utility functions**（来自 `ClassRegistry`）。

本次范围包含：
- `call_global "<function_name>" ...` 到 `godot_<function_name>(...)` 的生成。
- 参数数量/类型校验。
- 返回值数量/类型校验。
- vararg utility 的 `argv/argc` 形态生成。
- 失败时抛出带函数/块/指令位置信息的 `InvalidInsnException`。

本次范围不包含：
- `call_global` 调用非 utility 全局符号。
- 自动为非 `Variant` 参数做隐式 `pack_variant`。
- 修改构建脚本或 Gradle 配置。

---

## 2. 调研结论（已确认）

### 2.1 当前代码状态（已更新）

- `CallGlobalInsnGen` 已从 `TemplateInsnGen` 迁移到 `CInsnGen`，通过 `CBodyBuilder` 发射代码。
- `src/main/c/codegen/insn/call_global.ftl` 仍不存在，且已不再依赖该模板。
- `CCodegen` 已注册 `CallGlobalInsnGen`，`CALL_GLOBAL` 已接入后端指令分发。

### 2.2 命名约定来源

根据 `doc/gdextension-lite.md`：
- Utility 函数 C 符号命名为：`godot_<function_name>`。
- Variadic 方法/utility 采用 `argv/argc` 约定。

### 2.3 Utility 元数据与签名来源

- utility 元数据来自 `src/main/resources/extension_api_451.json` 的 `utility_functions`。
- `ClassRegistry.findUtilityFunctionSignature(name)` 使用 **无前缀名**（如 `print`、`deg_to_rad`）检索。
- `return_type` 缺失时在当前实现中会解析为 `null`，语义等价于无返回值（void）。

### 2.4 vararg 真实 C 声明形态

`tmp/test/c_build/include/gdextension-lite/generated/utility_functions.h` 已确认 vararg utility 声明形态，例如：

```c
void godot_print(const godot_Variant *arg1, const godot_Variant **argv, godot_int argc);
```

同类函数包括 `print*`、`push_*`、`str`、`max`、`min`。

---

## 3. 关键遗漏与约束（需在实现中显式处理）

1. **指令接入遗漏**：`CALL_GLOBAL` 未注册到 `CCodegen`。
2. **模板缺失**：当前模板路径不可用，应直接改为 `CBodyBuilder` 路径。
3. **utility 名称前缀差异**：IR 常用无前缀名（`print`），但 C 侧需要 `godot_print`。
4. **vararg ABI 细节**：需要显式构造 `const godot_Variant* argv[]` 与 `argc`。
5. **类型严格性**：本阶段不做隐式 pack，vararg 额外参数必须是 `Variant`（否则报错）。
6. **返回值规则**：void/null return 与 non-void return 的 resultId 约束必须严格校验。

---

## 4. 总体设计

## 4.1 生成器形态

将 `CallGlobalInsnGen` 从 `TemplateInsnGen<CallGlobalInsn>` 改为 `CInsnGen<CallGlobalInsn>`，实现：

- `getInsnOpcodes()` 返回 `CALL_GLOBAL`。
- `generateCCode(CBodyBuilder bodyBuilder)` 走 builder 统一语义（赋值、生命周期、错误定位）。

## 4.2 名称解析策略

定义内部解析流程（建议记录结构体/record）：

- 输入：IR 中 `instruction.functionName()`。
- 输出：
  - `lookupName`：用于 `ClassRegistry.findUtilityFunctionSignature` 的名称（优先无前缀）。
  - `cFunctionName`：最终 C 调用符号（`godot_` 前缀）。
  - `signature`：`FunctionSignature`。

建议规则：
1. 先按原名查签名。
2. 若失败且原名以 `godot_` 开头，则剥离前缀后再查。
3. 若仍失败，抛 `InvalidInsnException`（未找到 utility function）。
4. `cFunctionName` 统一输出为：
   - 原名已是 `godot_` 开头：直接用原名；
   - 否则：拼接 `godot_` + 原名。

---

## 5. 校验规则（实现核心）

## 5.1 参数存在性与数量校验

设：
- `provided = instruction.args().size()`
- `fixed = signature.parameterCount()`
- `isVararg = signature.isVararg()`

规则：
- 先校验每个参数变量 ID 在 `func` 中存在，不存在立即报错。
- 非 vararg：
  - 默认要求 `provided == fixed`。
  - 若未来 utility 元数据出现 default 值，可放宽为“缺失参数必须均有默认值”。
- vararg：
  - 要求 `provided >= fixed`。

## 5.2 参数类型校验

- 前 `fixed` 个参数：逐个用 `ClassRegistry.checkAssignable(argType, paramType)` 校验。
- vararg 额外参数（`i >= fixed`）：
  - 本阶段强制要求可赋给 `Variant`，即 `checkAssignable(argType, GdVariantType.VARIANT)` 为真。
  - 实际上当前实现下通常等价“必须就是 `Variant`”；非 `Variant` 需在上游先 `pack_variant`。

## 5.3 返回值数量与类型校验

设 `retType = signature.returnType()`：

- `retType == null` 或 `retType instanceof GdVoidType`：
  - `instruction.resultId()` 必须为 `null`，否则报错。
- 其他（non-void）：
  - `instruction.resultId()` 必须非空，否则报错。
  - result 变量必须存在。
  - result 变量必须可写（非 `ref`）。
  - `checkAssignable(retType, resultVar.type())` 必须为真。

---

## 6. 代码生成规则

## 6.1 非 vararg utility

- 构造参数列表：`List<ValueRef>`，每个参数使用 `bodyBuilder.valueOfVar(argVar)`。
- void：`bodyBuilder.callUtilityVoid(cFunctionName, args)`。
- non-void：`bodyBuilder.callUtilityAssign(target, cFunctionName, args)`。

## 6.2 vararg utility

### 6.2.1 调用参数组织

utility 函数 C 形参为：
- 固定参数（来自签名）
- `argv`（`const godot_Variant**`）
- `argc`（`godot_int`）

对应生成：
1. 固定参数照常构建为 `ValueRef`。
2. 额外参数数量 `extraCount = provided - fixed`。
3. `extraCount == 0`：
   - `argvExpr = "NULL"`
   - `argcExpr = "0"`
4. `extraCount > 0`：
   - 先声明：
     ```c
     const godot_Variant *<tmp>[] = { <extra1_ptr>, <extra2_ptr>, ... };
     ```
   - 其中 `<extra_ptr>` 规则：
     - 变量是 ref：`$id`
     - 非 ref：`&$id`
   - `argvExpr = <tmp>`
   - `argcExpr = <extraCount>`

### 6.2.2 发射调用

将最终参数拼成：`fixedArgs + argv + argc`。

- void：`callUtilityVoid(cFunctionName, finalArgs)`。
- non-void：`callUtilityAssign(target, cFunctionName, finalArgs)`。

> 说明：这里仍建议走 `CBodyBuilder.callUtilityVoid/callUtilityAssign`，避免绕过统一赋值语义（旧值释放/析构、对象 own/release 等）。

## 6.3 CBodyBuilder 需要配合的增强（提升健壮性）

为保证 `CallGlobalInsnGen` 实现稳定，建议同步补齐以下 `CBodyBuilder` 能力：

1. **统一 utility 名称解析与签名查找**
   - 问题：现有 `validateCallArgs/resolveReturnType` 对 `godot_` 前缀处理不一致，容易出现“有符号但查不到签名”。
   - 建议：新增内部解析入口（如 `resolveUtilityCall`），统一产出：
     - `lookupName`（registry 查询名）
     - `cFunctionName`（最终 C 符号）
     - `signature`
   - 并让 `validateCallArgs`、`resolveReturnType`、`call*` 共用该入口。

2. **提供 utility 专用调用 API（避免在生成器中拼接细节）**
   - 建议新增：
     - `callUtilityVoid(String funcName, List<ValueRef> args)`
     - `callUtilityAssign(TargetRef target, String funcName, List<ValueRef> args)`
   - 由 `CBodyBuilder` 内部完成：
     - 参数数量/类型校验
     - vararg 组包
     - 调用发射
   - 这样 `CallGlobalInsnGen` 仅保留 IR 变量解析与错误上下文，降低重复逻辑和漂移风险。

3. **内建 vararg argv/argc 组包能力**
   - 建议在 builder 内实现专用渲染流程（如 `renderUtilityArgs`）：
     - 按 `signature.parameterCount()` 分离 fixed 与 extra。
     - `extra == 0` 时统一发射 `NULL, (godot_int)0`。
     - `extra > 0` 时统一声明唯一临时数组：
       ```c
       const godot_Variant* __gdcc_tmp_argv_N[] = { ... };
       ```
       并发射 `__gdcc_tmp_argv_N, (godot_int)<extraCount>`。
   - `argc` 建议显式转为 `godot_int`，避免字面量类型歧义。

4. **严格约束 vararg extra 参数类型**
   - 约束：extra 参数必须可赋给 `Variant`（当前阶段等价要求 `Variant`）。
   - 报错应在 builder 内统一输出，减少各生成器重复构造错误文案。
   - 对表达式 extra 参数应明确拒绝或要求先落地为变量，避免生命周期与地址稳定性问题。

5. **补充“原样参数”通道，避免错误地被 `renderArgument` 二次处理**
   - `argv` 与 `argc` 不是普通 GDScript 值语义参数，不应再走自动 `&`/对象指针转换。
   - 建议新增内部参数模型（例如 `RenderedArg` 或 `RawArg`），支持“已渲染 C 片段直接拼接”。
   - 这样可以防止 `NULL`、数组名、`(godot_int)N` 被误判成需取地址或做对象转换。

6. **增强 utility 路径的返回类型解析**
   - `resolveReturnType` 需支持 `godot_` 前缀名和无前缀名双向解析。
   - 保证 `callUtilityAssign` 在仅传 IR 函数名时也能稳定推导返回类型，避免把 utility 误判为 non-utility。

7. **新增针对 CBodyBuilder 的回归测试**
   - 在 `CBodyBuilderPhaseCTest`（或新增专用测试类）补充：
     - 前缀/无前缀 utility 名解析一致性；
     - vararg `extra=0` => `NULL,(godot_int)0`；
     - vararg `extra>0` => 生成 argv 临时数组且命名唯一；
     - vararg extra 非 `Variant` 报错；
     - `callUtilityAssign` 对 void utility 报错、对 non-void utility 正常赋值。

---

## 7. 异常信息规范

统一通过 `bodyBuilder.invalidInsn(...)` 抛出，消息建议包含：
- utility 名称
- 参数位置（第几个参数）
- 期望/实际类型
- 期望/实际参数数量
- 返回值规则冲突信息

示例：
- `Global utility function 'foo' not found in registry`
- `Too few arguments for utility 'print': expected at least 1, got 0`
- `Vararg argument #2 of utility 'print' must be Variant, got 'String'`
- `Utility 'deg_to_rad' returns 'float' but resultId is missing`
- `Utility 'print' has no return value but resultId is provided`

---

## 8. 具体改动清单

1. `src/main/java/dev/superice/gdcc/backend/c/gen/insn/CallGlobalInsnGen.java`
   - 改为 `CInsnGen` 实现。
   - 移除模板依赖逻辑。
   - 增加 utility 解析、校验、vararg 组参与调用发射逻辑。

2. `src/main/java/dev/superice/gdcc/backend/c/gen/CCodegen.java`
   - 在静态注册块加入 `registerInsnGen(new CallGlobalInsnGen())`。

3. `src/main/java/dev/superice/gdcc/backend/c/gen/CBodyBuilder.java`
   - 增加 utility 名称统一解析入口（前缀/无前缀兼容）。
   - 增加 utility 专用调用 API（建议 `callUtilityVoid/callUtilityAssign`）。
   - 增加 vararg 组包能力（argv 临时数组 + `godot_int` argc）。
   - 增加 raw 参数通道，避免 `argv/argc` 被普通参数渲染逻辑误处理。

4. `src/main/java/dev/superice/gdcc/backend/c/gen/CGenHelper.java`
   - 已新增 utility 名称归一化与解析公共能力：
     - `normalizeUtilityLookupName(String functionName)`
     - `toUtilityCFunctionName(String functionName)`
     - `resolveUtilityCall(String functionName)`
   - 已新增 `UtilityCallResolution` record，统一承载：
     - `lookupName`
     - `cFunctionName`
     - `signature`
   - `CBodyBuilder` 与 `CallGlobalInsnGen` 已改为复用该公共解析能力，避免重复实现。

---

## 9. 测试计划（JUnit5）

建议新增：
- `src/test/java/dev/superice/gdcc/backend/c/gen/CallGlobalInsnGenTest.java`
- `src/test/java/dev/superice/gdcc/backend/c/gen/CBodyBuilderCallUtilityTest.java`（或并入 `CBodyBuilderPhaseCTest`）

最小覆盖矩阵：

1. **成功路径**
- `call_global "deg_to_rad" $x`：non-vararg + 有返回值。
- `call_global "print" $v1`：vararg + 无额外参数，生成 `NULL, 0`。
- `call_global "print" $v1 $v2 $v3`：vararg + 生成 `argv` 数组与 `argc=2`。
- `call_global "godot_print" ...`：带前缀输入也可解析。

2. **失败路径**
- utility 不存在。
- 参数变量不存在。
- 非 vararg 参数数量不匹配。
- vararg 参数不足 fixed 部分。
- 固定参数类型不匹配。
- vararg 额外参数非 `Variant`。
- void utility 却给了 resultId。
- non-void utility 没有 resultId。
- result 变量不存在。
- result 变量为 `ref`。
- result 类型不兼容。

3. **接入验证**
- `CCodegen` 遇到 `CALL_GLOBAL` 不再抛“Unsupported instruction opcode”。

4. **CBodyBuilder 协同验证**
- utility 名称前缀归一化正确（`print`/`godot_print`）。
- vararg `extra=0` 时发射 `NULL,(godot_int)0`。
- vararg `extra>0` 时发射 argv 临时数组且命名唯一。
- vararg extra 非 `Variant` 时抛出 `InvalidInsnException`。

建议执行命令（按仓库约定）：

```bash
./gradlew test --tests CallGlobalInsnGenTest --no-daemon --info --console=plain
./gradlew test --tests CBodyBuilderCallUtilityTest --no-daemon --info --console=plain
./gradlew test --tests CCodegenTest --no-daemon --info --console=plain
./gradlew classes --no-daemon --info --console=plain
```

---

## 10. 分阶段落地步骤

### Phase A：生成器改造与注册
- [x] `CallGlobalInsnGen` 改为 `CBodyBuilder` 路径。
- [x] 在 `CCodegen` 完成注册。

### Phase B：CBodyBuilder 能力补齐
- [x] utility 名称归一化解析入口落地。
- [x] `callUtilityVoid/callUtilityAssign`（或等价 API）落地。
- [x] vararg argv/argc 组包和 raw 参数通道落地。

### Phase C：完整校验落地
- [x] utility 名称解析与签名查找。
- [x] 参数数量/类型校验。
- [x] 返回值数量/类型校验。

### Phase D：vararg 代码生成
- [x] `argv/argc` 组装。
- [x] `extraCount=0` 的 `NULL,0` 分支。
- [x] `extraCount>0` 的局部数组分支。

### Phase E：测试与回归
- [x] 新增 `CallGlobalInsnGenTest`。
- [x] 新增/补充 CBodyBuilder utility 调用专项测试。
- [x] 跑针对性测试并修复。

---

## 11. 风险与应对

1. **风险：直接用 `godot_` 名调用时签名查不到**
- 应对：在生成器内做双路径查找（原名/去前缀名）。

2. **风险：vararg 额外参数类型来源不规范**
- 应对：本期严格要求 `Variant`，明确报错提示“先 pack_variant”。

3. **风险：绕过 builder 导致生命周期语义丢失**
- 应对：调用发射尽量走 `callUtilityVoid/callUtilityAssign`。

4. **风险：未来 utility 元数据引入 default 参数**
- 应对：数量校验逻辑预留 default 分支，不阻塞后续扩展。

---

## 12. 完成标准（DoD）

- `CALL_GLOBAL` 在 C 后端可用，且仅支持 utility 函数。
- `CBodyBuilder` 具备 utility 专用调用能力，`CallGlobalInsnGen` 不再手写 vararg 组包细节。
- 命名转换符合 `godot_<function_name>` 约定。
- vararg utility 正确生成 `argv/argc` 调用。
- 参数与返回值数量/类型校验完整，错误定位到函数/块/指令。
- 对应单测通过，且 `CCodegen` 不再因 `CALL_GLOBAL` 未注册失败。

---

## 13. 附：本次调研确认的事实清单

- `utility_functions` 中 vararg utility 共 113 个（当前 Godot 4.5.1 API 资源）。
- vararg utility 的固定参数部分均在 API 中显式给出，额外参数通过 `argv/argc` 传入。
- `print`/`printerr` 在 API 中无 `return_type` 字段，当前等价 void。
- `entry.c.ftl` 里已有 `godot_print(&msg_variant, NULL, 0);` 现成范式，可作为实现对照。

---

## 14. 阶段进度记录（2026-02-19）

### 已完成（第一阶段）

- `CBodyBuilder` 已新增 utility 名称归一化解析，`print` / `godot_print` 可统一识别。
- `CBodyBuilder` 已新增 utility 专用 API：
  - `callUtilityVoid(String funcName, List<ValueRef> args)`
  - `callUtilityAssign(TargetRef target, String funcName, List<ValueRef> args)`
- 已落地 utility vararg 组包能力：
  - `extra=0` 生成 `NULL, (godot_int)0`
  - `extra>0` 生成 `const godot_Variant* __gdcc_tmp_argv_N[] = {...}` + `(godot_int)N`
- 已新增 raw 参数通道，避免 `argv/argc` 被普通参数渲染逻辑（自动 `&` / 对象指针转换）二次处理。
- 已新增并通过单元测试：`CBodyBuilderCallUtilityTest`。

### 已完成（代码审阅阶段，2026-02-19）

审阅范围：对照本方案及 `doc/gdcc_c_backend.md`，检查 `CBodyBuilder` utility 相关改动的缺陷与可改进之处。

**修复的缺陷与改进**：

1. **移除 `resolveUtilityCall` 中的死代码回退分支**
   - `ClassRegistry.utilityByName` 以无前缀名为 key，normalize 后查询已覆盖全部合法输入，回退分支（用原名再查）永远不会命中。

2. **消除 `renderVarargVariantPointer` 与 `validateCallArgs` 的重复校验**
   - 类型校验和变量性校验已在 `validateCallArgs` 中完成，render 阶段不再重复，避免双重维护负担。改为注释说明前置条件。

3. **新增 `rejectVarargUtilityViaNonUtilityPath` 防误用守卫**
   - 在 `callVoid` / `callAssign` 入口处检测 vararg utility，立即抛错，防止误用生成缺少 argv/argc 的非法 C 代码。

4. **提取 `emitCallResultAssignment` 公共方法**
   - `callAssign` 与 `callUtilityAssign` 原本各自重复了"destroy 旧值 → ptr 转换 → 赋值 → own 新值 → 标记初始化"逻辑，现提取为私有方法统一维护。

5. **统一 utility 路径错误消息风格**
   - 消息中一律使用 "utility function" 而非 "function"，以区分 utility 和普通函数调用错误。
   - vararg 场景的 "too few" 消息加入 "at least" 前缀。

6. **补充 `renderUtilityArgs` 和 `renderVarargVariantPointer` 的文档注释**
   - 说明 argv 指针的生命周期假设（依赖 validateCallArgs 保证 extra 参数是变量引用）。

**此次审阅未修改（低优先级或待后续处理）**：

- `callUtilityVoid` 不校验 non-void utility 返回值被丢弃（方案允许有意忽略）。
- non-vararg utility 允许通过通用 `callVoid`/`callAssign` 调用，当前不强制走专用路径。
- 已知 `checkGlobalFuncReturnGodotRawPtr` 对所有 utility 永远返回 true（因 cFunctionName 总以 `godot_` 开头），但因为后续有 `instanceof GdObjectType` 守卫所以不产生错误，语义注释已在 §12.5 补充。

### 本阶段执行过的验证命令

```bash
./gradlew test --tests CBodyBuilderCallUtilityTest --no-daemon --info --console=plain
./gradlew test --tests CBodyBuilderPhaseCTest --no-daemon --info --console=plain
./gradlew classes --no-daemon --info --console=plain
```

### 已完成（CallGlobalInsnGen 落地，2026-02-19）

本阶段将方案从“仅设计”推进为“可运行实现”，并补齐了 CALL_GLOBAL 接入和专项测试。

**核心实现状态**：

1. **生成器迁移完成**
   - `src/main/java/dev/superice/gdcc/backend/c/gen/insn/CallGlobalInsnGen.java`
   - 已改为 `CInsnGen<CallGlobalInsn>`，移除模板依赖与 `TemplateInsnGen` 路径。

2. **CALL_GLOBAL 接入完成**
   - `src/main/java/dev/superice/gdcc/backend/c/gen/CCodegen.java`
   - 已在静态注册块加入 `registerInsnGen(new CallGlobalInsnGen())`。

3. **utility 解析与校验落地**
   - 生成器内已实现 utility 解析 record（`lookupName/cFunctionName/signature`）。
   - 支持 `print` 与 `godot_print` 双形态输入解析。
   - 对参数 operand 形态做了约束：`CallGlobalInsn.args` 必须是变量 operand（`VariableOperand`）。
   - 参数变量存在性校验在生成器中完成。
   - 参数数量/类型、vararg extra 的 `Variant` 约束委托给 `CBodyBuilder.callUtility*` 路径统一校验。
   - 返回值规则（void/non-void 与 `resultId`）、result 变量存在性、`ref` 可写性、返回类型兼容性均已落地。

4. **调用发射策略统一**
   - void utility 统一走 `callUtilityVoid(...)`。
   - non-void utility 统一走 `callUtilityAssign(...)`。
   - vararg `argv/argc` 组包仍由 `CBodyBuilder` 内部负责，避免在生成器重复实现。

5. **新增专项测试**
   - 新增：`src/test/java/dev/superice/gdcc/backend/c/gen/CallGlobalInsnGenTest.java`
   - 已覆盖：
     - 成功路径：`deg_to_rad` 返回值赋值、`print` vararg 的 `NULL/(godot_int)0`、`argv` 数组分支、前缀名输入。
     - 失败路径：utility 不存在、resultId 与返回值规则冲突、result 为 ref、result 类型不兼容、参数变量不存在、参数 operand 非变量。

### 本次实现执行过的验证命令（2026-02-19）

```bash
./gradlew test --tests CallGlobalInsnGenTest --no-daemon --info --console=plain
./gradlew test --tests CBodyBuilderCallUtilityTest --no-daemon --info --console=plain
./gradlew classes --no-daemon --info --console=plain
```

### 已完成（utility 名称归一化抽取 + 生成器双重校验，2026-02-19）

本轮聚焦“消除 utility 名称解析重复逻辑”并增强 `CallGlobalInsnGen` 的防御性校验。

**本次新增/变更**：

1. **utility 解析能力抽取到 `CGenHelper`**
   - 新增 `resolveUtilityCall` 统一解析入口（兼容 `print` / `godot_print`）。
   - 新增 `UtilityCallResolution` 作为跨组件共享数据模型。

2. **`CBodyBuilder` 改为复用 helper 解析**
   - 移除 builder 内部重复的 utility 名称归一化方法与私有解析 record。
   - `callUtility*`、`validateCallArgs`、`resolveReturnType` 等路径均通过 helper 解析结果协同工作。

3. **`CallGlobalInsnGen` 增强“前置校验”，并保留与 builder 双重校验**
   - 生成器端新增参数数量/类型校验（含 vararg extra 必须为 `Variant`）。
   - 生成器端继续校验 resultId 规则、result 变量存在性、可写性、返回类型兼容性。
   - builder 侧仍会再次校验（双重校验已按项目要求保留）。

4. **单测补强**
   - 新增：`src/test/java/dev/superice/gdcc/backend/c/gen/CGenHelperUtilityResolutionTest.java`
     - 覆盖 utility 名称归一化、C 符号名转换、prefixed/unprefixed 解析、missing 场景。
   - 扩展：`src/test/java/dev/superice/gdcc/backend/c/gen/CallGlobalInsnGenTest.java`
     - 新增 prefixed non-void 成功路径；
     - 新增更多失败路径：参数过多、vararg fixed 参数不足、fixed 类型不匹配、vararg extra 非 Variant、result 变量不存在。

### 本阶段执行过的验证命令（2026-02-19，补充）

```bash
./gradlew test --tests CallGlobalInsnGenTest --no-daemon --info --console=plain
./gradlew test --tests CGenHelperUtilityResolutionTest --no-daemon --info --console=plain
./gradlew test --tests CBodyBuilderCallUtilityTest --no-daemon --info --console=plain
./gradlew classes --no-daemon --info --console=plain
```

---

## 15. 对后续工程有价值的沉淀（建议）

1. **增加接入级回归测试**
   - 建议在 `CCodegen` 侧新增用例，显式断言 `CALL_GLOBAL` 不再触发 `Unsupported instruction opcode`。

2. **为未来“非 utility 全局调用”预留扩展位**
   - 目前 `CALL_GLOBAL` 仅支持 utility（符合本期范围）。
   - 若后续支持非 utility 全局符号，建议在生成器中引入清晰分流（utility path / symbol path）并保持错误信息可区分。

3. **持续保持错误信息可定位**
   - 当前路径已通过 `bodyBuilder.invalidInsn(...)` 保留函数/块/指令位置信息。
   - 后续新增分支时建议复用同一错误出口，避免回退到仅字符串异常。



---

## 16. CallGlobalInsnGen 当前实现审阅报告（2026-02-19）

### 16.1 审阅范围

- 主实现文件：`src/main/java/dev/superice/gdcc/backend/c/gen/insn/CallGlobalInsnGen.java`
- 协同实现文件：
  - `src/main/java/dev/superice/gdcc/backend/c/gen/CBodyBuilder.java`（utility 调用路径）
  - `src/main/java/dev/superice/gdcc/backend/c/gen/CGenHelper.java`（utility 名称解析）
  - `src/main/java/dev/superice/gdcc/backend/c/gen/CCodegen.java`（指令注册与分发）
  - `src/main/java/dev/superice/gdcc/backend/c/gen/CInsnGen.java`（生成器接口）
- 测试文件：
  - `src/test/java/dev/superice/gdcc/backend/c/gen/CallGlobalInsnGenTest.java`
  - `src/test/java/dev/superice/gdcc/backend/c/gen/CBodyBuilderCallUtilityTest.java`
  - `src/test/java/dev/superice/gdcc/backend/c/gen/CGenHelperUtilityResolutionTest.java`
- 语义基线文档：
  - `doc/gdcc_c_backend.md`
  - `doc/gdcc_low_ir.md`
  - `doc/gdextension-lite.md`
  - `doc/module_impl/cbodybuilder_implementation_guide.md`

### 16.2 当前状态概述

所有现有测试均通过（`CallGlobalInsnGenTest`、`CBodyBuilderCallUtilityTest`、`CGenHelperUtilityResolutionTest`、`CBodyBuilderPhaseCTest`），编译无错误无警告。`CallGlobalInsnGen` 已注册到 `CCodegen`，可处理 `CALL_GLOBAL` 指令的 utility function 调用。

---

### 16.3 发现的问题（按严重度分级）

#### P0-1：`CallGlobalInsnGen.validateCallArgs` 与 `CBodyBuilder.validateCallArgs` 双重校验存在语义差异

**现状**：

`CallGlobalInsnGen.validateCallArgs`（第 100–141 行）操作 `List<LirVariable>`，而 `CBodyBuilder.validateCallArgs`（第 550–593 行）操作 `List<ValueRef>`。两者的校验逻辑高度相似但存在以下差异：

1. **CBodyBuilder 侧额外校验了 vararg extra 参数必须是 `VarValue`**（第 588 行 `if (!(arg instanceof VarValue))`），而 **CallGlobalInsnGen 侧没有此校验**。这是因为 InsnGen 层面操作的是 `LirVariable`（都是变量），到 builder 层面才包装成 `ValueRef`，所以 InsnGen 层面此校验天然满足。但这意味着如果未来 InsnGen 的参数构造逻辑改变（例如允许非变量 ValueRef），此处会悄然失去守护。

2. **错误消息不一致**：CallGlobalInsnGen 使用 `"utility '"` 而 CBodyBuilder 使用 `"utility function '"`，不符合 `doc/gdcc_c_backend.md` §"Error Messages for Utility Validation"中统一使用 **"utility function"** 的约定。

   - `CallGlobalInsnGen` 第 108 行：`"Too many arguments for utility '"`
   - `CBodyBuilder` 第 557 行：`"Too many arguments for utility function '"`
   - `CallGlobalInsnGen` 第 114 行：`"Too few arguments for utility '"`
   - `CBodyBuilder` 第 564 行：`"Too few arguments for utility function '"`
   - `CallGlobalInsnGen` 第 128 行：`"Argument #" + ... + " of utility '"`
   - 同理其余消息。

**影响**：
- 虽然双重校验本身不产生逻辑错误（InsnGen 先校验，builder 后校验，都会拦截非法输入），但错误消息不一致会导致同一类问题在不同触发路径下报出不同措辞，增加用户排查困难。
- 双重校验增加了维护负担——如果校验规则变更需要同步两处。

#### P0-2：非 void utility 的返回值赋值未参与 `CBodyBuilder` 的完整赋值语义

**现状**：

`CallGlobalInsnGen.generateCCode` 第 62 行：
```java
bodyBuilder.callUtilityAssign(bodyBuilder.targetOfVar(resultVar), utility.cFunctionName(), args);
```

生成器在调用 `callUtilityAssign` 前对 `resultVar` 做了自己的校验（ref 检查、类型兼容性检查），然后将 `utility.cFunctionName()`（已经是 `godot_` 前缀的名字）传给 `callUtilityAssign`。

但 `callUtilityAssign` 内部（第 360 行）又会用这个已经带前缀的名字再次调用 `requireUtilityCall(funcName)`。而 `requireUtilityCall` 内部调用 `resolveUtilityCall(funcName)` → `helper.resolveUtilityCall(funcName)` → `normalizeUtilityLookupName(funcName)`，此函数会再次去除 `godot_` 前缀来查找。

虽然这个流程最终是正确的，但存在 **不必要的二次解析开销**：生成器已经持有 `UtilityCallResolution` 对象（包含 `signature`），却仍将 `cFunctionName` 传给 builder 让它重解析一遍。这是设计上的冗余。

**影响**：
- 性能影响微小但增加了路径复杂度。
- 更重要的是，如果 `normalizeUtilityLookupName` 的行为在边界条件下出现偏差（例如函数名恰好是 `"godot_"` 这 6 个字符），二次解析可能与第一次结果不一致。

#### P1-2：`CallGlobalInsnGen` 的 `requireUtilityCall` 错误消息使用原始 `functionName` 而非 `lookupName`

**现状**：

`CallGlobalInsnGen.requireUtilityCall`（第 69–74 行）：
```java
private @NotNull CGenHelper.UtilityCallResolution requireUtilityCall(..., @NotNull String functionName) {
    var utility = bodyBuilder.helper().resolveUtilityCall(functionName);
    if (utility == null) {
        throw bodyBuilder.invalidInsn("Global utility function '" + functionName + "' not found in registry");
    }
    return utility;
}
```

若用户 IR 中写的是 `godot_print`，此处会报 `"Global utility function 'godot_print' not found in registry"`。但实际 registry 是以 `"print"` 为 key 的。用户可能疑惑为什么 `godot_print` 查不到。

更好的做法是在错误消息中同时指出原始名称和归一化后的 lookup 名称，便于排查。

**影响**：中低。在正常场景下不触发（因为 `print` 能查到），但如果用户拼写错误（如 `godot_pritn`），错误消息可能误导用户以为 registry 使用 `godot_` 前缀 key。

#### P1-3：`CallGlobalInsnGen.validateCallArgs` 对 non-vararg utility 允许参数少于 fixed 只要有 default，但实际未生成 default 值调用代码

**现状**：

`CallGlobalInsnGen.validateCallArgs` 第 109–117 行：
```java
if (provided < fixed) {
    for (var i = provided; i < fixed; i++) {
        var param = signature.parameters().get(i);
        if (param.defaultValue() == null) {
            throw bodyBuilder.invalidInsn("Too few arguments for utility '...'");
        }
    }
}
```

如果参数个数小于 fixed 但缺失的参数都有 `defaultValue`，校验通过。但之后构建 `args` 列表时（第 33–36 行）只从 `argVars` 构建，`argVars` 的大小就是 `provided`。最终传给 `callUtilityVoid`/`callUtilityAssign` 的参数列表少于 fixed 个。

然而 `CBodyBuilder.renderUtilityArgs`（第 639 行 `var fixedLimit = Math.min(fixedCount, args.size())`）不会补充缺失参数，导致最终生成的 C 代码参数数量不足，产生 **编译错误或运行时崩溃**。

**注意**：当前 Godot 4.5.1 的 utility functions API 数据中，所有 utility function 的参数 `defaultValue` 均为 `null`，所以此路径实际上**当前不会被触发**。但这是一个潜在的正确性 bug——校验逻辑允许了一种它不支持的场景。

**影响**：中。当前不触发但一旦 API 数据引入 default 参数将产生错误 C 代码。

#### P1-4：`CBodyBuilder.validateCallArgs` 同样存在 default 参数校验通过但无补充逻辑的问题

**现状**：与 P1-3 完全同构——`CBodyBuilder.validateCallArgs`（第 561–567 行）也允许 `provided < fixed` 只要缺失参数有 default，但 `renderUtilityArgs` 不会生成 default 值填充。

**影响**：同 P1-3，两处校验逻辑和生成逻辑存在语义不匹配。

#### P1-5：生成器不校验 `instruction.args()` 中的 Operand 是否可能包含非 `VariableOperand` 类型以外的 IR 合法 operand

**现状**：

根据 `doc/gdcc_low_ir.md`，`call_global` 指令的 operand 格式为：
```
$<result_id>? = call_global "<function_name>" $<arg1_id> $<arg2_id> ...
```

参数应当只有 `VariableOperand`。但 `CallGlobalInsn.args()` 的类型声明是 `List<Operand>`（而非 `List<VariableOperand>`），所以在 IR 构造层面理论上可以放入任意 Operand 类型。

`CallGlobalInsnGen.resolveArgumentVariables` 第 84 行的 pattern matching 能正确拦截非 `VariableOperand`：
```java
if (!(operand instanceof LirInstruction.VariableOperand(var argId))) {
    throw bodyBuilder.invalidInsn("...must be a variable operand");
}
```

这是正确的防御，**此项不是 bug，而是确认防御到位**。但值得注意的是，`CallGlobalInsn` record 的 `args` 字段应考虑在类型层面约束为 `List<VariableOperand>` 以在编译期而非运行时捕获此类错误。

**影响**：低。当前防御有效，但类型系统层面可以更严格。

#### P2-1：测试覆盖缺口 — 未测试返回 Object 类型（含 GDCC 类型）的 utility

**现状**：

`CallGlobalInsnGenTest` 仅测试了 `float` 返回值（`deg_to_rad`）和 `void` 返回值（`print`）。未覆盖返回 `String`/`StringName` 等值语义类型的 utility — 需验证赋值时的 copy 语义和旧值 destroy。返回 GDCC Object 类型的 utility — 需验证 `fromGodotObjectPtr` 转换（`checkGlobalFuncReturnGodotRawPtr` 路径）。

**影响**：中低。当前 utility functions 很少返回 Object 类型，但补齐这些场景可以防止未来回归。

#### P2-2：测试覆盖缺口 — 未测试 `ref` 类型参数变量作为 vararg extra 参数

**现状**：

vararg extra 参数的 C 代码生成在 `CBodyBuilder.renderVarargVariantPointer` 中（第 669–681 行）：
```java
var code = varValue.generateCode(); // "$varId"
if (varValue.variable().ref()) {
    return code; // ref 变量已经是指针，直接用
}
return "&" + code; // 非 ref 加 &
```

测试中所有 vararg 参数都是非 ref 变量。未覆盖 ref 变量参数场景（应生成 `$varId` 而非 `&$varId`）。

**影响**：低。逻辑正确但缺少测试守护。

#### P2-3：测试覆盖缺口 — 未测试 `printerr` 等其他 vararg utility

**现状**：测试仅使用 `print` 和 `deg_to_rad` 两个 utility。虽然逻辑路径相同，但使用更多 utility 可以增强对 `FunctionSignature` 解析一致性的信心。

**影响**：很低。

#### P2-4：`CallGlobalInsnGen` 没有文档注释（Javadoc/markdown doc comment）

**现状**：类级别缺少 `///` 文档注释说明该生成器的职责、适用范围（仅 utility）、以及未来扩展方向（非 utility 全局符号）。`generateCCode` 方法也没有注释说明整体流程。

**影响**：可维护性降低。

#### P2-5：`CallGlobalInsnGen` 未处理 `callUtilityVoid` 对 non-void utility 的静默丢弃

**现状**：

根据 `doc/gdcc_c_backend.md` §"Calling Utility Functions"，`callUtilityVoid` 用于"no return value"的 utility。但代码中的分支（第 39–44 行）在 `returnType == null || returnType instanceof GdVoidType` 时调用 `callUtilityVoid`。

如果未来某个 utility 有返回值但 IR 合法地省略了 `resultId`（有意忽略返回值），当前逻辑会在第 46 行抛 `"resultId is missing"` 错误。

这意味着当前实现**不允许**有意忽略 non-void utility 的返回值，这与某些语言允许忽略函数返回值的惯例不同。方案文档 §14 审阅记录中提到"`callUtilityVoid` 不校验 non-void utility 返回值被丢弃（方案允许有意忽略）"，说明这是**有意遗留的限制**，但 `CallGlobalInsnGen` 的实现比方案更严格——它强制要求 non-void utility 必须提供 `resultId`。

**影响**：低。当前行为安全但可能限制了未来 IR 的灵活性。如果决定允许丢弃返回值，需同步修改此处分支。

---

## 17. 审阅问题修复状态同步（2026-02-19，第二轮）

本轮按“第 16 节最新审阅报告”执行修复，**参数默认值相关问题（P1-3/P1-4）按约定暂不处理**，其余项已落地如下：

### 已修复

1. **P0-1：InsnGen / Builder 参数校验重复与消息漂移**
   - `CallGlobalInsnGen` 已移除本地 `validateCallArgs`，参数数量/类型校验统一收敛到 `CBodyBuilder.callUtility*`。
   - 错误消息源统一到 builder utility 路径，避免同类错误多套文案。

2. **P0-2：`CallGlobalInsnGen` 到 `CBodyBuilder` 的 utility 二次解析冗余**
   - `CBodyBuilder` 新增基于 `UtilityCallResolution` 的重载：
     - `callUtilityVoid(UtilityCallResolution, List<ValueRef>)`
     - `callUtilityAssign(TargetRef, UtilityCallResolution, List<ValueRef>)`
   - `CallGlobalInsnGen.generateCCode` 第 50/53 行直接将已解析的 `utility` 对象传入，不再传 `cFunctionName` 让 builder 重查。
   - 基于 `String funcName` 的旧重载（第 320/374 行）仍保留供其他 builder 使用者使用，内部自行 resolve，不影响 `CallGlobalInsnGen` 路径。

3. **P1-2：utility not found 报错可读性**
   - `CallGlobalInsnGen.requireUtilityCall` 在报错中同时输出原始函数名与归一化 lookup key：
     - `Global utility function '<raw>' not found in registry (lookup key: '<normalized>')`

4. **P2-4：`CallGlobalInsnGen` 缺少文档注释**
   - 已补充类级与 `generateCCode` 方法级 `///` 注释，说明职责与流程。

5. **P2-5：non-void utility 返回值必须绑定 resultId（无法显式丢弃）**
   - `CBodyBuilder` 新增 `DiscardRef`（`TargetRef` 新实现）和 `discardRef()` 工厂方法。
   - `callAssign` / `callUtilityAssign` 新增“丢弃返回值”路径：
     - 目标是 `DiscardRef` 时仅发射调用语句，不执行赋值语义（destroy/release/own）。
   - `CallGlobalInsnGen` 对 non-void utility 在 `resultId == null` 时改为走 `discardRef()`，实现显式丢弃返回值。

6. **P2 覆盖缺口补强（部分）**
   - 新增/更新测试覆盖：
     - `callUtilityAssign` 丢弃返回值路径；
     - `callAssign` 丢弃返回值路径与 void 拒绝路径；
     - vararg extra 为 `ref Variant` 时指针拼接路径；
     - `printerr` 等其它 vararg utility 调用路径；
     - `CALL_GLOBAL` non-void 丢弃返回值路径。

### 暂未修复（按本轮范围排除）

- **P1-3 / P1-4（默认参数）**  
  当前仍保持“校验层允许 default，但渲染层不补默认实参”的现状；按计划留待后续专项修复。

### 本轮验证命令

```bash
./gradlew test --tests CBodyBuilderCallUtilityTest --tests CallGlobalInsnGenTest --tests CBodyBuilderPhaseCTest --no-daemon --info --console=plain
./gradlew classes --no-daemon --info --console=plain
```

## 18. 默认参数渲染修复状态同步（2026-02-19，第三轮）

本轮按 `doc/gdcc_c_backend.md` 的 “Default Argument Values” 约定，完成了 P1-3 / P1-4 的实现修复。

### 已修复

1. **P1-3：`CallGlobalInsnGen` 允许缺省参数但未补全**
   - `CallGlobalInsnGen` 继续统一走 `CBodyBuilder.callUtility*` 路径。
   - `CBodyBuilder.renderUtilityArgs` 现在会为缺失的 fixed 参数补齐默认值临时变量，不再生成参数缺失的非法 C 调用。

2. **P1-4：`CBodyBuilder.validateCallArgs` 与渲染逻辑不一致**
   - `CBodyBuilder` 新增 default 参数补全流程：
     - 将默认值字面量渲染为 C 表达式；
     - 表达式先赋值给临时变量；
     - 调用时使用该临时变量作为实参；
     - 调用后按临时变量生命周期统一销毁。
   - 对 generic `callVoid/callAssign` 路径新增约束：不再允许“省略参数并依赖 default”，避免 generic 路径与 utility 渲染能力不匹配。

3. **默认值表达式渲染能力（本轮新增）**
   - 已支持并用于 utility 默认参数补全：
     - 数值/布尔字面量直接生成；
     - `\"...\"` → `GD_STATIC_S(u8\"...\")`，并在 `String` 默认值场景下生成为 `godot_new_String_with_String(...)`；
     - `&\"...\"`（或 `\"...\"`）在 `StringName` 场景下生成为 `godot_new_StringName_with_StringName(GD_STATIC_SN(...))`；
     - `null`：
       - Object 参数 → `NULL`
       - Variant 参数 → `godot_new_Variant_nil()`
     - 非对象构造字面量（如 `Vector2(0, 0)`、`PackedVector2Array()`）→ `godot_new_<Type>(...)` 形态。

### 本轮新增测试覆盖

- `CBodyBuilderCallUtilityTest`
  - 省略 int default 参数时会生成 default temp 并参与调用；
  - 省略 string default 参数时会生成 temp，调用后销毁 temp。
- `CBodyBuilderPhaseCTest`
  - 更新 default 参数场景断言，验证 `callUtilityVoid` 的 default temp 补全行为。
- `CallGlobalInsnGenTest`
  - 覆盖 `CALL_GLOBAL` 调用 utility 且省略 optional 参数时，生成 default temp 补全调用。

### 本轮验证命令

```bash
./gradlew test --tests CBodyBuilderCallUtilityTest --tests CallGlobalInsnGenTest --tests CBodyBuilderPhaseCTest --no-daemon --info --console=plain
./gradlew classes --no-daemon --info --console=plain
```

## 19. 审阅问题修复状态全面审查（2026-02-19，第四轮）

本轮对照 §16.3 审阅报告中列出的全部问题，逐项核实当前代码的实际修复情况。

### P1-5：`CallGlobalInsn.args()` 类型为 `List<Operand>` 而非 `List<VariableOperand>` → ⚠️ 未改动（确认为低优先级）

**原始问题**：`CallGlobalInsn.args()` 的类型声明是 `List<Operand>`，运行时防御到位但编译期约束不足。

**当前状态**：
- `CallGlobalInsn` record 的 `args` 字段仍为 `List<Operand>`。
- `CallGlobalInsnGen.resolveArgumentVariables`（第 95–110 行）通过 pattern matching 对每个 operand 做 `instanceof VariableOperand` 检查，非变量 operand 立即抛 `InvalidInsnException`。
- 测试 `callGlobalNonVariableOperand` 验证了此防御。

**结论**：运行时防御有效，但类型层面未收紧。原审阅报告已标注为"确认防御到位"，影响为低。

**建议修改方法**：将 `CallGlobalInsn` record 的 `args` 字段类型从 `List<Operand>` 改为 `List<LirInstruction.VariableOperand>`，并在 `operands()` 方法中做适配。这会将检查提前到 IR 构造阶段（编译期+构造期），消除运行时 pattern matching 开销。但这会影响 IR 层定义，需评估是否有其他指令类同样需要统一调整。

---

### P2-1：测试未覆盖返回 Object 类型的 utility → ⚠️ 未补充

**原始问题**：测试仅覆盖 `float`（`deg_to_rad`）和 `void`（`print`）返回值。未覆盖返回 `String`/`StringName` 等值语义类型的 utility — 需验证赋值时的 copy 语义和旧值 destroy。返回 GDCC Object 类型的 utility — 需验证 `fromGodotObjectPtr` 转换（`checkGlobalFuncReturnGodotRawPtr` 路径）。

**当前状态**：
- `CallGlobalInsnGenTest` 当前 utility 列表仅包含 `print`（void）、`deg_to_rad`（float）、`utility_with_default`（void）。
- `CBodyBuilderCallUtilityTest` 中也仅测试 `float` 和 `void` 返回值。
- 未新增返回 `String`/`StringName`（需验证 copy 语义和旧值 destroy）或返回 Object（需验证 own 语义和 `fromGodotObjectPtr` 转换）的 utility 测试。

**结论**：未修复。影响为中低，因当前 Godot utility 很少返回 Object 类型，但作为防回归手段有价值。

**建议修改方法**：
1. 在测试辅助方法 `utilityApi()` 中新增假 utility，例如：
   - 返回 `String` 的 utility（如 `fake_string_util`）→ 验证赋值时的 copy 语义。
   - 返回 engine Object 的 utility（如 `fake_object_util`）→ 验证 `emitCallResultAssignment` 中的 own 语义。
   - 返回 GDCC Object 的 utility（如 `fake_gdcc_object_util`）→ 验证 `fromGodotObjectPtr` 转换。
2. 新增测试用例调用这些 utility 并断言生成的 C 代码包含正确的 copy/own/destroy 语句。

### 修复状态汇总表

| 编号 | 严重度 | 描述 | 修复状态 |
|------|--------|------|----------|
| P0-1 | 高 | InsnGen/Builder 双重校验与消息漂移 | ✅ 已修复 |
| P0-2 | 高 | utility 二次解析冗余 | ✅ 已修复 |
| P1-2 | 中 | utility not found 报错缺少 lookup key | ✅ 已修复 |
| P1-3 | 中 | InsnGen 缺省参数无补全 | ✅ 已修复 |
| P1-4 | 中 | Builder 缺省参数无补全 | ✅ 已修复 |
| P1-5 | 低 | `CallGlobalInsn.args` 类型宽泛 | ⚠️ 未改动（运行时防御有效） |
| P2-1 | 中低 | 测试未覆盖 Object 返回类型 utility | ⚠️ 未补充 |
| P2-2 | 低 | 测试未覆盖 ref vararg extra | ✅ 已修复 |
| P2-3 | 很低 | 测试未覆盖其他 vararg utility | ✅ 已修复 |
| P2-4 | 低 | 缺少文档注释 | ✅ 已修复 |
| P2-5 | 低 | non-void utility 无法丢弃返回值 | ✅ 已修复 |

### 遗留项分析

**P1-5（`CallGlobalInsn.args` 类型宽泛）**：
- 影响：低。运行时 pattern matching 防御已到位，有测试覆盖。
- 修改方法：将 `CallGlobalInsn` record 的 `args` 字段类型从 `List<Operand>` 改为 `List<LirInstruction.VariableOperand>`，并在 `operands()` 方法中做适配。这会将检查提前到 IR 构造阶段（编译期+构造期），消除运行时 pattern matching 开销。但这会影响 IR 层定义，需评估是否有其他指令类同样需要统一调整。

**P2-1（测试未覆盖 Object 返回类型 utility）**：
- 影响：中低。`emitCallResultAssignment` 的 own/release/destroy 和 `fromGodotObjectPtr` 转换路径在当前 utility 测试中未被触发。
- 修改方法：
  1. 在 `CallGlobalInsnGenTest.utilityApi()` 和 `CBodyBuilderCallUtilityTest` 的 API fixtures 中新增返回 `String` 和 engine Object 类型的假 utility。
  2. 新增测试用例验证：
     - 返回 `String` 的 utility 赋值到 `String` 变量时，生成 copy assign + old destroy + new copy。
     - 返回 engine Object 的 utility 赋值到 Object 变量时，生成 `try_release_object`/`try_own_object`。
     - 返回 GDCC Object 的 utility 赋值到 GDCC Object 变量时，生成 `gdcc_object_from_godot_object_ptr` 转换。

---

### 本轮验证命令

```bash
./gradlew test --tests CallGlobalInsnGenTest --tests CBodyBuilderCallUtilityTest --tests CGenHelperUtilityResolutionTest --tests CBodyBuilderPhaseCTest --no-daemon --info --console=plain
./gradlew classes --no-daemon --info --console=plain
```
