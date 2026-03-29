# Frontend Lowering Frontend CFG Graph 实施计划

> 本文档是 frontend lowering 第二阶段的当前主计划，定义“frontend-only CFG graph”及其 condition-evaluation-region 合同。它替代旧的 `frontend_lowering_cfg_pass_plan.md` 作为当前实施依据。新 CFG 必须在 `frontend.lowering.cfg` 包中独立实现，只由 `FrontendLoweringBuildCfgPass` 构建；现有 `FrontendLoweringCfgPass` 与 `FunctionLoweringContext.cfgNodeBlocks` 只应视为废弃中的过渡实现，尚未完成最终 CFG 架构，后续必须迁移并删除。

## 文档状态

- 状态：计划维护中（legacy metadata-only CFG pass 已落地，但仍属于未完成过渡层；下一阶段迁移到 frontend CFG graph）
- 更新时间：2026-03-29
- 适用范围：
  - `src/main/java/dev/superice/gdcc/frontend/lowering/**`
  - `src/main/java/dev/superice/gdcc/frontend/lowering/cfg/**`
  - `src/main/java/dev/superice/gdcc/frontend/lowering/pass/**`
  - `src/test/java/dev/superice/gdcc/frontend/lowering/**`
- 关联文档：
  - `doc/module_impl/common_rules.md`
  - `frontend_rules.md`
  - `frontend_lowering_plan.md`
  - `frontend_lowering_func_pre_pass_implementation.md`
  - `frontend_lowering_cfg_pass_plan.md`
  - `frontend_compile_check_analyzer_implementation.md`
  - `diagnostic_manager.md`
  - `doc/gdcc_low_ir.md`
- 本轮调研额外参考：
  - `E:/Projects/gdparser/src/main/java/dev/superice/gdparser/frontend/ast/ASTWalker.java`
  - `E:/Projects/gdparser/src/main/java/dev/superice/gdparser/frontend/ast/ASTNodeHandler.java`
  - `E:/Projects/gdparser/src/main/java/dev/superice/gdparser/frontend/ast/BreakStatement.java`
  - `E:/Projects/gdparser/src/main/java/dev/superice/gdparser/frontend/ast/ContinueStatement.java`
  - `godotengine/godot: modules/gdscript/gdscript_analyzer.cpp`
  - `godotengine/godot: modules/gdscript/gdscript_byte_codegen.h`
- 明确非目标：
  - 当前计划不直接引入 high-level IR / sea-of-nodes
  - 当前计划不在本轮同时完成完整 statement / expression lowering
  - 当前计划不在 frontend CFG graph 落稳前解除 `ConditionalExpression` 的 compile-only block
  - 当前计划不在第一轮把 property initializer / parameter default init 接入 frontend CFG materialization

---

## 1. 问题定义与当前事实

### 1.1 当前 pipeline 的稳定起点

当前默认 frontend lowering pipeline 固定为：

1. `FrontendLoweringAnalysisPass`
2. `FrontendLoweringClassSkeletonPass`
3. `FrontendLoweringFunctionPreparationPass`
4. `FrontendLoweringCfgPass`（legacy，待删除）

当前稳定输入与边界：

- lowering 入口仍然固定为 `FrontendModule`
- compile-ready 语义事实统一来自 `FrontendSemanticAnalyzer.analyzeForCompile(...)`
- function-shaped lowering 单元统一经由 `FunctionLoweringContext`
- public lowering 返回值仍然必须是 shell-only `LirModule`

这条链路当前已经稳定，后续 CFG 工程应在其上增量推进，而不是重新发明新的 lowering 入口。

### 1.2 当前 `FrontendLoweringCfgPass` 的真实状态

当前代码中的 `FrontendLoweringCfgPass` 已经存在，但它不是最终想要的 frontend CFG。

当前 pass 的真实能力只有：

