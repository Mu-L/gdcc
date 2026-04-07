# Frontend Builtin Property Access Plan

## 1. Background

当前代码库对 builtin instance property 的目标语义应当是 compile-ready 的，例如：

- `vector.x`
- `color.r`
- `Vector3(1.0, 2.0, 3.0).y`
- `Basis.IDENTITY.x`

其中：

- `Vector3.ZERO` / `Color.RED` 这类 type-meta static load 已经走 `load_static`
- `vector.x` / `color.r` 这类写法则属于 ordinary instance property route
- 它们与 builtin keyed access 不是同一条语义路径

当前用户感知到的问题是：类似 `vector.x` 的 builtin property read 仍无法顺利通过 frontend compile surface。

---

## 2. Investigation Summary

### 2.1 Observed failure shape

用最小回归用例验证时：

- `func axis_x(vector: Vector3) -> float: return vector.x`
- `func constructed_y() -> float: return Vector3(1.0, 2.0, 3.0).y`

都会在 build CFG 之前失败，最终由 `FrontendCfgGraphBuilder.requireLoweringReadyExpressionType(...)` 看到整个 `AttributeExpression` 仍是 `FAILED`。

这说明问题不在 backend，也不在最终 body lowering，而是在 frontend shared semantic / published fact 阶段更早就已经把 route 判坏。

### 2.2 Actual failure chain

当前链路如下：

1. `FrontendChainReductionHelper.reducePropertyStep(...)` 对非 object receiver 会进入 `reduceBuiltinPropertyStep(...)`。
2. `reduceBuiltinPropertyStep(...)` 调用 `ScopePropertyResolver.resolveBuiltinProperty(...)`。
3. `ScopePropertyResolver.resolveBuiltinProperty(...)` 只遍历 `ClassRegistry.findBuiltinClass(...).getProperties()`。
4. 但实际 Godot `extension_api_451.json` 中，`Vector3` / `Color` / `Basis` 等 builtin 的成员字段并不在 `properties`，而是在 `members`：
   - 例如 `Vector3` 条目具有 `members = [x, y, z]`
   - 例如 `Color` 条目具有 `members = [r, g, b, a]`
5. `ExtensionApiLoader.parseBuiltinClasses(...)` 当前会解析：
   - `operators`
   - `methods`
   - `constructors`
   - `properties`
   - `constants`
6. 但它没有把 builtin JSON schema 中的 `members` 读入 `ExtensionBuiltinClass`。
7. 因此 builtin class 在 registry 中是“类存在，但 property surface 为空”的状态。
8. `ScopePropertyResolver.resolveBuiltinProperty(...)` 最终返回 `BUILTIN_PROPERTY_MISSING`。
9. `FrontendChainReductionHelper.reduceBuiltinPropertyStep(...)` 把该 step 发布为 `FAILED`。
10. expression typing / compile surface 因此 fail-closed，CFG builder 也就无法继续。

### 2.3 Why this is not a backend gap

backend 侧其实已经具备 builtin property 读写能力：

- `LoadPropertyInsnGen` 对非 object receiver 会生成 `godot_<Builtin>_get_<member>`
- `StorePropertyInsnGen` 也已经走 builtin property setter path
- `gdextension-lite` 的命名契约同样明确支持：
  - `godot_Vector3_get_x`
  - `godot_Vector3_set_x`

也就是说，当前缺口不是 C codegen，而是 shared metadata ingestion 没有把 builtin `members` 暴露给 frontend/backend 共用的 property resolver。

### 2.4 Affected surface

所有在 Godot builtin metadata 中通过 `members` 暴露字段的类型都会受影响，常见包括：

- `Vector2` / `Vector2i`
- `Rect2` / `Rect2i`
- `Vector3` / `Vector3i`
- `Transform2D`
- `Vector4` / `Vector4i`
- `Plane`
- `Quaternion`
- `AABB`
- `Basis`
- `Transform3D`
- `Projection`
- `Color`

因此当前问题不仅影响 read path，也会影响 assignment / mutation path，例如：

- `vector.x = 1.0`
- `color.a = 0.5`

---

## 3. Design Constraints

实施修复时必须保持以下约束：

- builtin property access 与 builtin keyed access 必须继续严格区分
  - `vector.x` 是 ordinary attribute property
  - `vector["x"]` 仍然不是当前 MVP 支持面
- 不要在 frontend、backend、scope resolver 中分别写三套 builtin member 特判
- `frontend_rules.md` 中关于 keyed builtin access 的 unsupported 边界必须保持不变
- compile gate / chain binding / lowering 只消费 shared published fact；不要在下游偷偷补一套 “如果 builtin member miss 就再试一次 JSON members” 的旁路逻辑

