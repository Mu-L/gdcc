package dev.superice.gdcc.frontend.sema.analyzer.support;

import dev.superice.gdcc.frontend.scope.ClassScope;
import dev.superice.gdcc.frontend.sema.FrontendAstSideTable;
import dev.superice.gdcc.scope.ResolveRestriction;
import dev.superice.gdcc.scope.Scope;
import dev.superice.gdparser.frontend.ast.DeclarationKind;
import dev.superice.gdparser.frontend.ast.VariableDeclaration;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/// Freezes the minimal body-phase support island for class property initializer subtrees.
///
/// Current contract supports only:
/// - `VariableDeclaration(kind == VAR && value != null)`
/// - whose declaration scope is a `ClassScope`
///
/// This keeps property initializer publication explicit without widening the whole class body into
/// an executable region.
public final class FrontendPropertyInitializerSupport {
    private FrontendPropertyInitializerSupport() {
    }

    public static boolean isSupportedPropertyInitializer(
            @NotNull FrontendAstSideTable<Scope> scopesByAst,
            @NotNull VariableDeclaration variableDeclaration
    ) {
        Objects.requireNonNull(scopesByAst, "scopesByAst must not be null");
        Objects.requireNonNull(variableDeclaration, "variableDeclaration must not be null");
        if (variableDeclaration.kind() != DeclarationKind.VAR || variableDeclaration.value() == null) {
            return false;
        }
        return scopesByAst.get(variableDeclaration) instanceof ClassScope;
    }

    public static @NotNull ResolveRestriction restrictionFor(@NotNull VariableDeclaration variableDeclaration) {
        return Objects.requireNonNull(variableDeclaration, "variableDeclaration must not be null").isStatic()
                ? ResolveRestriction.staticContext()
                : ResolveRestriction.instanceContext();
    }
}
