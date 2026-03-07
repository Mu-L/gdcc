package dev.superice.gdcc.scope;

import dev.superice.gdcc.type.GdType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Strict type-meta binding returned from the dedicated type namespace.
///
/// This record answers "what does this identifier mean when the parser/analyzer expects a type?".
/// Unlike `ScopeValue`, the result lives on the type side of the language and only exposes the
/// runtime/value type after the analyzer leaves the type namespace.
///
/// Field semantics:
/// - `name`: the resolved type/meta name or the strict textual type expression.
/// - `instanceType`: the runtime/value-side type represented by this type-meta symbol.
/// - `kind`: the metadata domain the type name came from.
/// - `declaration`: backing metadata object when one exists.
/// - `pseudoType`: whether this binding should be treated as a synthetic/non-class-like type symbol.
///
/// Current examples:
/// - builtin `String` -> instance type `GdStringType.STRING`
/// - engine/gdcc class `Node` -> instance type `GdObjectType("Node")`
/// - global enum type -> instance type `GdIntType.INT`, with `pseudoType = true`
/// - `Array[String]` -> synthesized strict type with no concrete declaration object
public record ScopeTypeMeta(
        @NotNull String name,
        @NotNull GdType instanceType,
        @NotNull ScopeTypeMetaKind kind,
        @Nullable Object declaration,
        boolean pseudoType
) {
    /// Validates the mandatory parts of the strict type-meta binding.
    public ScopeTypeMeta {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(instanceType, "instanceType");
        Objects.requireNonNull(kind, "kind");
    }
}
