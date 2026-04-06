# PROPERTY_INIT Lowering 修复计划

状态：计划维护中

## 1. 问题定义

当前 `PROPERTY_INIT` 已经在 frontend pre-pass 中被建模为独立的函数级 lowering 单元，但它仍停留在 shell-only 状态，没有进入 CFG build 和 body lowering。与此同时，backend 继续把 `LirPropertyDef.initFunc` 当成一个可执行的真实函数消费，导致 frontend/backend 合同失配。

这条失配链路当前已经固定在仓库里：

- `FrontendLoweringFunctionPreparationPass`
  - 为 supported property initializer 发布 `FunctionLoweringContext.Kind.PROPERTY_INIT`
  - 为 property 回写 hidden synthetic `_field_init_<property>` shell
- `FrontendLoweringBuildCfgPass`
  - 对 `PROPERTY_INIT` 只做 shell-only 校验，不发布 `frontendCfgGraph`
- `FrontendLoweringBodyInsnPass`
  - 对 `PROPERTY_INIT` 只做 shell-only 校验，不写入 `LirBasicBlock`
- `FrontendLoweringPassManagerTest`
  - 当前显式断言 property-init context 在 lowering 结束后仍然 `basicBlockCount == 0`
  - 当前显式断言 property-init context 没有 frontend CFG graph
- `src/main/c/codegen/template_451/entry.c.ftl`
  - 在 class constructor 中无条件生成 `self->prop = Class_<init_func>(self);`
- `CCodegen`
  - 仅在 `property.getInitFunc() == null` 时为 property 合成 backend 默认 init function
  - 对 frontend 已经回写了 `initFunc` 但仍 shell-only 的 property，不再走默认值兜底
  - `generateFunctionPrepareBlock()` / `ensureFunctionFinallyBlock()` 继续对所有函数统一追加控制流

这意味着：frontend 已经发布了 “有名字的 property init function”，backend 就会假定它已经是 executable function。当前 shell-only `PROPERTY_INIT` 进入 backend 后，不是被正确拒绝，而是被当成普通函数继续改写控制流。

## 2. 根因分析

根因不是 backend 模板单点错误，而是函数级 lowering 合同在两个阶段之间不一致：

1. frontend pre-pass 把 property initializer 升格成了真实的 `LirFunctionDef` shell；
2. frontend CFG/body pass 仍把它视为 future work，因此不 materialize body；
3. backend 则把所有带 `initFunc` 的 property 一律视为“已有可调用函数体”。

当前最危险的具体后果有两个：

- class constructor 模板会无条件调用 `_field_init_*`
- backend prepare/finally pass 会继续改写这个空壳函数

其中第二点会把一个 `entryBlockId == ""` 的 shell 继续推进到 backend 控制流路径，形成错误的 prepare/finally graft，而不是在边界处 fail-fast。这个行为会掩盖真正的 owner 问题，让 bug 以“奇怪控制流”而不是“property init 尚未 lowering” 的形式暴露。

## 3. 修复目标

目标合同必须收敛为下面二选一，不允许中间态泄漏到 backend：

1. property 没有 frontend 生成的 `initFunc`
   - backend 可以继续为“无 initializer”的 property 合成默认值 init helper
2. property 有 `initFunc`
   - 该函数必须已经拥有完整的 frontend-lowered body
   - backend 不再负责替它补 body，也不允许把 shell-only function 当成真实函数继续改写

本次修复的正式目标是：

- 让 compile-ready property initializer 进入与 executable body 同一套函数级 lowering 管线
- `PROPERTY_INIT` 使用独立 context，但不再长期停留在 shell-only
- backend 只消费“已 lowering 的 property init function”或“backend 自己生成的默认值 helper”

## 4. 范围与非目标

本计划只覆盖当前 MVP 已允许的 property initializer 支持面：

- class property declaration
- declaration 自带 initializer expression
- 已通过 compile gate 的 property initializer island
- 当前 frontend rules 已冻结的 fail-closed 边界

本计划明确不在本轮解决：

