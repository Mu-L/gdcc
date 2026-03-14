# ScopeTypeResolver 迁移实施计划

> 本文档细化“提取 `resolveStrictDeclaredType` 相关逻辑，新增 `ScopeTypeResolver`，并把 frontend 的 declared type 解析逐步迁移到基于 `Scope` 的共享解析路径”这一轮改造的执行清单。本文档是本轮工作单，不替代 `scope_architecture_refactor_plan.md`、`scope_analyzer_implementation_plan.md` 与 `inner_class_registry_canonical_name_plan.md` 的长期事实源地位。

## 文档状态

- 状态：Phase 0 / Phase 1 / Phase 2 / Phase 3 / Phase 4 / Phase 5 已完成
- 更新时间：2026-03-14
- 适用范围：
  - `src/main/java/dev/superice/gdcc/frontend/sema/**`
  - `src/main/java/dev/superice/gdcc/frontend/scope/**`
  - `src/main/java/dev/superice/gdcc/scope/**`
  - `src/main/java/dev/superice/gdcc/scope/resolver/**`
  - `src/test/java/dev/superice/gdcc/frontend/**`
  - `src/test/java/dev/superice/gdcc/scope/**`
- 明确非目标：
  - 本轮不实现完整 frontend binder/body phase
  - 本轮不改变 `FrontendSemanticAnalyzer` 的 `skeleton -> scope analyzer` 阶段顺序
  - 本轮不把 diagnostics manager 注入 shared resolver
  - 本轮不放宽当前 strict declared type 的容器规则
  - 本轮不让 `ClassRegistry#resolveTypeMetaHere(...)` 反向依赖 `ScopeTypeResolver`

---

## 1. 背景与问题界定

当前仓库里，strict declared type 解析能力分散在三处：

1. `FrontendClassSkeletonBuilder`
   - 现在已经改为通过 shared `ScopeTypeResolver` 的 strict 无 mapper 路径解析 declared type
   - skeleton 的 unknown type 继续由 diagnostics + `Variant` fallback 处理，而不是 guessed object fallback
2. `ClassRegistry`
   - 已经是 `Scope` global root
   - 提供严格 `resolveTypeMeta(...)` 与 `tryResolveDeclaredType(...)`
   - `findType(...)` 仍保留兼容 guessed-object 行为，但 strict 前半段已收口到 shared resolver
   - 仅覆盖全局 strict namespace，不处理 lexical inner class `sourceName`
3. `Scope` / `AbstractFrontendScope`
   - 已具备独立的 type-meta namespace 与 parent-chain 协议
   - 但 production scope graph 仍未真正发布 inner class 的 lexical type-meta

这会产生三个结构性问题：

- skeleton build 与未来 binder 无法共享同一套 type resolution 入口
- strict container 解析规则存在重复实现与漂移风险
- inner class / future local type-meta 虽然已经有 `Scope` 协议承载位，但 declared type 解析仍被迫绕过 `Scope`

本轮目标是把 declared type 解析收口为：

- 一个 shared `ScopeTypeResolver`
- 一个 frontend 侧最小 type-scope 构建路径，供 skeleton 过渡使用
- 一个真实 scope graph 上的 immediate-inner type-meta 发布路径，供后续 binder 复用

---

## 2. 调研结论

### 2.1 当前代码基线

- `FrontendClassSkeletonBuilder#resolveTypeOrVariant(...)`
  - 已通过 shared `ScopeTypeResolver` 的 strict 无 mapper overload 处理 type hint
  - 已经不再依赖 `findType(...)`
- `ClassRegistry#resolveTypeMetaHere(...)`
  - 既能处理 builtin / engine / gdcc / global enum
  - 也能处理严格容器文本，如 `Array[T]`、`Dictionary[K, V]`
  - 它仍是 global scope 的 local primitive，不应反向委托 `ScopeTypeResolver`
- `ClassRegistry#tryParseStrictTextType(...)`
  - 对 nested structured container 保持严格拒绝
  - 这是当前 strict declared type 规则的重要事实源
- `ClassRegistry#findType(...)`
  - 现在先走 shared strict `tryResolveDeclaredType(...)`
  - strict miss 后才通过 optional unresolved mapper 保留 guessed-object fallback
- `AbstractFrontendScope#defineTypeMeta(...)`
  - 已经以 `sourceName` 为 key 维护 local type-meta namespace
