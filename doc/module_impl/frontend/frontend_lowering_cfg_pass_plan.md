# Frontend Lowering CFG Pass 迁移说明

> 本文档记录第一版 `FrontendLoweringCfgPass` 的 legacy metadata-only 设计。它不再是当前 frontend CFG 工程的主计划文档。当前实施应以 `frontend_lowering_cfg_graph_plan.md` 为准，并由独立的 `frontend.lowering.cfg` 包与 `FrontendLoweringBuildCfgPass` 接手。

## 文档状态

- 状态：归档维护中（legacy metadata-only CFG pass 已落地，但已被标注废弃并明确定义为未完成过渡层）
- 更新时间：2026-03-29
- 当前主计划：
  - `frontend_lowering_cfg_graph_plan.md`
- 仍然保留的关联文档：
  - `frontend_lowering_plan.md`
  - `frontend_lowering_func_pre_pass_implementation.md`
  - `frontend_rules.md`
  - `frontend_compile_check_analyzer_implementation.md`
  - `doc/gdcc_low_ir.md`

---

## 1. 当前代码中的 legacy 事实

当前仓库里确实已经存在一个名为 `FrontendLoweringCfgPass` 的 pass，但它只完成了第一版 metadata-only skeleton。

当前稳定事实：

- pass 已接入默认 `FrontendLoweringPassManager`
- pass 只消费 compile-ready `EXECUTABLE_BODY`
- pass 继续要求 target function 保持 shell-only
- pass 只向 `FunctionLoweringContext` 发布 AST identity keyed `CfgNodeBlocks`
- pass 不向 `LirFunctionDef` 写 basic block
- pass 不设置 `entryBlockId`
- public lowering 返回值继续保持 shell-only `LirModule`
- pass 当前应视为 `@Deprecated(forRemoval = true)` 的 legacy 实现

这些事实仍然有效，但它们不应再被表述成“frontend CFG 已完成”。

---

## 2. 这版设计为什么不足

legacy `CfgNodeBlocks` 方案当前不能正确表达：

- `if` / `elif` 的 condition-entry region
- source truthy condition 到 bool-only branch 的前置求值区域
- `and` / `or` / `not` 的短路控制流
- `while` 的 `break` / `continue`
- `ConditionalExpression` future lowering 所需的控制流和值合流入口

因此，当前 pass 只能视为：

- 前期调研结论的一次着陆
- 对 AST identity keyed bookkeeping、deterministic naming、shell-only LIR contract 的一次冻结
- 后续 frontend CFG graph migration 的起点

---

## 3. 仍然有效、可复用的部分

第一版实现里仍有几条值得保留的稳定约束：

- frontend CFG 工程必须继续复用 `FunctionLoweringContext`
- AST-keyed side table 方向是正确的
- deterministic lexical-counter 命名策略是正确的
- public lowering 在 frontend CFG -> LIR lowering 闭环前仍然必须保持 shell-only `LirModule`
- compile gate 继续负责拦截 blocked subtree，而不是让 cfg pass 重复扫描

后续迁移不应推翻这些约束，只应替换“metadata-only block bundle”这层表达模型。

---

## 4. 后续迁移要求

从现在起，任何与 frontend CFG 相关的新工作都应遵守：

- 不再继续扩展 `CfgNodeBlocks` 去承载更多 source control-flow 语义
- 新 frontend CFG 必须在 `dev.superice.gdcc.frontend.lowering.cfg` 包中独立实现
- `FrontendLoweringBuildCfgPass` 必须成为唯一的 frontend CFG graph builder
- `FrontendLoweringCfgPass` 只保留迁移期兼容职责，迁移完成后删除
- 当前主计划与验收标准以 `frontend_lowering_cfg_graph_plan.md` 为准

只有完成 frontend CFG graph migration 后，才允许重新评估：

- `ConditionalExpression` 的 compile gate 解封
- `break` / `continue` 的 compile-ready surface
- frontend CFG -> LIR lowering 的接线策略