- 读取 compile-ready `EXECUTABLE_BODY` context
- 校验 target function 仍保持 shell-only
- 在 `FunctionLoweringContext` 上发布 AST identity keyed 的 `CfgNodeBlocks`
- 用 `LirBasicBlock` 充当 metadata-only block skeleton

它当前还不能正确表达：

- `if` / `elif` 的显式 condition-entry region
- truthiness / condition normalization 的前置求值区域
- `and` / `or` / `not` 的短路控制流
- `while` 中 `break` / `continue` 的语义跳转
- `ConditionalExpression` 所需的条件求值与值合流入口

因此，仓库内不应再把它称为“minimal CFG lowering 已完成”。更准确的表述是：

- 当前 pass 已落地一个 legacy metadata-only skeleton
- 该 skeleton 为后续迁移提供了一部分稳定约束
- 但它仍然不是可消费的 source-level frontend CFG

### 1.3 当前语义合同对 CFG 层提出的要求

frontend 与 LIR 当前已经冻结了两条必须同时满足的事实：

- source-level `if` / `elif` / `while` / `assert` condition 仍然采用 Godot-compatible truthy contract
- backend / LIR 的 control-flow 仍然保持 bool-only 边界

这意味着 frontend CFG 层必须能表达：

- 条件值的求值过程
- 从“任意 stable typed value”到“最终 bool-only branch”的过渡
- 短路导致的多段前置条件区域

如果 CFG 层仍然只会发布“某个 AST 节点对应几个 block”，这个合同就无法闭合。

### 1.4 Godot 对 `and` / `or` 的真实语义

对照 Godot 当前 GDScript 实现，可以确认：

- `and` / `or` 不只是 condition context 下短路
- 它们对任意操作数类型都成立
- 它们始终返回 `bool`
- 即使在非 condition 的 value context 中，也通过跳转与结果写入来实现短路

当前调研依据包括：

- `gdscript_analyzer.cpp`
  - `OP_AND` / `OP_OR` “always return a boolean”
  - “don't use the Variant operator since they have short-circuit semantics”
- `gdscript_byte_codegen.cpp`
  - `write_and_left_operand(...)`
  - `write_and_right_operand(...)`
  - `write_end_and(target)`
  - `write_or_left_operand(...)`
  - `write_or_right_operand(...)`
  - `write_end_or(target)`

`write_end_and(target)` / `write_end_or(target)` 这组接口尤其关键，因为它们说明：

- `and` / `or` 即使作为普通表达式值被消费
- 也不是“先算左右值再做普通二元运算”
- 而是“控制流短路 + 向目标结果槽写 true/false”

因此，本项目的 frontend CFG 计划不能只在 `buildCondition(...)` 中对 `and` / `or` 特判；`buildValue(...)` 也必须把它们展开为多序列节点和分支节点。

### 1.5 compile-only gate 当前仍需保留的封口

`ConditionalExpression` 当前继续 compile-block，不是因为 parser 或 shared semantic 不支持，而是因为它的 lowering 依赖更强的 control-flow 表达层。

在 frontend CFG graph 尚未稳定前，不得提前解除：

- `ConditionalExpression`
- 任何依赖 condition-evaluation-region 的 future feature

---

## 2. 目标模型

### 2.1 总体方向

推荐新增一套与 legacy `FrontendLoweringCfgPass` 解耦的 frontend-only CFG graph。

这层 graph 的职责固定为：

- 只在 frontend/lowering 内部存在
- 代码组织上放在独立的 `dev.superice.gdcc.frontend.lowering.cfg` 包下
- 先表达 source-level control-flow 与 condition-evaluation-region
- 在真正写 LIR 前，把 source 语义整理成一个更稳健的中间层

这层 graph 不直接替代 future HIR，也不直接取代 LIR。它的定位是：

- 比当前 `CfgNodeBlocks` 更强，能表达 source 控制流
- 比 future sea-of-nodes 更小，先服务于 frontend -> LIR lowering

### 2.2 推荐 node 形状

第一轮保持 3 个 node kind 即可：

- `SequenceNode`
- `BranchNode`
- `StopNode`