- `FrontendScopeAnalyzer`
  - 已能建立 `SourceFile` / `ClassDeclaration` / callable / block 的 scope graph
  - 但尚未在 production code 中发布 inner class immediate type-meta

### 2.2 `gdparser 0.5.1` 相关结论

参考 `E:/Projects/gdparser/src/main/java/dev/superice/gdparser/frontend/ast/ASTWalker.java` 与 `ASTNodeHandler.java`：

- `ASTWalker` 已提供稳定的 pre-order depth-first 遍历
- `ASTNodeHandler` 已覆盖 `SourceFile`、`ClassDeclaration`、`ConstructorDeclaration`、`LambdaExpression`、`TypeRef` 等所需节点
- `SKIP_CHILDREN` 只裁剪当前子树，适合 scope analyzer 的局部恢复策略

因此本轮不需要再额外封装 walker，直接继续复用 `gdparser` 内置 `ASTWalker`
即可。

### 2.3 Godot 对齐结论

参考 `godotengine/godot` 中的 `modules/gdscript/gdscript_analyzer.cpp` 与 `gdscript_parser.cpp`：

- 上游继续采用 parser / analyzer 分阶段推进，而不是把 declaration collection、scope graph、binding、body analysis 混成一个阶段
- `_init` 仍是 constructor 的统一成员表面
- 这与 GDCC 当前已冻结的 `skeleton -> scope -> future binder/body` 路径一致

因此本轮不应通过打乱 phase 顺序来“强行让 skeleton 直接使用真实 scope graph”，而应采用过渡性的最小 type-scope 方案。

---

## 3. 本轮冻结目标与不变量

本轮落地后，代码必须满足以下不变量：

- `ScopeTypeResolver` 以 `Scope` 为解析入口，而不是直接依赖 `FrontendSourceClassRelation`
- `ClassRegistry#tryResolveDeclaredType(...)` 复用同一套 shared strict declared type 解析逻辑
- shared resolver 可通过 optional `UnresolvedTypeMapper` 暴露 compatibility fallback，但 frontend strict 位置不得传入 mapper
- skeleton member filling 不再持有 builder 私有的 declared type parser
- skeleton 解析 inner class / outer class / same-module class 时，走最小 type-scope 链而不是 relation 私有回放逻辑
- scope analyzer 会在真实 `ClassScope` 上发布 immediate inner class 的 type-meta
- outer class 仅继续通过 type-meta parent chain 暴露 outer type，不得污染 value/function namespace
- unknown declared type 仍由调用方决定恢复策略；shared resolver 不直接产出 diagnostics
- `ClassRegistry#resolveTypeMetaHere(...)` 保持为 `Scope` primitive；shared resolver 只能建立在 `Scope#resolveTypeMeta(...)` 之上
- strict container 规则保持不变：
  - 允许 `Array[T]`
  - 允许 `Dictionary[K, V]`
  - nested structured container 继续拒绝

---

## 4. 目标设计

### 4.1 `ScopeTypeResolver`

建议新增：

- `src/main/java/dev/superice/gdcc/scope/resolver/ScopeTypeResolver.java`

职责冻结为：

- 在给定 `Scope` 内严格解析 type text
- 通过 `scope.resolveTypeMeta(...)` 处理 leaf type 名
- 自身处理顶层 `Array[...]` / `Dictionary[..., ...]`
- 在 nested slot 中继续拒绝 structured container text
- 不猜测 unknown object type
- 不创建 diagnostics

建议公开最小 API：

- `tryResolveTypeMeta(Scope scope, String typeText)`
- `tryResolveDeclaredType(Scope scope, String typeText)`
- `tryResolveDeclaredType(Scope scope, String typeText, @Nullable UnresolvedTypeMapper unresolvedTypeMapper)`

其中 optional `UnresolvedTypeMapper` 的边界冻结为：

- 仅在 bare type name 或 top-level strict container 的 leaf type 严格解析失败后触发
- 不负责恢复 malformed structured text
- 默认 strict 调用方不传该参数
- 兼容调用方可借此保留 guessed-object 或其他 fallback 策略

### 4.2 Frontend 最小 type-scope 过渡层

skeleton phase 仍运行在 scope analyzer 之前，因此不能直接依赖 production `scopesByAst`。

本轮采用过渡层：

- 以 `ClassRegistry` 为 root
- 为每个 accepted top-level / inner class 构造一条仅用于 type resolution 的 `ClassScope` 链
- 只发布 immediate inner class type-meta
- 不填充 value / function / parameter / local binding

