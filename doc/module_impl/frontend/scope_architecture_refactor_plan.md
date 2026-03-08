# Scope 架构与共享 Resolver 重构计划

> 本文档作为 `dev.superice.gdcc.scope` 与 `dev.superice.gdcc.frontend.scope` 的长期事实源，定义当前已冻结的协议、shared resolver 边界、Godot 对齐结论，以及 frontend 后续接入时必须遵守的约束。

## 文档状态

- 状态：事实源维护中（`Scope` / frontend scope chain / shared resolver 已落地，frontend binder 接入待实施）
- 更新时间：2026-03-08
- 适用范围：
  - `src/main/java/dev/superice/gdcc/scope/**`
  - `src/main/java/dev/superice/gdcc/scope/resolver/**`
  - `src/main/java/dev/superice/gdcc/frontend/scope/**`
  - `src/main/java/dev/superice/gdcc/backend/c/gen/insn/BackendMethodCallResolver.java`
  - `src/main/java/dev/superice/gdcc/backend/c/gen/insn/BackendPropertyAccessResolver.java`
  - 后续 `src/main/java/dev/superice/gdcc/frontend/sema/**`
- 关联文档：
  - `doc/module_impl/frontend/frontend_implementation_plan.md`
  - `doc/analysis/frontend_semantic_analyzer_research_report.md`
  - `doc/module_impl/call_method_implementation.md`
  - `doc/module_impl/load_store_property_implementation.md`
  - `doc/module_impl/backend/load_static_implementation.md`
  - `doc/gdcc_type_system.md`
  - `doc/gdcc_c_backend.md`
  - `doc/gdcc_low_ir.md`
- 已归并文档：
  - `doc/module_impl/frontend/phase4_inner_class_and_restriction_followup_plan.md`

---

## 1. 背景与目标

当前仓库已经有 `dev.superice.gdcc.scope` 包，但后续工程真正需要的不是“若干 metadata 接口 + `ClassRegistry` 注册表”的松散集合，而是一套可以被 frontend binder 与 backend adapter 同时依赖的稳定协议。

本次重构已经冻结并正在继续推进的目标有两件：

1. 把原先埋在 backend `BackendMethodCallResolver` / `BackendPropertyAccessResolver` 中、未来前后端都要复用的成员 metadata 查找逻辑抽到 `scope` 包。
2. 基于 `dev.superice.gdcc.scope.Scope` 协议，在 `dev.superice.gdcc.frontend.scope` 包内建立 frontend 真正会实例化的 lexical scope chain，使 AST 语义分析可以按“class -> callable -> block”的层级显式建 scope，并通过 parent 链完成无 base 标识符解析。

从 Godot 当前 analyzer 可提炼出的总体形态仍然成立：

- 一条 lexical scope 链：block / function / class / global。
- 一套成员解析 helper：在 class metadata 或 receiver type 上做 method/property owner lookup。
- `Scope` 回答“无 base 的名字从哪里来”，shared resolver 回答“有 receiver 的成员从哪里来”；两者相关，但不能重新混成一个万能对象。

---

## 2. 当前已冻结的架构事实

### 2.1 `ClassRegistry` 是 global scope root

`ClassRegistry` 现在既是全局 metadata root，也是正式的 global scope root。

它当前稳定承载：

- builtin / engine / gdcc class 的注册、查询与 `checkAssignable(...)`
- singleton / global enum / utility function 的全局查询
- 严格 `type-meta` namespace 的全局入口
- frontend skeleton/interface 产物注入后的跨脚本可见性

它在 `Scope` 协议下的当前事实如下：

- `getParentScope()` 恒为 `null`
- `setParentScope(non-null)` 必须拒绝，保持 root invariant
- `resolveValueHere(...)` 处理 singleton / global enum
- `resolveFunctionsHere(...)` 处理 utility function
- `resolveTypeMetaHere(...)` 处理 builtin / engine / gdcc class、global enum type、strict typed container
- 兼容入口 `findType(...)`、`findSingletonType(...)`、`findGlobalEnum(...)`、`findUtilityFunctionSignature(...)` 继续保留，但后续 frontend binder 不应再把 `findType(...)` 当作最终语义判定器

### 2.2 `Scope` 协议已经从“lexical skeleton”升级为正式 binding protocol

`Scope` 当前是 restriction-aware lexical binding protocol，而不是仅供未来参考的骨架。

协议层已经冻结的事实：

- `Scope` 明确分离三套 namespace：
  - value：`resolveValue(...)`
  - function：`resolveFunctions(...)`
  - type-meta：`resolveTypeMeta(...)`
