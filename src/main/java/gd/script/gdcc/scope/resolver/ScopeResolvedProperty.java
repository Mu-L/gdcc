package gd.script.gdcc.scope.resolver;

import gd.script.gdcc.scope.ClassDef;
import gd.script.gdcc.scope.PropertyDef;
import gd.script.gdcc.scope.ScopeOwnerKind;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/// Shared property lookup result consumed by frontend and backend.
///
/// The record answers three questions after instance-property resolution succeeds:
/// - which metadata domain owns the property (`ownerKind`)
/// - which class metadata node contributes that property (`ownerClass`)
/// - which concrete property definition matched the lookup (`property`)
///
/// This record intentionally models only **instance/builtin property metadata**.
/// Static accesses such as `EnumType.VALUE` or builtin constants stay on the `load_static`
/// path and must not be funneled through the property resolver.
public record ScopeResolvedProperty(
        @NotNull ScopeOwnerKind ownerKind,
        @NotNull ClassDef ownerClass,
        @NotNull PropertyDef property
) {
    public ScopeResolvedProperty {
        Objects.requireNonNull(ownerKind, "ownerKind");
        Objects.requireNonNull(ownerClass, "ownerClass");
        Objects.requireNonNull(property, "property");
    }
}
