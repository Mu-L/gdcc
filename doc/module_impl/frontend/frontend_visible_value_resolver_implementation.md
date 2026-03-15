# FrontendVisibleValueResolver 行为与接入说明

> 本文档定义 `FrontendVisibleValueResolver` 的职责边界、输入输出、可见性规则、与 `FrontendVariableAnalyzer` / shared `Scope` 协议的分工，以及在“同一 callable 内禁止变量遮蔽”的前提下它仍然需要解决的问题。

## 文档状态

- 性质：frontend binder 辅助设计 / 行为事实源
- 更新时间：2026-03-15
- 适用范围：
  - `src/main/java/dev/superice/gdcc/frontend/sema/**`
  - `src/main/java/dev/superice/gdcc/frontend/sema/analyzer/**`
  - `src/main/java/dev/superice/gdcc/frontend/scope/**`
  - `src/test/java/dev/superice/gdcc/frontend/sema/**`
- 关联文档：
  - `doc/module_impl/frontend/frontend_variable_analyzer_plan.md`
  - `doc/module_impl/frontend/scope_architecture_refactor_plan.md`
  - `doc/module_impl/frontend/scope_analyzer_implementation.md`
  - `doc/module_impl/frontend/diagnostic_manager.md`
  - `doc/analysis/frontend_semantic_analyzer_research_report.md`

---

## 1. 背景

当前 frontend 已经具备并计划继续具备两类事实：

- `FrontendScopeAnalyzer` 负责发布 lexical/container scope graph
- `FrontendVariableAnalyzer` 负责把 parameter / local declaration inventory 写进 `CallableScope` / `BlockScope`

但现有 shared `Scope` 协议只表达：

- 哪一层“拥有这个名字”
- restriction 下当前层命中是否 allowed / blocked

它不表达：

- 这个 declaration 从源码哪个位置开始可见
- `var x = x` 右侧是否能看到正在声明的 `x`

因此 frontend binder 在 use-site 上不能直接把 `scope.resolveValue(...)` 作为最终答案，而必须额外经过一个前端专用的“可见性修正层”。本文把这层行为冻结为 `FrontendVisibleValueResolver`。

---

## 2. 设计目标

`FrontendVisibleValueResolver` 的目标只有一个：

- 在不修改 shared `Scope` 协议的前提下，为 frontend use-site 解析补上 declaration-order 可见性语义。

它不负责：

- 构建 scope graph
- 往 scope 中写 parameter/local binding
- 生产 diagnostics
- 写入 `symbolBindings()`
- 表达式类型推断
- member/call 解析
- function namespace 或 type-meta namespace 的解析

---

## 3. 与 no-shadowing 规则的关系

当前 frontend 还额外冻结一条约束：

- 同一 `FunctionDeclaration` / `ConstructorDeclaration` 内，不允许变量遮蔽同一 callable 中更早可见的 parameter / local / future capture。

这条规则由 `FrontendVariableAnalyzer` 负责检查、诊断并跳过非法 declaration。

因此 `FrontendVisibleValueResolver` 的前提是：

- scope 中已经只保留“合法写入”的 parameter/local inventory
- 它不需要再额外承担“识别非法 nested shadowing declaration”的职责

但即便如此，它仍然有必要存在。原因是：

- “禁止同一 callable 内变量遮蔽”只能消除一类冲突
- 它不能解决“首次声明在 use-site 之后”的 declaration-order 问题
- 它也不能解决 initializer 内自引用问题

---

## 4. 前置条件

`FrontendVisibleValueResolver` 的调用依赖以下已发布事实：

1. `FrontendAnalysisData.scopesByAst()` 已经可读
2. `FrontendVariableAnalyzer` 已经把合法 parameter/local declaration 写入 `CallableScope` / `BlockScope`
3. parameter/local 对应的 `ScopeValue.declaration()` 分别指向：
   - `Parameter`
   - `VariableDeclaration`
4. `useSite` 节点本身已经能通过 `scopesByAst` 找到当前 lexical scope

在当前 MVP 下，它不依赖：

- `symbolBindings()`
- `expressionTypes()`
- `resolvedMembers()`
- `resolvedCalls()`

---

## 5. 推荐接口形态

推荐保持一个 frontend-only 的窄接口，例如：

