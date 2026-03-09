package dev.superice.gdcc.frontend.sema;

/// Symbol binding categories resolved during frontend semantic analysis.
public enum FrontendBindingKind {
    LOCAL_VAR,
    PARAMETER,
    CAPTURE,
    PROPERTY,
    /// A frontend binding that resolved to a signal value/member.
    ///
    /// Signals are intentionally kept separate from properties and functions so later semantic
    /// stages can preserve Godot-style signal behavior without guessing from declaration shape.
    SIGNAL,
    UTILITY_FUNCTION,
    CONSTANT,
    SINGLETON,
    GLOBAL_ENUM,
    TYPE_META,
    UNKNOWN
}