- 三套 namespace 彼此独立：
  - value lookup 采用最近命中优先
  - function lookup 采用最近非空层优先
  - type-meta lookup 采用严格、独立的 lexical type namespace
- `Scope` 的 found/miss 已不再靠 `null` 或空列表表达，而是统一通过 `ScopeLookupResult<T>` + `ScopeLookupStatus`
- tri-state lookup 已冻结为：
  - `FOUND_ALLOWED`
  - `FOUND_BLOCKED`
  - `NOT_FOUND`
- 只有 `NOT_FOUND` 允许继续递归到 parent
- `FOUND_ALLOWED` 与 `FOUND_BLOCKED` 都必须停止 lookup，确保当前层命中继续构成 shadowing
- `Scope` 实现继续只承载 metadata/lookup，不承载 AST 节点、backend-only 状态或 codegen 细节

### 2.3 `ResolveRestriction` 的当前语义边界

`ResolveRestriction` 已正式进入 `Scope` 方法签名，其职责是表达“当前上下文允许哪些未限定 class member 停止 lookup”，而不是承载诊断文本。

当前冻结的 restriction 语义：

- `ResolveRestriction.unrestricted()`：兼容旧调用方与 unrestricted 测试
- `ResolveRestriction.staticContext()` 允许无 base 命中：
  - class const
  - static property
  - static method
- `ResolveRestriction.instanceContext()` 允许无 base 命中：
  - class const
  - instance property / instance method
  - static property / static method
- 当前 restriction 只影响未限定 class-member 的 value/function lookup
- parameter / capture / block local / global root 当前都不受 static-vs-instance member restriction 影响
- frontend binder 仍负责把“为什么不合法”翻译成用户可见的 diagnostics

### 2.4 `type-meta` 当前采用“统一签名 + always-allowed”契约

为了统一三套 namespace 的调用形状，`resolveTypeMeta(..., restriction)` 也接受 `ResolveRestriction`，但当前协议已经明确冻结：

- 对现有 `ScopeTypeMetaKind` 集合，`type-meta` lookup 只允许返回 `FOUND_ALLOWED` / `NOT_FOUND`
- 当前 `type-meta` lookup 不产生 `FOUND_BLOCKED`
- `TYPE_META` 的后续合法性继续由 frontend binder 在消费阶段判断，而不是由 type lookup 本身承担
- 当前 binder/static analysis 需要处理的消费路径至少包括：
  - `TypeRef`
  - `CastExpression`
  - `TypeTestExpression`
  - static access
  - constructor resolution
  - `load_static`

这条约定不能被后续实现重新改写为“lookup 顺便完成全部 type legality 判断”，否则会再次把 binding 与消费阶段耦合回去。

### 2.5 Frontend lexical scope chain 已落地

`dev.superice.gdcc.frontend.scope` 当前已经落地以下实现：

- `AbstractFrontendScope`
- `ClassScope`
- `CallableScope`
- `BlockScope`

当前冻结的链式语义如下：

- value namespace：
  - `BlockScope` local
  - `CallableScope` parameter
  - `CallableScope` capture
  - `ClassScope` member
  - global scope
- function namespace：
  - 继续使用“最近非空层优先”
  - 同名 overload set 在当前层若存在，即使被 restriction 全部阻止，也必须返回 `FOUND_BLOCKED`
- type-meta namespace：
  - 最近命中优先
  - 只沿 type namespace 的 lexical parent 链递归
  - 不污染 value/function namespace

当前 `ClassScope` 的事实：

- 直接索引当前类 direct property / direct method
- inherited property / method 只在 direct miss 时回退
- 类成员继承查找属于当前 class scope layer，不是额外 lexical parent
- class-local type-meta 只走 lexical namespace，不沿继承链扩散

当前 `CallableScope` / `BlockScope` 的事实：

- 默认继承 parent 的 type namespace
- 当前没有 function namespace 的本地注册能力
- 仍保留 `defineTypeMeta(...)` 级别的扩展余地，以承接 preload alias / const alias / local enum 等未来来源

当前仍未纳入 `Scope` 直接建模的内容：

- `self` 不是隐式 `ScopeValue`
- signal 不是现成的 `ScopeValue` / `FunctionDef`
- 这两者的完整语义和 diagnostics 仍由 frontend binder 的后续阶段负责

### 2.6 Shared resolver 已经成为前后端共享事实源

`scope` 包当前已经拥有两个正式 shared resolver：

- `ScopePropertyResolver`
- `ScopeMethodResolver`

#### `ScopePropertyResolver` 的边界

它当前只负责 instance-style property metadata lookup：

- known object receiver：沿 class metadata + inheritance 解析 owner
- builtin receiver：走 builtin class metadata