```java
public final class FrontendVisibleValueResolver {
    public @NotNull ScopeLookupResult<ScopeValue> resolve(
            @NotNull String name,
            @NotNull Node useSite,
            @NotNull ResolveRestriction restriction,
            @NotNull FrontendAnalysisData analysisData
    ) {
        ...
    }
}
```

这里刻意不把它塞进 shared `Scope` 协议，原因是：

- declaration-order 可见性只属于 frontend binder 语义
- `ClassRegistry` / `ClassScope` / backend/shared resolver 不应承担这个概念
- 避免把 shared scope protocol 与 frontend AST 位置语义重新耦合

---

## 6. 冻结行为

### 6.1 总体规则

`FrontendVisibleValueResolver.resolve(...)` 的行为应冻结为：

1. 从 `analysisData.scopesByAst().get(useSite)` 获取当前 lexical scope
2. 对 `BlockScope` / `CallableScope` 使用“逐层 value lookup + declaration-order 过滤”
3. 对 `ClassScope` / `ClassRegistry` 使用现有 shared `Scope` 语义，不再额外做 statement-order 过滤
4. `FOUND_BLOCKED` 仍保留原有含义，不可被 declaration-order 过滤误降级成 `NOT_FOUND`

### 6.2 当前层命中的处理

对于 `BlockScope` / `CallableScope` 当前层命中的 `ScopeValue`：

- 若结果是 `FOUND_BLOCKED`，直接返回
- 若结果是 `FOUND_ALLOWED`：
  - 若 declaration 当前已可见，则返回
  - 若 declaration 当前尚不可见，则把它当作“当前层不贡献可用 binding”，继续向 lexical parent 查找
- 若结果是 `NOT_FOUND`，继续向 lexical parent 查找

### 6.3 declaration 可见性规则

当前 MVP 先冻结以下最小规则。

#### parameter

- `Parameter` 在 function / constructor executable body 内始终视为可见
- 参数默认值表达式的“只允许看前面参数”规则当前仍 deferred，不在本文当前行为范围内

#### ordinary local `var`

- `VariableDeclaration` 只有在 declaration 结束位置早于 use-site 起始位置时才视为可见
- 推荐比较基准：
  - `declaration.range().endByte` / `endOffset`
  - `useSite.range().startByte` / `startOffset`
- 具体字段名以 `gdparser` 当前 `Range` API 为准，但语义必须保持为：
  - declaration 整体结束后，binding 才开始可见

#### class/global bindings

- class property / signal / class const / singleton / global enum 等非 callable-local binding 不受 statement-order 过滤影响
- 它们继续按 shared `Scope` 的当前协议工作

### 6.4 initializer 自引用

对于：

```gdscript
var x = x
```

右侧 `x` 不应视为已经可见的当前 local declaration。

因此本文冻结：

- local declaration 的“开始可见点”不能取 declaration 的起始位置
- 必须至少晚于 declaration/value 初始化表达式所在区间的 use-site
- 用 declaration 结束位置做可见性判断可以满足这一要求

---

## 7. 典型工作流程

推荐的内部工作流程如下：

1. 读取 `useSite` 当前 scope
2. 若当前 scope 为 `BlockScope` 或 `CallableScope`：
   - 调用 `resolveValueHere(name, restriction)`
   - 根据 declaration-order 判断当前命中是否可见
   - 不可见则上移到 parent scope
3. 若当前 scope 为 `ClassScope` 或 `ClassRegistry`：
   - 直接委托现有 `resolveValue(name, restriction)` 语义
4. 直到命中一个可见 binding，或整条 lexical chain 结束

对应的伪代码形态：

```java
resolve(name, useSite, restriction, analysisData):
    scope = requireScope(useSite)
    while (scope != null) {
        if (scope instanceof BlockScope || scope instanceof CallableScope) {
            var hit = scope.resolveValueHere(name, restriction)
            if (hit.isBlocked()) {
                return hit
            }
            if (hit.isAllowed() && isVisibleAtUseSite(hit.requireValue(), useSite)) {
                return hit
            }
            scope = scope.getParentScope()
            continue
        }
        return scope.resolveValue(name, restriction)
    }
    return ScopeLookupResult.notFound()
```

---

## 8. 场景示例

### 8.1 首次声明位于 use-site 之后

源码：