推荐最小形状：

- `SequenceNode`
  - `id`
  - `items`
  - `nextId`
- `BranchNode`
  - `id`
  - `conditionRoot`
  - `conditionValueId`
  - `trueTargetId`
  - `falseTargetId`
- `StopNode`
  - `id`
  - `returnValueIdOrNull`

其中 `BranchNode.conditionValueId` 当前不要求已经是 `bool`。frontend CFG -> LIR lowering 阶段再完成 truthiness / condition normalization。

### 2.3 `SequenceNode` 必须承载线性求值步骤

`SequenceNode` 不能只保存 statement AST list。若仍然只保存 statement，短路与条件前置区域就无法表达。

推荐把 `items` 定义成最小线性求值单元列表，例如：

- `StatementItem(statement)`
- `EvalExprItem(expression, resultValueId)`

第一轮不需要为了可扩展性提前制造复杂抽象，但必须保留一个事实：

- `SequenceNode.items` 表达的是“线性执行内容”
- 它不等价于“若干 source statement”

### 2.4 AST-keyed region side table

保留 AST identity keyed side table 的方向，但 side table 的 value 需要从 `CfgNodeBlocks` 迁移为 frontend CFG region。

推荐的最小 region 形状：

- `BlockRegion(entryId)`
- `IfRegion(conditionEntryId, thenEntryId, elseOrNextClauseEntryId, mergeId)`
- `ElifRegion(conditionEntryId, bodyEntryId, nextClauseOrMergeId)`
- `WhileRegion(conditionEntryId, bodyEntryId, exitId)`

这一步有两个直接收益：

- `if` / `elif` 首次拥有显式 `conditionEntryId`
- `continue` 可以稳定回跳到 `WhileRegion.conditionEntryId`

### 2.5 包与 pass 的职责边界

新 frontend CFG 必须与旧 `FrontendLoweringCfgPass` 解耦。

推荐固定边界：

- `dev.superice.gdcc.frontend.lowering.cfg`
  - 保存 frontend CFG graph model
  - 保存 region model
  - 保存 builder / helper / naming / loop-frame 等实现
- `dev.superice.gdcc.frontend.lowering.pass.FrontendLoweringBuildCfgPass`
  - 只负责读取 `FunctionLoweringContext`
  - 调用 `frontend.lowering.cfg` 下的 builder 构图
  - 把 graph/region 发布回 lowering context
- 现有 `FrontendLoweringCfgPass`
  - 立即标注废弃
  - 迁移完成后删除

这样可以避免新 CFG 的抽象、测试和后续 body lowering 输入继续受制于 legacy block-bundle 设计。

### 2.6 构图 API 必须区分 value context 与 condition context

frontend CFG builder 需要显式拆成两类构图入口：

- `buildValue(expr, currentSequence)`
- `buildCondition(expr, trueTargetId, falseTargetId)`

这是升级的核心。没有这两个分工，就无法正确处理：

- `and` / `or` / `not`
- 非 `bool` condition
- 条件表达式 future lowering

但这里需要额外固定一条约束：

- `and` / `or` 不能只在 `buildCondition(...)` 中展开
- `buildValue(...)` 遇到 `and` / `or` 时，也必须走控制流展开
- 也就是说，`and` / `or` 永远不是普通的线性 `EvalExprItem`

推荐做法：

- `buildCondition(...)`
  - 直接展开短路控制流
- `buildValue(and/or, currentSequence)`
  - 分配结果 value id
  - 构建短路分支区域
  - 在 success/fail 路径的后继 sequence 中写入 `true` / `false`
  - 最后汇合到统一 continuation

这意味着无论 `and` / `or` 出现在：

- `if a and b`
- `while a or b`
- `var x = a and b`
- `return a or b`
- `call(a and b)`

它们都应当生成多个 `SequenceNode` 与 `BranchNode`，而不是退化成单个线性求值节点。

### 2.7 loop stack