它明确不处理：

- `TypeMeta` 驱动的静态常量或 enum 项读取
- builtin constant / engine integer constant
- frontend static binding 与 `load_static` 决策

它当前暴露的失败协议有明确区分：

- object receiver metadata 缺失：`MetadataUnknown`
- hierarchy malformed：`Failed`
- property 不存在：`Failed`
- builtin class 缺失或 builtin property 缺失：`Failed`

这意味着 shared resolver 只回答“metadata lookup 的事实”，至于 caller 是否在 `PROPERTY_MISSING` 时选择 fail-fast 或动态路径，仍是 caller 的策略责任。

#### `ScopeMethodResolver` 的边界

它当前同时支持：

- instance receiver：基于 `GdType`
- static/type-meta receiver：基于 `ScopeTypeMeta`

它明确不处理：

- constructor route，例如 `ClassName.new(...)`
- `EnumType.VALUE` / builtin constant 读取
- frontend LIR 形态选择
- backend C 发射细节

它当前冻结的候选筛选与排序基线如下：

- 先做 applicability 过滤：
  - 参数数量
  - 默认参数兼容
  - vararg 兼容
  - `ClassRegistry#checkAssignable(...)` 类型兼容
- 再按 owner distance 优先
- instance call 路径上，实例方法优先于 static 方法
- non-vararg 优先于 vararg
- instance object receiver 若同优先级最佳候选仍歧义，则允许 `OBJECT_DYNAMIC`
- `Variant` receiver 允许 `VARIANT_DYNAMIC`
- builtin/static receiver 的歧义或不适用仍属于 hard failure，而不是动态兜底

shared resolver 与 adapter 的边界已经冻结为：

- shared resolver：只输出 metadata 事实、fallback reason 与 hard failure
- backend adapter：继续负责 `LirVariable` 校验、receiver 渲染、pack/unpack、default literal 物化、最终 C 发射
- frontend binder：后续负责语法上下文分流、diagnostics 与 typed semantic result

---

## 3. Godot 对齐事实与已知差异

### 3.1 被 restriction 阻止的当前层命中仍然构成 shadowing

这条结论已经作为 `ScopeLookupStatus.FOUND_BLOCKED` 的设计前提冻结下来。

Godot 当前行为可概括为：

- 标识符解析顺序先 local/member，再 global
- `static_context` 检查发生在当前层命中之后
- 当前层/local/member 一旦命中，即使随后因 static context 非法而报错，也不会继续回退 outer/global 的同名绑定

对 GDCC 的直接约束是：

- `restriction` 绝不能被建模为“当前层不合法就当成 miss”
- lookup 协议必须能区分：
  - `FOUND_ALLOWED`
  - `FOUND_BLOCKED`
  - `NOT_FOUND`

相关 Godot 事实源：

- `reduce_identifier(...)` 先 local/member，再 global：
  - `https://github.com/godotengine/godot/blob/220b0b2f74d8e089481b140c42a42992a76dd6fc/modules/gdscript/gdscript_analyzer.cpp#L4363-L4524`
- `static_context` 检查发生在命中之后，且随后直接返回：
  - `https://github.com/godotengine/godot/blob/220b0b2f74d8e089481b140c42a42992a76dd6fc/modules/gdscript/gdscript_analyzer.cpp#L4458-L4484`
  - `https://github.com/godotengine/godot/blob/220b0b2f74d8e089481b140c42a42992a76dd6fc/modules/gdscript/gdscript_analyzer.cpp#L4523-L4549`
- `reduce_identifier_from_base(...)` 命中类成员后不会退回 global：
  - `https://github.com/godotengine/godot/blob/220b0b2f74d8e089481b140c42a42992a76dd6fc/modules/gdscript/gdscript_analyzer.cpp#L4160-L4251`

### 3.2 Inner class 的当前规则是有意识的工程化差异

当前 GDCC 在 inner class 上已经冻结以下规则：

- `type-meta` namespace：
  - 继续沿完整 lexical parent 链查找
  - inner class 仍可看见 outer class 提供的 inner class / class enum / 其他 type-meta
- `value/function` namespace：
  - `ClassScope` 在 lexical miss 时跳过连续 outer `ClassScope` ancestor
  - inner class 不会无 base 继承 outer class 的 property / const / function

这条规则当前是通过 `ClassScope` 在 value/function lookup 时显式寻找“第一个非 `ClassScope` ancestor”实现的，而不是通过引入双 parent 或 namespace-specific parent 改写整条 parent 链。

这与 Godot 当前 outer-class member visibility 并不完全一致。Godot 当前会把 outer class 纳入 current-scope class chain，只是顺位晚于 base type：