```gdscript
func ping():
    print(count)
    var count = 1
```

变量分析阶段结束后：

- 当前 body `BlockScope` 已写入 `count`

若 binder 直接调用裸 `scope.resolveValue("count")`：

- 会错误命中后面的 local `count`

`FrontendVisibleValueResolver` 的正确行为：

1. 在当前 `BlockScope.resolveValueHere("count")` 命中 local `count`
2. 比较 declaration 结束位置与 `print(count)` 中标识符的起始位置
3. 发现 declaration 尚未结束，因此当前 local 不可见
4. 继续向 parent scope 查找
5. 若 parent 没有同名 binding，则返回 `NOT_FOUND`

最终效果：

- `print(count)` 不会错误命中尚未生效的 local

### 8.2 inner block 中的 use-site 不能看见 outer block 稍后声明的 local

源码：

```gdscript
func ping():
    if ready:
        print(flag)
    var flag = true
```

这里 `flag` 是 outer block 中的首次声明，不属于“非法 shadowing”，但仍然不能在 `if` body 的 use-site 上提前可见。

`FrontendVisibleValueResolver` 的正确行为：

1. 在 `if` body 的 `BlockScope` 中查不到 `flag`
2. 回退到 outer function body `BlockScope`
3. 命中 local `flag`
4. 发现它的 declaration 仍晚于 `print(flag)` 的 use-site
5. 将这次命中视为“当前仍不可见”，继续向上查
6. 若更外层也无同名 binding，则返回 `NOT_FOUND`

### 8.3 initializer 内不能看到正在声明的 local

源码：

```gdscript
func ping():
    var node = node
```

若直接按 inventory 查询：

- 右侧 `node` 会错误命中当前 declaration

`FrontendVisibleValueResolver` 的正确行为：

1. 当前 `BlockScope` 命中 local `node`
2. 但右侧 use-site 仍位于 declaration 内部
3. declaration-order 判定为“尚不可见”
4. 继续向 parent 查找
5. 若外层无同名 binding，则返回 `NOT_FOUND`

### 8.4 与 no-shadowing 规则的分工

源码：

```gdscript
func ping(value):
    if ready:
        var value = 1
```

这里的问题不应由 `FrontendVisibleValueResolver` 处理，而应在 `FrontendVariableAnalyzer` 阶段就被诊断并跳过写入。

因此本文冻结的分工是：

- `FrontendVariableAnalyzer`：负责发现“同一 callable 内非法 shadowing”
- `FrontendVisibleValueResolver`：只负责在“合法 declaration inventory”之上补 statement-order 可见性

---

## 9. 与 shared `Scope` 协议的边界

本文明确不建议：

- 给 `Scope.resolveValue(...)` 增加 `index`
- 给 `Scope.resolveValue(...)` 增加 frontend-specific `VisibilityFilter`

原因不是这些方案完全不可实现，而是它们会把 frontend use-site 可见性语义推进 shared scope protocol。

当前建议继续冻结：

- shared `Scope` 负责 lexical inventory / namespace / restriction
- frontend binder 通过 `FrontendVisibleValueResolver` 叠加 declaration-order 可见性

---

## 10. 当前实现前建议的测试锚点

若后续为 `FrontendVisibleValueResolver` 新增单测，建议至少覆盖：

- 首次 local 声明在 use-site 之后 -> 不可见
- 首次 outer-block local 声明在 inner use-site 之后 -> 不可见
- `var x = x` -> 右侧不命中当前 declaration
- parameter 在 callable body 内始终可见
- `FOUND_BLOCKED` 的 class-member 命中不会被 resolver 错误降级成 `NOT_FOUND`
- 非 callable-local binding 继续遵循现有 `ClassScope` / `ClassRegistry` 行为

---

## 11. 建议触达文件

若后续实现本文档对应行为，预计会触达：

- `src/main/java/dev/superice/gdcc/frontend/sema/FrontendVisibleValueResolver.java`
- `src/test/java/dev/superice/gdcc/frontend/sema/FrontendVisibleValueResolverTest.java`
- 视 binder 接线位置，可能还需要：
  - `src/main/java/dev/superice/gdcc/frontend/sema/analyzer/**`
  - `src/test/java/dev/superice/gdcc/frontend/sema/FrontendSemanticAnalyzerFrameworkTest.java`