frontend CFG builder 需要显式维护 loop stack。

推荐最小 frame：

- `continueTargetId`
- `breakTargetId`

语义固定为：

- `continue` 跳回当前 loop 的 `conditionEntryId`
- `break` 跳到当前 loop 的 `exitId`

---

## 3. 这套模型如何解决当前已知问题

### 3.1 非 `bool` 条件

当前 `BranchNode` 不要求条件值已经是 bool，因此可以先保留 source contract：

- 先在 `SequenceNode` 中求出条件值
- 再在 `BranchNode` 中保留分支点
- 等 frontend CFG -> LIR lowering 时补 bool normalization

这样不会反向把 frontend 收紧成 strict-bool dialect。

### 3.2 `and` / `or` 短路

基于 Godot 当前实现，`and` / `or` 的短路语义对 value context 与 condition context 一视同仁。

因此：

- `buildCondition(...)` 可以直接把：

- `a and b`
- `a or b`
- `not a`

展开成一段条件求值区域
- `buildValue(...)` 遇到 `and` / `or` 时，也必须复用控制流展开，再把结果写回统一的 value id

而不是先 eager lower 两边值再做普通二元运算。

这意味着：

- 即使最外层条件静态类型已经是 `bool`，仍然允许存在多段前置节点
- 即使表达式处于普通 value context，也仍然必须生成多个序列节点和分支节点

### 3.3 `while` 的 `break` / `continue`

当前 block-bundle metadata 无法可靠表达 loop-control edge。引入 `WhileRegion` 与 loop stack 后：

- `continue` 总是回到条件入口
- `break` 总是离开当前 loop
- nested loop 的目标也能稳定区分

### 3.4 `ConditionalExpression`

`ConditionalExpression` 仍然不应在本轮提前放行，但 frontend CFG graph 已经为其 future lowering 提供必要前提：

- 条件求值区域
- 分支后的值合流入口

后续只需再明确 value-merge / phi-like contract，才能安全解封。

---

## 4. 分步骤实施计划

### 4.1 第一步：冻结 legacy CFG pass 的过渡定位

实施内容：

- 在代码注释与实施文档中明确标注：
  - 当前 `FrontendLoweringCfgPass` 是 legacy metadata-only skeleton
  - 当前 `FunctionLoweringContext.cfgNodeBlocks` 是迁移期 side table
  - 现有实现尚未完成 frontend CFG 工程
- 在代码中把 `FrontendLoweringCfgPass` 明确标注为 deprecated / for-removal
- `frontend_lowering_cfg_pass_plan.md` 改为归档/迁移说明，不再作为当前实施主计划
- `frontend_lowering_plan.md` 改为把第二阶段定义为 frontend CFG graph 迁移，而不是“最小 CFG 已基本完成”

验收细则：

- 仓库内不存在把当前 `FrontendLoweringCfgPass` 描述成“最终 CFG lowering”的文档冲突
- 新旧文档的职责边界清晰：
  - 新文档是当前主计划
  - 旧文档仅保留 legacy 迁移背景

### 4.2 第二步：在 `FunctionLoweringContext` 中引入 frontend CFG graph carrier

实施内容：

- 在 `dev.superice.gdcc.frontend.lowering.cfg` 包中新增 frontend CFG graph model、region model 与 builder 支撑类型
- 在 `FunctionLoweringContext` 中新增 frontend CFG graph carrier
- 同时新增 AST-keyed frontend CFG region side table
- `CfgNodeBlocks` 先保留为迁移期兼容物；新代码不得再把它当最终模型继续扩张

验收细则：

- happy path：
  - 每个 compile-ready executable context 都可以持有独立 frontend CFG graph
  - `if` / `elif` / `while` 都能按 AST identity 读回对应 region
- negative path：
  - duplicate publish 继续 fail-fast
  - 不属于该函数的 AST 节点不得误命中
  - graph 与 region side table 不得共享到别的 function context

### 4.3 第三步：先迁移直线型 executable body

