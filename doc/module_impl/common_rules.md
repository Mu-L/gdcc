# 通用规则

## Exception 约定

- 项目内自定义异常必须统一放在 `src/main/java/gd/script/gdcc/exception/` 下，不要把异常类散落在业务包中，也不要把它们定义为业务类的内部类。
- 所有项目内自定义异常都必须直接或间接继承 `gd.script.gdcc.exception.GdccException`，以统一基础语义、日志与上层处理方式。
- 即使异常只用于某个模块内部的控制流，也应提取为 `exception` 包中的具名异常类型，再由模块边界将其转换为结果对象、诊断或用户可见错误。
- 迁移或新增异常后，需要同步更新代码与文档中的引用路径，避免残留旧包名或旧约定。

## 命名约定

- 纯校验函数应使用能直接表达“只做校验”的名字，例如 `validateXxx(...)`、`checkXxx(...)`；不要把这类函数命名成 `requireXxx(...)`。
- `requireXxx(...)` 更适合“读取并保证存在”的 helper，例如缺失时抛错并返回已存在对象；如果函数本身不承担读取/获取语义，就不要使用 `require` 前缀。

## 字符串处理约定

- 与当前模块语义无关的纯字符串处理必须统一复用 `gd.script.gdcc.util.StringUtil`，例如 non-blank 校验、trim/null/empty 归一化、多行片段规范化、按行拆分；不要在 `frontend` 等业务类里再定义同构 private helper。
- 如果多个 helper 只是在 `null`、`blank`、`trim` 的处理细节上略有差异，应优先合并成少量统一的 `StringUtil` API，并直接更新调用点，不要保留平行实现。
- 只有当字符串分支本身承载模块规则时，才保留在业务模块内部，例如根据 `extends` 文本判断是否命中 path-based superclass 这种 frontend 语义判定。
