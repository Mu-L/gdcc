package gd.script.gdcc.frontend.sema.resolver;

import gd.script.gdcc.scope.ResolveRestriction;
import gd.script.gdcc.util.StringUtil;
import dev.superice.gdparser.frontend.ast.Node;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/// Input contract for one frontend visible-value lookup.
///
/// `domain` stays explicit on purpose so callers choose the semantic domain intentionally instead
/// of forcing the resolver to guess lookup intent purely from AST shape.
public record FrontendVisibleValueResolveRequest(
        @NotNull String name,
        @NotNull Node useSite,
        @NotNull ResolveRestriction restriction,
        @NotNull FrontendVisibleValueDomain domain
) {
    public FrontendVisibleValueResolveRequest {
        name = StringUtil.requireTrimmedNonBlank(name, "name");
        Objects.requireNonNull(useSite, "useSite must not be null");
        Objects.requireNonNull(restriction, "restriction must not be null");
        Objects.requireNonNull(domain, "domain must not be null");
    }

    public FrontendVisibleValueResolveRequest(
            @NotNull String name,
            @NotNull Node useSite,
            @NotNull FrontendVisibleValueDomain domain
    ) {
        this(name, useSite, ResolveRestriction.unrestricted(), domain);
    }
}