实施内容：

- 新增 `FrontendLoweringBuildCfgPass`
- 让 `FrontendLoweringBuildCfgPass` 构建 frontend CFG graph，而不是继续扩展 `FrontendLoweringCfgPass`
- 第一批先覆盖：
  - 空 `Block`
  - `PassStatement`
  - `ExpressionStatement`
  - compile-ready local `var`
  - `ReturnStatement`
- `return` 终结 lexical path，后续 sibling statement 不再继续挂接
- public lowering 仍然保持 shell-only `LirModule`

验收细则：

- happy path：
  - 空函数与直线型函数得到稳定 graph entry
  - `return` 后的 source remainder 不再继续发布 region
- negative path：
  - 未支持 statement kind 继续 fail-fast
  - 不得偷偷向 `LirFunctionDef` 写 block

### 4.4 第四步：迁移 `if / elif / else / while` 的结构骨架

实施内容：

- 为 `if` / `elif` / `while` 发布带 `conditionEntryId` 的 region
- graph node id 继续采用 deterministic lexical-counter 策略
- `IfRegion.mergeId` 与 `WhileRegion.exitId` 必须稳定可回归

验收细则：

- happy path：
  - `if`
  - `if / else`
  - `if / elif / else`
  - `while`
  - nested `if` inside `while`
  - nested `while` inside `if`
    都能得到稳定 graph shape 与 region 映射
- negative path：
  - empty branch body 不得漏建 merge / exit contract
  - fully-terminating branch chain 后不得继续发布错误的 lexical remainder

### 4.5 第五步：引入 `buildCondition(...)` 与 condition-evaluation-region

实施内容：

- 在 builder 中正式拆分：
  - `buildValue(...)`
  - `buildCondition(...)`
- `buildCondition(...)` 对普通条件表达式先生成线性求值步骤，再连接 `BranchNode`
- `BranchNode` 保留 source condition value，不提前强制 bool 化
- `buildValue(...)` 遇到 `and` / `or` 时，不得生成单个线性 `EvalExprItem`；必须改走短路控制流展开，并在汇合点写回统一结果值

验收细则：

- happy path：
  - 非 `bool` condition 也能构图成功
  - `if payload:` 与 `while payload:` 的 graph 中都存在显式条件求值区域
  - value context 下的 `and` / `or` 也会生成多个 `SequenceNode` 与 `BranchNode`
- negative path：
  - 不得因为 LIR bool-only 边界而反向把 frontend 规则改成 strict-bool
  - 不得在本步骤就提前解除 `ConditionalExpression` compile gate

### 4.6 第六步：实现短路与 loop-control

实施内容：

- `buildCondition(...)` 特判：
  - `and`
  - `or`
  - `not`
- `buildValue(...)` 对 `and` / `or` 复用同一套短路控制流展开，而不是回退到普通二元表达式求值
- 引入 loop stack，正式消费：
  - `BreakStatement`
  - `ContinueStatement`
- 在此之前，若 compile-ready surface 仍可能泄露 `break` / `continue`，必须继续视为前置协议破坏并 fail-fast

验收细则：

- happy path：
  - `a and b` 在 false path 上不进入 `b` 的求值区域
  - `a or b` 在 true path 上不进入 `b` 的求值区域
  - `var x = a and b` 与 `return a or b` 也会走短路控制流，而不是 eager 求值
  - `continue` 稳定回到当前 loop 的 `conditionEntryId`
  - `break` 稳定跳到当前 loop 的 `exitId`
  - nested loop 中内外层 `break` / `continue` 不串线
- negative path：
  - 没有 loop frame 时遇到 `break` / `continue` 必须 fail-fast
  - 不能把 `continue` 错跳到 body entry

### 4.7 第七步：移除 legacy block-bundle 依赖

实施内容：

- 当所有 frontend CFG 读者都已改为 graph/region 后，逐步移除：
  - `CfgNodeBlocks`
  - 以 `LirBasicBlock` 作为 metadata skeleton 的 legacy side table