---

## 4. Implementation Plan

### Step 1. Normalize builtin `members` into shared property surface

目标：

- 让 `ExtensionBuiltinClass` 同时保留 raw schema truth，并向 shared resolver 提供稳定的 property-like surface

当前状态：

- 已完成：`ExtensionBuiltinClass` 现显式保留 raw `members()`，并通过 `getProperties()` 统一暴露 member-backed synthetic property surface
- 已完成：`ExtensionApiLoader.parseBuiltinClasses(...)` 已解析 builtin JSON schema 中的 `members`
- 已完成：默认 API 上的 `Vector3` / `Color` member-backed property surface 与 `ScopePropertyResolver.resolveBuiltinProperty(...)` 已由单元测试覆盖正反路径

实施建议：

1. 在 `ExtensionBuiltinClass` 中显式建模 builtin `members`
   - 使用该类型的嵌套 record 即可，不要新拆独立 public 文件
2. 更新 `ExtensionApiLoader.parseBuiltinClasses(...)`
   - 解析 JSON 中的 `members`，不要读取整个json文件
   - 同时保留现有 `properties` / `constants` / `constructors` / `methods` 解析
3. 给 `ExtensionBuiltinClass` 增加一个统一的 property-like 暴露面
   - 当前事实源 `extension_api_451.json` 的 builtin class 不导出 raw `properties`
   - `getProperties()` 直接返回 synthetic member-backed properties 视图
   - synthetic builtin member property 的 contract：
     - `name` = member name
     - `type` = member type
     - `isReadable = true`
     - `isWritable = true`
4. 不要把 `members` 只修补到某一个 consumer
   - 真正的修复点必须是 metadata normalization 层

验收：

- `ClassRegistry.findBuiltinClass("Vector3").getProperties()` 中包含 `x/y/z`
- `ClassRegistry.findBuiltinClass("Color").getProperties()` 中包含 `r/g/b/a`
- `ScopePropertyResolver.resolveBuiltinProperty(..., "x")` 能在真实默认 API 上返回 `Resolved`
- 不影响现有 builtin constant / method / constructor 路由

### Step 2. Close frontend semantic and compile surface

目标：

- 让 builtin property route 在 shared semantic、expression typing、compile gate、CFG builder 之间闭环

当前状态：

- 已完成：chain binding 现可对 `vector.x`、`Basis.IDENTITY.x` 这类 builtin property step 发布 `RESOLVED` member fact
- 已完成：expr typing 现可对 `vector.x`、`Color(...).r`、`Basis.IDENTITY.x` 发布稳定结果类型，并继续把 `vector.missing` 锚定为 `FAILED`
- 已完成：compile-ready CFG build / body lowering regression tests 已覆盖 `return vector.x` 与 `return Vector3(...).y`
- 已完成：实现注释与事实源文档已明确 builtin property access 走 ordinary property route，builtin keyed access 仍保持 unsupported

实施建议：

1. 依赖 Step 1 的 normalized metadata，让 `ScopePropertyResolver.resolveBuiltinProperty(...)` 直接复用统一 surface
2. 不在 `FrontendChainReductionHelper.reduceBuiltinPropertyStep(...)` 中新增 JSON schema 旁路
3. 补齐 frontend regression tests，至少覆盖：
   - `vector.x`
   - `Color(1, 2, 3, 4).r`
   - `Basis.IDENTITY.x`
   - negative：`vector.missing`
4. 补齐 compile/lowering regression tests，至少覆盖：
   - `return vector.x` 不再在 CFG builder 前失败
   - `return Vector3(1.0, 2.0, 3.0).y` 产出 `ConstructBuiltinInsn + LoadPropertyInsn`
5. 文档中明确写清：
   - builtin property access 现在属于 compile-ready surface
   - builtin keyed access 仍然 unsupported

验收：

- chain binding 对 builtin property step 发布 `FrontendResolvedMember.status = RESOLVED`
- expression typing 对 `vector.x` 发布 `RESOLVED(float)`
- compile-only gate 不再把该 route 误判为 blocker
- `FrontendCfgGraphBuilder` / `FrontendLoweringBodyInsnPass` 可以稳定产出 `MemberLoadItem` / `LoadPropertyInsn`

### Step 3. Close backend parity and writable route

目标：

- 确认 read / write 两条 builtin member property path 都与真实默认 API 对齐

当前状态：