- `https://github.com/godotengine/godot/blob/220b0b2f74d8e089481b140c42a42992a76dd6fc/modules/gdscript/gdscript_analyzer.cpp#L320-L344`

因此必须长期保留以下表述，不能在后续文档里被弱化：

- 这是 GDCC 当前的工程决策，不是 Godot 当前实现的 1:1 平移
- 若未来要进一步靠拢 Godot，需要重新评估：
  - namespace-specific parent
  - scoped view
  - outer class / base type / current class 三者的更细粒度查找顺序

### 3.3 当前 Godot 对齐范围仍有限

当前 `ResolveRestriction` 与 tri-state lookup 对 Godot 的对齐范围只覆盖：

- class const
- property
- method

尚未纳入完整 parity 的内容：

- `self`
- signal
- 通过实例语法访问静态成员时的 frontend 绑定与诊断策略

这些问题必须在后续 frontend binder 阶段单独建模，而不能假装已经由 `Scope` 或 shared resolver 自动解决。

---

## 4. Frontend 后续接入约束

Phase 7+ 的 frontend 接入必须建立在上述事实之上，不能重新发明第二套 lookup 语义。

### 4.1 AST -> Scope 建图策略

当前推荐并冻结的建图策略：

- module/root -> `ClassRegistry`
- class -> `ClassScope`
- function / constructor / lambda -> `CallableScope`
- block / if / while / match branch -> `BlockScope`

AST 节点与 scope 的关联仍应由 side-table 维护，而不是把 AST 节点塞回 `Scope` 实现。

### 4.2 Binder 的上下文敏感解析顺序

后续 binder 必须按语法上下文选择 namespace：

- value position：优先 `resolveValue(...)`
- function/callable position：优先 `resolveFunctions(...)`
- type position：优先 `resolveTypeMeta(...)`

额外约束：

- 遇到同名 value/type symbol 时，由语法上下文决定 namespace，不做“猜哪个更像用户想要的绑定”
- 未限定类成员解析时，先构造 `ResolveRestriction`，再调用 restriction-aware `Scope` API
- 若 `Scope` 返回 `FOUND_BLOCKED`，必须直接形成“当前上下文非法访问该绑定”的语义结论，不能继续回退 outer/global

### 4.3 `TYPE_META` 的消费规则

后续 frontend 接入 `TYPE_META` 时必须继续遵守以下边界：

- `TypeRef`、`CastExpression`、`TypeTestExpression`、显式 type hint 统一走 `resolveTypeMeta(...)`
- `ClassName.static_method(...)` 走 static method 路径
- `ClassName.new(...)` / builtin ctor / object ctor 走 constructor resolution / `construct_*`
- `EnumType.VALUE` / builtin constant / engine integer constant 走 `load_static`
- 禁止在 binder/type inference 中回退到宽松 `findType(...)`

### 4.4 当前建议保留 deferred 的来源

若 Phase 7+ 尚未完整接入以下来源，应给出显式 deferred / unsupported 诊断，而不是静默忽略：

- preload class
- const-based type alias
- local enum
- 其他 parser 已接入但 frontend 仍未消费的 type-meta 来源

---

## 5. 当前行为冻结测试

以下测试当前承担“事实冻结”角色，后续改动若改变这些测试覆盖的行为，应先更新本文档中的迁移说明与边界约定。

- `Scope` 协议与 global scope：
  - `ScopeProtocolTest`
  - `ClassRegistryScopeTest`
  - `ClassRegistryTypeMetaTest`
- Frontend scope chain：
  - `ScopeChainTest`
  - `ScopeTypeMetaChainTest`
  - `ScopeCaptureShapeTest`
  - `ClassScopeResolutionTest`
- Inner class / restriction follow-up：
  - `FrontendInnerClassScopeIsolationTest`
  - `FrontendNestedInnerClassScopeIsolationTest`
  - `FrontendStaticContextValueRestrictionTest`
  - `FrontendStaticContextFunctionRestrictionTest`
  - `FrontendStaticContextShadowingTest`
- Shared property resolver：
  - `ScopePropertyResolverTest`
  - `PropertyResolverParityTest`
- Shared method resolver：
  - `ScopeMethodResolverTest`
  - `MethodResolverParityTest`

---

## 6. 已知风险与后续检查项

### 6.1 不要把 `FOUND_BLOCKED` 重新退化回 `null` / 空列表语义

只要把 blocked hit 重新当成 miss，Godot-style shadowing 就会立即失真。

### 6.2 不要把 `type-meta` legality 提前塞回 lookup 阶段