- 脚本类 `static var` compile-ready 放行
- property `:=` 类型回写
- property initializer 访问同 class non-static property / method / signal / `self` 的完整初始化时序语义
- parameter default lowering
- `@onready` ready-time 执行模型

## 5. 推荐实施顺序

建议按两个提交阶段推进，而不是一次性大改：

1. 先加 backend fail-fast guard，阻止 shell-only `PROPERTY_INIT` 继续伪装成真实函数
2. 再接通 frontend `PROPERTY_INIT` CFG build 和 body lowering，最后移除旧的 shell-only 测试假设

这样做的原因是：当前仓库里已经存在会误消费 shell-only `initFunc` 的 backend 代码路径，先封口可以让后续 frontend 改造期间的中间状态更容易定位。

## 6. 分步实施与验收

### 第一步：补 backend 边界保护

执行状态：

- [x] 在 backend 默认值 helper 生成后增加 property-init readiness 校验，shell-only / 缺失 helper / 非法 entry block 会在 prepare/finally graft 前 fail-fast
- [x] 用 targeted unit tests 锚定 backend 默认值路径、已有 executable helper 路径，以及缺失 helper / shell-only helper 的 fail-fast 路径
- [x] 已运行 `CCodegenTest`，确认第一步改动没有打断现有 backend happy path

实现目标：

- 在 backend 正式生成 C 之前，验证每个 property 的 `initFunc` 合同
- 若 property 引用了 shell-only function，直接 fail-fast，并把 class/property/function 名字写进异常
- backend 默认值 helper 仍只负责 `initFunc == null` 的 property

建议改动点：

- `dev.superice.gdcc.backend.c.gen.CCodegen`
  - 在 `prepare(...)` 或 `generate()` 前增加 property-init 合同校验
  - 合法条件固定为：
    - `property.getInitFunc() == null`
    - 或 `owningClass` 中存在同名 function，且该 function 已拥有有效 body
- 明确禁止当前这种状态继续进入后续 pass：
  - `property.getInitFunc() != null`
  - 但 target function `basicBlockCount == 0` 或 `entryBlockId` 为空

验收细则：

- happy path：
  - 无 initializer 的 property 仍由 backend 默认值路径生成 helper
  - 已有真实 body 的 property init function 可继续进入 backend
- negative path：
  - shell-only `_field_init_*` 不再被 backend 悄悄 graft 成 prepare/finally 控制流
  - 异常文本必须包含 owning class、property name、init function name

建议测试：

- 新增或扩展 `CCodegenTest`
  - shell-only property init function 进入 backend 时抛出明确异常
  - `initFunc == null` 的 property 仍能生成默认值 helper

### 第二步：为 PROPERTY_INIT 发布 frontend CFG graph

执行状态：

- [x] `FrontendCfgGraphBuilder` 已新增 expression-rooted property-init CFG 入口，直接复用现有 value / short-circuit 构图逻辑并以 `RETURN` stop 收口
- [x] `FrontendLoweringBuildCfgPass` 已将 `PROPERTY_INIT` 从 shell-only guard 接到真实 CFG graph 发布路径，并校验 property declaration / initializer expression 合同
- [x] 已补充并运行 `FrontendCfgGraphBuilderTest`、`FrontendLoweringBuildCfgPassTest`、`FrontendLoweringPassManagerTest`、`FrontendLoweringBodyInsnPassTest`，确认 graph 发布、缺失 published fact fail-fast、以及默认 pipeline 下 property-init 仍保持 shell-only LIR 边界

实现目标：

- `PROPERTY_INIT` 不再只做 shell-only guard
- 为 property initializer expression 发布与 executable body 同一模型的 frontend CFG
- graph 入口仍由 `FunctionLoweringContext` 承载，不增加新的并行 lowering 体系

建议改动点：

- `FrontendCfgGraphBuilder`
  - 增加专用入口，例如 `buildPropertyInitializer(Expression root, FrontendAnalysisData analysisData)`
  - 该入口直接复用现有 value-building 逻辑，构造：
    - expression value sequence
    - `StopNode(kind = RETURN, returnValueId = ...)`