- 已完成：`CLoadPropertyInsnGenTest` / `CStorePropertyInsnGenTest` 现基于默认 API 覆盖 `Vector3.x`、`Color.r`、`Color.a` 等 member-backed property 的 getter/setter 生成，并继续锚定 missing member 的 fail-fast 边界
- 已完成：`FrontendAssignmentSemanticSupportTest` / `FrontendExprTypeAnalyzerTest` 现覆盖 builtin member-backed property assignment route，确认 `vector.x = 1.0`、`color.a = 0.5` 走 ordinary writable contract，同时对类型不匹配与 missing member 继续 fail-closed

实施建议：

1. 补 backend/property parity tests，基于默认 API 而不是手工伪造 metadata
2. 覆盖 read path：
   - `LoadPropertyInsn(Vector3, "x")`
   - `LoadPropertyInsn(Color, "r")`
3. 覆盖 write path：
   - `StorePropertyInsn(Vector3, "x")`
   - `StorePropertyInsn(Color, "a")`
4. 验证 `PropertyDefAccessSupport` 与 assignment 路径会自动受益于 normalized builtin member surface

验收：

- C codegen 使用 `godot_<Builtin>_get_<member>` / `godot_<Builtin>_set_<member>`
- writable 判定对 builtin member 不再误报 metadata missing
- `vector.x = 1.0` 这类 route 的 shared semantic 不再因 builtin property miss 提前失败

### Step 4. Documentation sync

目标：

- 把“builtin property access 支持、keyed access 仍不支持”的边界写成事实源，防止后续漂移

当前状态：

- 已完成：事实源文档现明确 builtin instance property read/write 都走 ordinary property route，分别 materialize 为 `LoadPropertyInsn` / `StorePropertyInsn`
- 已完成：`frontend_rules.md` 已把 compile-ready builtin property access 与 unsupported builtin keyed access 的边界写成正式规则
- 已完成：`ScopePropertyResolver` 注释已明确 shared resolver 只消费 normalized builtin property surface，不允许下游再加 JSON `members` 旁路

实施建议：

1. 更新 `frontend_chain_binding_expr_type_implementation.md`
   - 把 builtin property route 列入当前稳定支持面
2. 更新 `frontend_lowering_cfg_pass_implementation.md`
   - 说明 builtin property read 会经过 ordinary `MemberLoadItem -> LoadPropertyInsn`
3. 更新 `frontend_rules.md`
   - 明确区分 builtin property access 与 builtin keyed access
4. 如有必要，补充 `ScopePropertyResolver` / `ExtensionApiLoader` 的注释
   - 写清 Godot builtin JSON schema 的 `members` 会被 normalize 成 property-like surface

验收：

- 文档不再暗示 “builtin property 也是 keyed access”
- 新读代码的人仅通过实现注释与文档即可理解为何 `Vector3.x` 能走 property route

---

## 5. Test Plan

最少测试集建议如下。

### 5.1 Shared resolver

- `ScopePropertyResolverTest`
  - `Vector3.x` resolves on default API
  - `Color.r` resolves on default API
  - missing builtin member still returns `BUILTIN_PROPERTY_MISSING`

### 5.2 Frontend semantic

- `FrontendChainBindingAnalyzerTest`
  - builtin property step publishes `RESOLVED`
- `FrontendExprTypeAnalyzerTest`
  - `vector.x` infers `float`
  - `Basis.IDENTITY.x` infers `Vector3`

### 5.3 Frontend lowering

- `FrontendCfgGraphBuilderTest`
  - builtin property expression is lowering-ready
- `FrontendLoweringBodyInsnPassTest`
  - `vector.x` lowers to `LoadPropertyInsn`
  - `Vector3(...).y` lowers to `ConstructBuiltinInsn` then `LoadPropertyInsn`

### 5.4 Backend

- `CLoadPropertyInsnGenTest`
  - default API path emits `godot_Vector3_get_x`
- `CStorePropertyInsnGenTest`
  - default API path emits `godot_Vector3_set_x`

### 5.5 Negative boundaries

- `vector.missing` 继续报 `sema.member_resolution`
- `vector["x"]` 继续保持 builtin keyed access unsupported
- 不因为修复 builtin member property 而放宽任何 `Variant` / keyed / dynamic 路线

---

## 6. Exit Criteria

本计划完成时，应满足以下事实：

- `vector.x`、`color.r`、`Basis.IDENTITY.x` 能以默认 API 端到端通过 frontend compile surface
- builtin member read/write 共享同一份 normalized metadata surface
- frontend / backend / shared resolver 不再各自维护一套 builtin member 规则
- 文档明确区分：
  - supported builtin property access
  - unsupported builtin keyed access
