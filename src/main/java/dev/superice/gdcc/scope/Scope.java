package dev.superice.gdcc.scope;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/// Minimal lexical scope contract shared by frontend binding and future shared resolvers.
///
/// `Scope` intentionally models three independent namespaces:
/// - value bindings via `resolveValue(...)`
/// - function overload sets via `resolveFunctions(...)`
/// - type/meta bindings via `resolveTypeMeta(...)`
///
/// The namespaces stay separate on purpose:
/// - value lookup follows nearest-hit shadowing.
/// - function lookup follows nearest non-empty scope level, so overload resolution stays local.
/// - type/meta lookup stays strict and must not silently fall back to the loose `findType(...)` behavior.
///
/// Implementations are expected to be lightweight metadata containers only.
/// They should not own AST nodes, backend-only state, or code-generation details.
public interface Scope {
    /// Returns the lexical parent scope.
    ///
    /// `null` means this scope is the root of the current lookup chain.
    @Nullable Scope getParentScope();

    /// Updates the lexical parent scope.
    ///
    /// Implementations may reject invalid parent relationships if they need stronger invariants,
    /// but the default protocol treats parent wiring as a simple tree/chain construction step.
    void setParentScope(@Nullable Scope parentScope);

    /// Resolves a value binding in the current scope level only.
    ///
    /// This method does not recurse into the parent chain. Callers that need full lexical lookup
    /// should use `resolveValue(...)` instead.
    @Nullable ScopeValue resolveValueHere(@NotNull String name);

    /// Resolves function candidates in the current scope level only.
    ///
    /// The returned list represents the overload set owned by this scope level for `name`.
    /// Returning an empty list means the lookup should continue in the parent chain.
    @NotNull List<? extends FunctionDef> resolveFunctionsHere(@NotNull String name);

    /// Resolves a type/meta binding in the current scope level only.
    ///
    /// This namespace is separate from values because identifiers such as class names and enum types
    /// participate in type analysis differently from runtime value bindings.
    @Nullable ScopeTypeMeta resolveTypeMetaHere(@NotNull String name);

    /// Resolves a value binding through the lexical parent chain.
    ///
    /// The first non-null binding wins, which gives the standard shadowing semantics used by locals,
    /// parameters, captures, class members, and globals.
    default @Nullable ScopeValue resolveValue(@NotNull String name) {
        Objects.requireNonNull(name, "name");
        var value = resolveValueHere(name);
        if (value != null) {
            return value;
        }
        var parentScope = getParentScope();
        return parentScope != null ? parentScope.resolveValue(name) : null;
    }

    /// Resolves function candidates through the lexical parent chain.
    ///
    /// Lookup stops at the first scope level that contributes a non-empty overload set. This keeps
    /// overload resolution local to the nearest matching scope instead of concatenating every outer scope.
    default @NotNull List<? extends FunctionDef> resolveFunctions(@NotNull String name) {
        Objects.requireNonNull(name, "name");
        var functions = resolveFunctionsHere(name);
        if (!functions.isEmpty()) {
            return functions;
        }
        var parentScope = getParentScope();
        return parentScope != null ? parentScope.resolveFunctions(name) : List.of();
    }

    /// Resolves a type/meta binding through the lexical parent chain.
    ///
    /// The first non-null result wins, mirroring value shadowing while keeping type/meta lookup
    /// strict and independent of the runtime value namespace.
    default @Nullable ScopeTypeMeta resolveTypeMeta(@NotNull String name) {
        Objects.requireNonNull(name, "name");
        var typeMeta = resolveTypeMetaHere(name);
        if (typeMeta != null) {
            return typeMeta;
        }
        var parentScope = getParentScope();
        return parentScope != null ? parentScope.resolveTypeMeta(name) : null;
    }
}
