package dev.superice.gdcc.frontend.sema;

/// Published status for one expression-typing fact.
///
/// This mirrors the body-phase recovery lattice used by chain reduction, but keeps the expression
/// publication contract in `frontend.sema` so later analyzers do not depend on helper-internal
/// enums.
public enum FrontendExpressionTypeStatus {
    /// The expression has a stable published type and may continue through exact downstream typing.
    RESOLVED,
    /// The expression matched a real winner, but current policy forbids consuming it here.
    BLOCKED,
    /// The expression belongs to a supported route, but the current phase still lacks frozen input.
    DEFERRED,
    /// The expression intentionally degrades to runtime-dynamic semantics and publishes `Variant`.
    DYNAMIC,
    /// The current supported route reached a stable negative conclusion.
    FAILED,
    /// The expression sits behind a fail-closed MVP boundary and must be sealed explicitly.
    UNSUPPORTED
}