这个最小 scope 链只服务 skeleton member filling，不替代真实 scope analyzer。

### 4.3 真实 scope graph 的 type-meta 发布

`FrontendScopeAnalyzer` 在建立 `ClassScope` 后，需要同步发布：

- `SourceFile` 顶层 `ClassScope` 的 immediate inner classes
- 每个 inner `ClassDeclaration` 对应 `ClassScope` 的 immediate inner classes

当前 class 自身无需在 local scope 再次重复注册：

- top-level class 由 registry 解析
- inner class 的 `sourceName` 通过外层 owner scope 已发布的 immediate inner type-meta 可被 child class 解析

---

## 5. 分阶段实施清单

## Phase 0. 调研与方案收敛

目标：

- 确认 shared resolver 的正确抽象边界与迁移前提。

当前进度：

- [x] 已核对 `FrontendClassSkeletonBuilder` 的 private declared type 解析链与调用点
- [x] 已核对 `ClassRegistry#resolveTypeMetaHere(...)`、`tryResolveDeclaredType(...)` 与 strict container 规则
- [x] 已核对 `AbstractFrontendScope#defineTypeMeta(...)` 与 `Scope#resolveTypeMeta(...)` 协议
- [x] 已核对 `FrontendScopeAnalyzer` 当前尚未发布 production immediate-inner type-meta
- [x] 已核对 `gdparser 0.5.1` 的 `ASTWalker` / `ASTNodeHandler` 能力
- [x] 已核对 Godot analyzer 的 staged 设计方向与 `_init` constructor 表面

验收清单：

- [x] 明确本轮不改变 `skeleton -> scope analyzer` 阶段顺序
- [x] 明确 `ScopeTypeResolver` 应依赖 `Scope` 而不是 frontend relation
- [x] 明确 skeleton 需要最小 type-scope 过渡层

## Phase 1. 引入 shared `ScopeTypeResolver`

目标：

- 新增 shared strict declared type 解析入口，并让 `ClassRegistry` 复用它。

执行项：

1. 新增 `ScopeTypeResolver`
   - 提供 `tryResolveTypeMeta(...)`
   - 提供 `tryResolveDeclaredType(...)`
2. 把 builder 现有 strict parser 中的容器辅助逻辑迁移到 `ScopeTypeResolver`
3. 让 `ClassRegistry#tryResolveDeclaredType(...)` 委托 `ScopeTypeResolver`
4. 删除 `ClassRegistry` 中不再需要的重复 strict declared type 解析代码或辅助逻辑
5. 为 shared resolver 添加独立测试

本阶段当前进度：

- [x] `ScopeTypeResolver` 已落地
- [x] `ClassRegistry#tryResolveDeclaredType(...)` 已改为委托 shared resolver
- [x] strict container 规则已保持不变
- [x] 已新增 shared resolver 单元测试

验收清单：

- [x] `ClassRegistry.tryResolveDeclaredType("Array[InventoryItem]")` 行为不回退
- [x] `ClassRegistry.tryResolveDeclaredType("Array[Array[int]]")` 仍返回 `null`
- [x] `ScopeTypeResolver` 能在 class-local scope 中解析直接 inner class `sourceName`
- [x] shared resolver 不直接创建 diagnostics

## Phase 2. 迁移 skeleton member filling 到最小 type-scope

目标：

- 移除 `FrontendClassSkeletonBuilder` 私有 declared type parser，改用 shared resolver + 最小 type-scope 链。

执行项：

1. 在 frontend 中新增 immediate-inner type-meta 发布 helper
2. 为 skeleton phase 构建每个 source relation 的最小 type-scope 链
3. 让 `resolveTypeOrVariant(...)` 改为接收 `Scope`
4. 删除 builder 私有的：
   - `resolveStrictDeclaredType(...)`
   - `resolveStrictNestedDeclaredType(...)`
   - `resolveStrictDeclaredLeafType(...)`
   - `resolveLexicalGdccDeclaredType(...)`
   - `findEnclosingOwnedRelation(...)`
   - `looksStructuredTypeText(...)`
   - `splitDictionaryTypeArgs(...)`
   - `DeclaredTypeResolutionScope`
5. 更新代码注释，明确 skeleton 已经通过最小 scope 链走 shared resolver
6. 为 skeleton declared type 行为补充正反测试

本阶段当前进度：

