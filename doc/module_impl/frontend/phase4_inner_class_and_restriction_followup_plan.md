# Phase 4 归档：Inner Class 与 ResolveRestriction

> 本文档已整理归档。Phase 4 形成的长期约定、架构边界与 Godot 对齐结论已经归并到 `doc/module_impl/frontend/scope_architecture_refactor_plan.md`；本文仅保留 Phase 4 特有的摘要，避免后续查阅时丢失上下文。

## 文档状态

- 状态：已归档
- 更新时间：2026-03-08
- 主事实源：
  - `doc/module_impl/frontend/scope_architecture_refactor_plan.md`
- 关联文档：
  - `doc/module_impl/frontend/frontend_implementation_plan.md`
  - `doc/analysis/frontend_semantic_analyzer_research_report.md`
- 归档原则：
  - 删除 Step A-J、命令记录、验收流水、阶段进度等过程性内容
  - 只保留对后续工程仍有约束力的协议、差异与实现边界

---

## 1. Phase 4 固化结论

- `Scope` 已从“仅 lexical skeleton”升级为 restriction-aware lexical binding protocol。
- 未限定类成员解析已采用 tri-state lookup：
  - `FOUND_ALLOWED`
  - `FOUND_BLOCKED`
  - `NOT_FOUND`
- 被 restriction 阻止的当前层命中仍然构成 shadowing；blocked hit 不能退化成 miss。
- `ResolveRestriction` 当前只约束未限定 class-member 的 value/function lookup：
  - `staticContext()` 允许 class const / static property / static method
  - `instanceContext()` 允许 class const / instance property / instance method / static property / static method
- `type-meta` 当前采用“统一签名 + always-allowed”契约：
  - `resolveTypeMeta(..., restriction)` 仅为统一协议形状
  - 对现有 `ScopeTypeMetaKind` 集合只允许返回 `FOUND_ALLOWED` / `NOT_FOUND`
  - `TYPE_META` 的消费合法性继续由 binder/static access / constructor / `load_static` 处理
- inner class 当前规则已经冻结：
  - `type-meta` 继续沿 lexical parent 链查找
  - `value/function` 在 `ClassScope` lexical miss 时跳过连续 outer `ClassScope`
  - inner class 因此可以继承 outer lexical type-meta，但不会无 base 继承 outer value/function
- 当前 Godot 对齐范围仍限于 class const / property / method；`self` / signal 仍待后续 binder 补齐。

---

## 2. Godot 对齐事实

### 2.1 Blocked hit 继续构成 shadowing

Godot 当前实现的关键结论是：

- 标识符解析先 local/member，再 global
- `static_context` 检查发生在命中之后
- 当前层/local/member 一旦命中，即使随后因 static context 非法，也不会继续回退 outer/global

对应约束：

- `restriction` 不能建模为“当前层非法就当作 miss”
- lookup 协议必须保留 `FOUND_BLOCKED`

参考：

- `https://github.com/godotengine/godot/blob/220b0b2f74d8e089481b140c42a42992a76dd6fc/modules/gdscript/gdscript_analyzer.cpp#L4363-L4524`
- `https://github.com/godotengine/godot/blob/220b0b2f74d8e089481b140c42a42992a76dd6fc/modules/gdscript/gdscript_analyzer.cpp#L4458-L4484`
- `https://github.com/godotengine/godot/blob/220b0b2f74d8e089481b140c42a42992a76dd6fc/modules/gdscript/gdscript_analyzer.cpp#L4523-L4549`
- `https://github.com/godotengine/godot/blob/220b0b2f74d8e089481b140c42a42992a76dd6fc/modules/gdscript/gdscript_analyzer.cpp#L4160-L4251`

### 2.2 Inner class 当前方案与 Godot 仍有已知差异

Godot 当前会把 outer class 纳入 current-scope class chain，并放在 base type 之后。

GDCC 当前没有照搬这条语义，而是采用更保守的工程化方案：

- outer lexical `type-meta` 继续可见
- outer lexical value/function 在 inner class 中被隔离

因此必须长期保留以下结论：

- 这是有意识的设计分歧，不是实现遗漏
- 若未来要进一步靠拢 Godot，需要重新评估 namespace-specific parent / scoped view，而不是继续在现有 `ClassScope` 上叠补丁

参考：

- `https://github.com/godotengine/godot/blob/220b0b2f74d8e089481b140c42a42992a76dd6fc/modules/gdscript/gdscript_analyzer.cpp#L320-L344`

---

## 3. 对后续工程的直接约束

- frontend binder 遇到 `FOUND_BLOCKED` 时必须停止 lookup，并直接生成“当前上下文非法访问该绑定”的语义结论。
- `TYPE_META` 的合法性不能回塞到 lookup 阶段；后续 static access / constructor / `load_static` 仍要单独分流。
- 如果某个实现想提升 Godot parity，必须先回到主事实源文档重新核对 inner class 差异、shadowing 语义和 shared resolver 边界。
- `self` / signal 的 binding 与 diagnostics 仍是 Phase 7+ 的独立工作项，不能假设已经被 Phase 4 自动覆盖。
- 之后若再出现 `Scope` 只是“找名字、不管合法性”的说法，应视为过时表述并主动清理。