- `FrontendLoweringBuildCfgPass`
  - `PROPERTY_INIT` 分支从“仅校验 shell-only”改为“构图并发布 graph/region”
  - `sourceOwner` 必须是 property declaration
  - `loweringRoot` 必须是 initializer expression

建议 CFG 合同：

- property init graph 是 expression-rooted function graph，不伪装成 `Block`
- 不新增 property-init-only node kind
- 继续复用现有 `SequenceNode` / `StopNode`
- 当前支持面只允许 compile gate 已放行的 typed subtree facts

验收细则：

- happy path：
  - `PROPERTY_INIT` context 拥有 `frontendCfgGraph`
  - graph entry 到 return stop 的顺序稳定
  - value-op / call / member / constructor 等现有 executable-body 已支持 expression route 可直接复用
- negative path：
  - 缺失 published fact 时继续 fail-fast
  - 不允许在 CFG pass 中回退成第二套语义推导

建议测试：

- 扩展 `FrontendLoweringBuildCfgPassTest`
  - 断言 `PROPERTY_INIT` graph 已发布
  - 断言 graph 末端是 `RETURN` stop
  - 断言 property init expression 中的 published facts 被直接消费

### 第三步：接通 PROPERTY_INIT body lowering

实现目标：

- `FrontendLoweringBodyInsnPass` 对 `PROPERTY_INIT` 调用同一套 `FrontendBodyLoweringSession`
- property init function 最终拥有真实 `LirBasicBlock` 与 `entryBlockId`
- return 值通过现有 typed boundary materialization 路径写入，不另起特例

建议改动点：

- `FrontendLoweringBodyInsnPass`
  - `PROPERTY_INIT` 分支改为实际执行 lowering，而不是只保留 shell-only guard
- `FrontendBodyLoweringSession`
  - 保持现有函数级 lowering session，不拆新的 property-init session
  - 继续复用：
    - `declareSelfSlotIfNeeded()`
    - `declareCfgValueSlots()`
    - `createBlocks()`
    - `lowerBlocks()`
  - property init graph 若不包含 local declaration，`declareSourceLocalSlots()` 自然为空操作

理由：

- 现有 session 本身就是 graph-driven，而不是 block-AST-only
- `PROPERTY_INIT` 真正缺的是 graph publication，不是另一套 lowering 机制
- 继续复用单一 session 可以避免把 property initializer 做成长期常驻特例

验收细则：

- happy path：
  - property init target function 拥有有效 `entryBlockId`
  - property init target function 至少包含一个 block 和一个 `ReturnInsn`
  - non-static property init 继续只声明一个 `self` parameter
  - return boundary 与 property type / function return type 一致
- negative path：
  - 不允许绕过 compile gate 后再由 backend 报“缺 body”
  - 不允许 property init lowering 静默复用 executable body 的 AST owner 假设

建议测试：

- 新增 `FrontendLoweringBodyInsnPassTest`
  - literal initializer
  - constructor/call initializer
  - member/global helper initializer
  - 缺失 published fact 时的 fail-fast

### 第四步：收紧 backend 所有权边界

实现目标：

- backend 只消费 lowering 完成的 property init function
- backend 默认值 helper 与 frontend property init helper 的 owner 边界彻底分开
- 模板层不再依赖“frontend 给了 `initFunc` 名字，backend 就能补成函数体”这种隐式假设

建议改动点：

- 保留 `entry.c.ftl` 当前调用形态：
  - `self->prop = Class_<init_func>(self);`
- 但把它的前置条件写死：
  - 模板渲染前，所有 `property.initFunc` 要么来自 frontend-lowered helper，要么来自 backend 默认值 helper
- `CCodegen.generateDefaultGetterSetterInitialization()`
  - 继续只负责 `initFunc == null` 的 property
  - 不再承担修补 frontend shell-only property init 的隐式职责

验收细则：

- happy path：
  - frontend-lowered property init function 在生成的 C 中按正常 helper 被调用
  - backend 默认值 helper 仅服务于无 initializer property
- negative path：
  - 任何 shell-only property init 再次流入 backend 时，必须在模板渲染前失败