- 相关单元测试覆盖 happy path 与 negative path，并严格锚定行为边界

---

## 7. Builtin Getter ABI Clarification

### 7.1 Investigated concern

近期有一个表面上看起来像 ABI 错误的担忧：

- 如果生成 `godot_Color_get_r($color)` / `godot_Vector3_get_x($vector)`，
- 而真实 gdextension-lite 签名是
  - `godot_float godot_Color_get_r(const godot_Color *self)`
  - `godot_float godot_Vector3_get_x(const godot_Vector3 *self)`

那么看起来像是“少了 `&`”。

### 7.2 End-to-end finding

对 `FrontendLoweringToCProjectBuilderIntegrationTest#lowerFrontendBuiltinPropertyAbiModuleBuildNativeLibraryAndRunInGodot` 的真实生成物与 Godot 运行时验证后，结论是：

- 这不是当前端到端产物中的实际 ABI bug
- builtin 值类型参数在 generated C 的用户方法签名中本来就是指针：
  - `RuntimeBuiltinPropertyAbiProbe_color_r(RuntimeBuiltinPropertyAbiProbe* $self, godot_Color* $color)`
  - `RuntimeBuiltinPropertyAbiProbe_vector_x(RuntimeBuiltinPropertyAbiProbe* $self, godot_Vector3* $vector)`
- GDExtension method bind wrapper 会先从 `Variant` 解包出栈上值，再以地址形式调用用户方法：
  - `const godot_Color arg0 = godot_new_Color_with_Variant(...)`
  - `function(p_instance, &arg0)`
- 进入用户方法体后，frontend/C backend 当前会先把参数指针物化为本地值槽，再对该本地值槽取地址做 member getter：
  - `godot_Color __gdcc_tmp_color_0 = godot_new_Color_with_Color($color);`
  - `$cfg_tmp_v0 = __gdcc_tmp_color_0;`
  - `godot_Color_get_r(&$cfg_tmp_v0);`

因此，真实链路是：

1. runtime wrapper 以 `godot_Color*` / `godot_Vector3*` 形式把 builtin 值参数传入用户方法；
2. 用户方法把参数 materialize 成局部值；
3. builtin property getter/setter 始终对“值槽地址”调用；
4. 最终 ABI 与 gdextension-lite 头文件保持一致。

### 7.3 Root cause of the confusion

误判来自只看了局部 codegen 片段，而没有把以下几层放在一起看：

- 函数参数签名生成：`func.ftl` + `CGenHelper.renderGdTypeRefInC(...)`
- GDExtension method bind wrapper：`call_*` / `ptrcall_*`
- 函数体内的参数物化与 slot write
- builtin property getter 本身总是消费指向值语义 builtin 的地址

单独看某个中间层的“`$color` 没有显式写 `&`”并不能推出最终 emitted C 违反 ABI。

还需要额外区分两类测试视角：

- unit 级 `CLoadPropertyInsnGenTest` 观察的是“body generator 在已有 ref-like receiver slot 上如何发出 getter call”
- 端到端集成测试观察的是“method bind wrapper + 用户方法签名 + 函数体 materialization + getter call”拼起来后的真实 ABI 合同

前者可以继续出现 `godot_Color_get_r($color)` 这类片段；后者才是判断 ABI 是否正确的最终事实源。

### 7.4 Current contract

后续实现必须继续满足以下合同：

- 对 builtin 值类型形参，C 边界签名必须是指针 ABI，而不是按值传 struct
- 对 builtin property getter/setter，最终调用点必须接收到“builtin value 的地址”
- 参数 receiver 可以：
  - 直接转发已有的指针参数，或
  - 先 materialize 到局部值槽，再对局部值槽取地址
- 构造结果、局部变量、临时值这类非 ref receiver，最终调用点必须显式走 `&slot`

### 7.5 Follow-up plan

当前这一条 concern 的修复方式不是修改 getter ABI 代码，而是：

- 用端到端集成测试把真实合同锚定下来，避免以后再次被局部片段误导
- 在事实源文档中明确写出“参数 ABI 是指针、property getter 消费值槽地址、函数体会先 materialize 参数值”
- 后续若重构 builtin value 参数/slot materialization，必须保留上述 ABI 合同

如果未来出现真正的 ABI 回归，应优先检查：

- `renderGdTypeRefInC(...)` 是否仍对 builtin 值类型参数生成 `godot_<Builtin>*`
- method bind wrapper 是否仍以地址把参数传给用户方法
- builtin property load/store 路径是否仍对本地值槽地址调用 getter/setter