- [x] immediate-inner type-meta 发布 helper 已落地
- [x] skeleton 最小 type-scope 链已落地
- [x] `FrontendClassSkeletonBuilder` 已切到 `ScopeTypeResolver`
- [x] builder 私有 declared type parser 已删除
- [x] 已补充 skeleton 正反测试

验收清单：

- [x] top-level 可解析 same-module top-level / direct inner class
- [x] inner class 可解析 self / outer / sibling-inner / deep inner 合法组合
- [x] 非法 lexical 上下文下 inner sourceName 仍失败并回退 `Variant`
- [x] unknown declared type 仍发 `sema.type_resolution` warning 并回退 `Variant`

## Phase 3. 在真实 `FrontendScopeAnalyzer` 上发布 immediate-inner type-meta

目标：

- 让真实 scope graph 具备与 skeleton 最小 type-scope 一致的 lexical type namespace 事实。

执行项：

1. 在 `SourceFile -> ClassScope` 创建后发布直接 inner classes
2. 在 `ClassDeclaration -> ClassScope` 创建后发布直接 inner classes
3. 保持 `ClassScope` 现有 value/function 规则不变
4. 为 analyzer 和 scope tests 添加 sourceName -> canonicalName 绑定测试

本阶段当前进度：

- [x] top-level `ClassScope` 发布 immediate inner type-meta
- [x] inner `ClassScope` 发布 immediate inner type-meta
- [x] analyzer/scope tests 已补齐

验收清单：

- [x] outer class scope 可通过 `Inner` 命中 `canonicalName = "Outer$Inner"`
- [x] inner class scope 能通过 parent chain 看到 outer type
- [x] inner class 不能看到 outer value/function
- [x] 当前 scope 只暴露 immediate inner classes，不平铺所有后代 inner classes

## Phase 4. 文档收敛、回归验证与测试矩阵补齐

目标：

- 让实现、测试、注释、文档对齐，避免 `ScopeTypeResolver` 再次形成第二套漂移事实源。

执行项：

1. 审阅并同步以下文档：
   - `scope_architecture_refactor_plan.md`
   - `scope_analyzer_implementation_plan.md`
   - `inner_class_registry_canonical_name_plan.md`
2. 审阅相关代码注释，删除旧的 relation-private resolver 表述
3. 补齐 shared resolver / skeleton / analyzer 的负向测试
4. 运行 targeted tests

本阶段当前进度：

- [x] 文档已同步
- [x] 代码注释已同步
- [x] 负向测试已补齐
- [x] targeted tests 已运行

验收清单：

- [x] 文档、实现、测试不再同时存在“builder 私有 strict resolver 是唯一事实源”的表述
- [x] 至少一组测试锚定 `ScopeTypeResolver` 与 skeleton/analyzer 的行为一致性
- [x] 所有本轮新增/修改测试均通过

## Phase 5. compatibility wrapper 收口与 unresolved mapper 扩展

目标：

- 让 `findType(...)` 复用 shared strict resolver，同时通过 optional unresolved mapper 保留兼容路径。

执行项：

1. 为 `ScopeTypeResolver#tryResolveDeclaredType(...)` 增加 optional `UnresolvedTypeMapper`
2. 明确 mapper 只处理 unresolved leaf type，不处理 malformed structured text
3. 为 `ClassRegistry#tryResolveDeclaredType(...)` 增加对应 overload
4. 让 `ClassRegistry#findType(...)` 改为：
   - 先走 shared strict resolver
   - 再通过 compatibility unresolved mapper 保留 guessed-object fallback
5. 抽共享 type-text helper，减少 `ClassRegistry` 与 `ScopeTypeResolver` 的结构化文本规则漂移
6. 更新文档与注释，明确 frontend strict 调用点继续使用 no-mapper overload

本阶段当前进度：

- [x] `UnresolvedTypeMapper` 已落地
- [x] `ScopeTypeResolver#tryResolveDeclaredType(...)` 已支持 optional mapper
- [x] `ClassRegistry#tryResolveDeclaredType(...)` 已增加 mapper overload
- [x] `ClassRegistry#findType(...)` 已收口为 strict-first + compatibility-mapper fallback
- [x] 共享 type-text helper 已落地
- [x] 文档、注释、测试已同步

验收清单：

- [x] strict frontend declared type 位置继续不传 mapper
- [x] bare unknown type 可由 mapper 恢复
- [x] `Array[MissingType]` / `Dictionary[String, MissingType]` 可由 mapper 恢复 leaf type
- [x] `Dictionary[String]`、`Array[Array[int]]` 等 malformed/unsupported structured text 不触发 mapper
- [x] `findType(...)` 继续对 global enum / utility function / singleton 返回 `null`