建议测试：

- `CCodegenTest`
  - frontend-lowered property init helper 的 C body 可生成
- `FrontendLoweringToCProjectBuilderIntegrationTest`
  - 含 property initializer 的脚本可从 frontend lowering 走到 C project 生成

### 第五步：替换旧合同并清理文档/测试

实现目标：

- 删除“`PROPERTY_INIT` 必须保持 shell-only”这条过时合同
- 将 property initializer 正式纳入 compile-ready lowering surface
- 更新所有把 property init 当作 shell-only 边界的测试锚点和文档

必须同步更新的文档：

- `doc/module_impl/frontend/frontend_lowering_plan.md`
- `doc/module_impl/frontend/frontend_lowering_cfg_pass_implementation.md`
- `doc/module_impl/frontend/frontend_lowering_func_pre_pass_implementation.md`
- `doc/module_impl/frontend/frontend_compile_check_analyzer_implementation.md`
- `doc/module_impl/frontend/frontend_rules.md`
- `doc/gdcc_low_ir.md`
- `doc/gdcc_c_backend.md`
- backend implementation docs（若新增 backend guard 或 invariant）

必须同步调整的测试锚点：

- `FrontendLoweringPassManagerTest`
  - 不再断言 property init 没有 CFG / 没有 body
- `FrontendLoweringBuildCfgPassTest`
  - 增加 property init graph publication
- `FrontendLoweringBodyInsnPassTest`
  - 增加 property init lowering
- `FrontendLoweringFunctionPreparationPassTest`
  - preparation 结束后仍保持 shell-only，这条测试继续成立
  - 但它只约束 preparation 阶段，不能再外推到整个 lowering pipeline
- `CCodegenTest`
- `FrontendLoweringToCProjectBuilderIntegrationTest`

验收细则：

- happy path：
  - 默认 frontend lowering pipeline 结束后，property init 与 executable body 一样拥有真实函数体
  - serializer 输出中的 `init_func` 指向真实 executable function
  - backend 不再需要修补 frontend 已声明但未 lowering 的 property init shell
- negative path：
  - 文档、测试、实现三者不再保留相互冲突的旧合同

## 7. 建议回归命令

优先跑 targeted tests，不跑全量套件：

```powershell
rtk powershell -ExecutionPolicy Bypass -File script/run-gradle-targeted-tests.ps1 -Tests FrontendLoweringFunctionPreparationPassTest,FrontendLoweringBuildCfgPassTest,FrontendLoweringBodyInsnPassTest,FrontendLoweringPassManagerTest
```

```powershell
rtk powershell -ExecutionPolicy Bypass -File script/run-gradle-targeted-tests.ps1 -Tests CCodegenTest,FrontendLoweringToCProjectBuilderIntegrationTest
```

若需要把 backend fail-fast 单独先落地，建议先只跑第一条 backend 相关新增测试，再接 frontend 全链路回归。

## 8. 与 Godot 现有模型的对齐说明

结合 `godotengine/godot` 中 `modules/gdscript/README.md` 对 GDScript “class 包含 implicit methods like initializers、runtime instance 负责实际执行”的描述，可以把 property initializer helper 视为 class-level implicit function，而不是 backend 临时补洞逻辑。

这里的结论是基于 README 的架构描述做的工程推断，不是逐行复刻 Godot 当前内部实现。对 GDCC 来说，更重要的是保持单一 owner：

- frontend 决定 property initializer 是否存在，以及它的语义事实
- lowering 负责把它 materialize 成真实函数体
- backend 只消费 lowering 完成后的函数，不再兼任“发现 shell 后替它补函数体”

## 9. 最终判定标准

修复完成的标志不是“backend 不再崩”，而是以下三条同时成立：

1. `PROPERTY_INIT` 不再在默认 lowering pipeline 末端保持 shell-only
2. backend 不再把 shell-only property init function 当作真实函数继续生成控制流
3. property initializer 的 owner 边界明确收敛为 “frontend 发布事实，lowering 写 body，backend 只消费结果”