`resolveTypeMeta(..., restriction)` 当前只是统一协议形状。若未来把 static access / constructor / `load_static` 的全部合法性都塞回 lookup，会再次制造 binding 阶段与消费阶段的职责漂移。

### 6.3 Inner class 规则与 Godot 外层成员可见性仍有差异

当前方案是工程化折中，不是最终 parity 终点。若未来目标转向更高 Godot 一致性，必须重新设计 parent/view 模型，而不是在现有 `ClassScope` 上继续堆条件分支。

### 6.4 `self` / signal 仍未纳入当前 `Scope` 协议

当前 restriction parity 不能宣称已经完整覆盖 Godot 的 static-context 语义。后续 binder 必须补齐：

- `SelfExpression`
- signal 绑定
- 相关错误文案与语法上下文差异

### 6.5 `ClassScope` 与显式 property resolver 的 failure policy 仍不完全一致

当前 `ClassScope` 对 missing super metadata 采取“停止继承链并返回 miss”的轻量策略，而 `ScopePropertyResolver` 对显式 property access 会把 `MISSING_SUPER_METADATA` 作为 hard failure 暴露给 caller。后续 frontend 不应误以为这两条路径已经共享完全一致的失败语义。

### 6.6 当前 method/property resolver 仍有后续补强空间

当前 shared resolver 已统一 backend 基线下的事实源，但仍有明确的后续检查项：

- 为 static/type-meta receiver 补齐更多负面场景测试：
  - pseudo type 拒绝
  - global enum 拒绝
  - static receiver metadata 缺失
  - builtin static lookup 边界
  - constructor route 与 static method 路径分流
- 评估 builtin property lookup 是否也需要与 method resolver 一样做 typed container 的 receiver 名规范化，例如 `Array[T] -> Array`、`Dictionary[K, V] -> Dictionary`
- 若 frontend 后续需要更细粒度的 overload specificity 或 diagnostics，不要误把当前“适用性过滤 + ownerDistance + instance 优先 + non-vararg 优先”基线当成最终闭包

---

## 7. 参考事实源

### 7.1 本仓库

- `src/main/java/dev/superice/gdcc/scope/Scope.java`
- `src/main/java/dev/superice/gdcc/scope/ResolveRestriction.java`
- `src/main/java/dev/superice/gdcc/scope/ScopeLookupStatus.java`
- `src/main/java/dev/superice/gdcc/scope/ScopeLookupResult.java`
- `src/main/java/dev/superice/gdcc/scope/ClassRegistry.java`
- `src/main/java/dev/superice/gdcc/scope/ScopeTypeMeta.java`
- `src/main/java/dev/superice/gdcc/scope/ScopeTypeMetaKind.java`
- `src/main/java/dev/superice/gdcc/scope/resolver/ScopePropertyResolver.java`
- `src/main/java/dev/superice/gdcc/scope/resolver/ScopeMethodResolver.java`
- `src/main/java/dev/superice/gdcc/frontend/scope/AbstractFrontendScope.java`
- `src/main/java/dev/superice/gdcc/frontend/scope/ClassScope.java`
- `src/main/java/dev/superice/gdcc/frontend/scope/CallableScope.java`
- `src/main/java/dev/superice/gdcc/frontend/scope/BlockScope.java`
- `src/main/java/dev/superice/gdcc/backend/c/gen/insn/BackendPropertyAccessResolver.java`
- `src/main/java/dev/superice/gdcc/backend/c/gen/insn/BackendMethodCallResolver.java`
- `doc/module_impl/frontend/frontend_implementation_plan.md`
- `doc/analysis/frontend_semantic_analyzer_research_report.md`

### 7.2 Godot 参考

- `https://github.com/godotengine/godot/blob/220b0b2f74d8e089481b140c42a42992a76dd6fc/modules/gdscript/gdscript_analyzer.cpp#L320-L344`
- `https://github.com/godotengine/godot/blob/220b0b2f74d8e089481b140c42a42992a76dd6fc/modules/gdscript/gdscript_analyzer.cpp#L4160-L4251`
- `https://github.com/godotengine/godot/blob/220b0b2f74d8e089481b140c42a42992a76dd6fc/modules/gdscript/gdscript_analyzer.cpp#L4363-L4524`
- `https://github.com/godotengine/godot/blob/220b0b2f74d8e089481b140c42a42992a76dd6fc/modules/gdscript/gdscript_analyzer.cpp#L4458-L4484`
- `https://github.com/godotengine/godot/blob/220b0b2f74d8e089481b140c42a42992a76dd6fc/modules/gdscript/gdscript_analyzer.cpp#L4523-L4549`