---

## 6. 建议测试矩阵

本轮至少覆盖以下测试，不允许只验证 happy path：

- shared resolver 正向：
  - class-local scope 解析 direct inner class
  - deep lexical scope 解析 outer / immediate inner
  - strict container 中解析 lexical inner class
- shared resolver 反向：
  - unknown type 返回 `null`
  - `Array[Array[int]]` 返回 `null`
  - `Dictionary[String]` 返回 `null`
  - strict no-mapper overload 不触发 guessed-object fallback
- shared resolver + mapper：
  - bare unknown type 可由 mapper 恢复
  - `Array[MissingType]` / `Dictionary[String, MissingType]` 的 leaf type 可由 mapper 恢复
  - malformed structured text 不触发 mapper
- skeleton 正向：
  - same-module top-level declared type
  - top-level -> direct inner
  - inner -> self / outer / sibling-inner
  - `Array[Inner]`、`Dictionary[String, Inner]`
- skeleton 反向：
  - 非法 lexical 上下文下 inner sourceName
  - unknown type 诊断 + `Variant`
  - malformed strict container 诊断 + `Variant`
- analyzer / scope 正向：
  - top-level scope 发布 immediate inner
  - inner scope 通过 parent chain 可见 outer type
  - callable / block scope 继承 class type namespace
- analyzer / scope 反向：
  - inner scope 不获得 outer value/function
  - 不平铺非 immediate inner class
- `ClassRegistry` compatibility：
  - `findType(...)` 与 strict resolver 对已知 builtin/engine/gdcc/container 结果保持一致
  - `findType(...)` 继续保留 unknown bare/container leaf guessed-object fallback
  - `findType(...)` 不对 singleton/global enum/utility function 产出类型

---

## 7. 主要风险与应对措施

风险 1：`ScopeTypeResolver` 与 `ClassRegistry` strict parser 再次形成两套规则

- 应对：
  - `ClassRegistry#tryResolveDeclaredType(...)` 直接委托 shared resolver
  - 删除冗余 declared type 解析代码

风险 2：为了共享 resolver 而打乱当前阶段顺序

- 应对：
  - 不改变 `FrontendSemanticAnalyzer` 的阶段顺序
  - 通过最小 type-scope 过渡层供 skeleton 使用

风险 3：最小 type-scope 与真实 analyzer scope 发布规则再次漂移

- 应对：
  - immediate-inner type-meta 发布 helper 复用同一份实现
  - 添加 parity tests 锚定 skeleton / analyzer 一致性

风险 4：在 class scope 中错误暴露 outer value/function

- 应对：
  - 只通过 `defineTypeMeta(...)` 发布 inner classes
  - 不修改 `ClassScope#resolveValue(...)` / `resolveFunctions(...)` 的当前策略

风险 5：shared resolver 直接负责 diagnostics，导致调用方失去恢复策略控制权

- 应对：
  - resolver 只返回严格解析结果
  - diagnostics 继续由 skeleton 或 future binder 创建

风险 6：compatibility fallback 若直接混入 strict frontend 调用点，会把 unknown declared type 静默升级成 object type

- 应对：
  - 仅为 `tryResolveDeclaredType(...)` 提供 optional mapper overload，不修改默认 strict overload 语义
  - `FrontendClassSkeletonBuilder` 与未来 binder/type inference 默认使用 no-mapper overload
  - 用单元测试锚定 malformed structured text 不会触发 mapper

---

## 8. 本轮完成定义

满足以下条件，才算本轮迁移完成：

- `ScopeTypeResolver` 已落地并被 `ClassRegistry` 复用
- `FrontendClassSkeletonBuilder` 已删除 private declared type parser，改走 shared resolver
- skeleton 已通过最小 type-scope 链解析 lexical inner class type name
- `FrontendScopeAnalyzer` 已在 production class scopes 上发布 immediate-inner type-meta
- `ClassRegistry#findType(...)` 已复用 shared strict resolver，而不是继续维护独立 strict 核心
- optional unresolved mapper 已落地，并被严格限制为 compatibility fallback 扩展点
- shared resolver、skeleton、analyzer 三个层面的正反测试均已补齐并通过
- 相关文档与代码注释已同步，不再互相冲突