- 从默认 pipeline 中移除并删除 `FrontendLoweringCfgPass`
- 让 `FrontendLoweringBuildCfgPass` 成为唯一的 frontend CFG 构图入口
- 若迁移期确实需要兼容层，也只能是短期桥接，不得继续扩展其职责

验收细则：

- happy path：
  - `FrontendLoweringBuildCfgPassTest` 与 `FrontendLoweringPassManagerTest` 改为锚定 frontend CFG graph
- negative path：
  - 不允许 graph 与 legacy side table 长期双写并各自漂移
  - 仓库内不再保留 `FrontendLoweringCfgPass`

---

## 5. 推荐测试矩阵

建议至少覆盖：

- `FrontendLoweringBuildCfgPassTest`
  - 空函数
  - `pass`
  - `return`
  - `if`
  - `if / else`
  - `if / elif / else`
  - `while`
  - nested `if` / `while`
  - 非 `bool` condition 的 condition-evaluation-region
  - condition context 下 `and` / `or` / `not` 的短路区域
  - value context 下 `and` / `or` 的短路区域
  - `break` / `continue`
  - fully-terminating branch 后的 lexical remainder
  - compile-blocked module 不进入 cfg pass
- `FrontendLoweringPassManagerTest`
  - 默认 pipeline 发布 frontend CFG graph
  - `LirModule` 继续保持 shell-only
  - property initializer context 继续不进入 frontend CFG materialization
- `FrontendCompileCheckAnalyzerTest`
  - `ConditionalExpression` 继续 compile-block
  - compile gate 说明与 frontend CFG graph 计划保持一致

测试重点应优先覆盖：

- AST identity keyed region lookup
- deterministic node id / graph shape
- condition context 与 value context 的分工
- short-circuit 只展开必要路径
- `and` / `or` 在 value context 下也不会 eager 求值
- loop stack target 的正确性
- shell-only LIR 合同未被破坏

---

## 6. 主要风险与缓解

### 6.1 迁移期双模型漂移

风险：

- legacy `CfgNodeBlocks` 与新 graph 并存时，容易出现两套结构不同步

缓解：

- 明确 graph/region 是新主模型
- legacy block-bundle 只允许短期兼容，不得继续加需求

### 6.2 用 ASTWalker 直接实现控制流构图

风险：

- control-flow builder 需要显式携带：
  - lexical continuation
  - true/false target
  - loop stack
- 通用 ASTWalker 更适合遍历，不适合承担这套有向构图协议

缓解：

- 保持显式状态机构图器
- 允许局部复用 AST 访问帮助函数，但不要把核心构图逻辑改写成 generic walker callback

### 6.3 过早解除 `ConditionalExpression`

风险：

- 若只看 graph 有了分支节点就解封，很容易遗漏 value merge / ownership / evaluation-order 语义

缓解：

- 继续维持 compile gate
- 直到 condition region 与 value merge 合同一起冻结后再解封

### 6.4 property initializer / parameter default 过早接入

风险：

- 它们当前仍不是完整 executable body
- 若强行复用 executable graph builder，容易伪造错误的 return / ordering 语义

缓解：

- 第一轮只处理 `EXECUTABLE_BODY`
- 等 expression/body lowering 与 synthetic init function contract 一起闭合后再复用

---

## 7. 文档同步要求

只要 frontend CFG graph 的合同发生变化，至少要同步更新：

- `frontend_rules.md`
- `frontend_compile_check_analyzer_implementation.md`
- `diagnostic_manager.md`
- `frontend_lowering_plan.md`
- `frontend_lowering_func_pre_pass_implementation.md`
- 本文档

其中有一条必须长期保持一致：

- 新 frontend CFG 实现在 `frontend.lowering.cfg` 包中独立维护
- `FrontendLoweringBuildCfgPass` 是唯一的构图 pass
- 当前 `FrontendLoweringCfgPass` 是已废弃、待删除的过渡实现
