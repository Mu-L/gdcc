package gd.script.gdcc.scope.resolver;

import gd.script.gdcc.scope.Scope;
import gd.script.gdcc.type.GdType;
import org.jetbrains.annotations.Nullable;

/// Optional compatibility hook used after strict declared-type resolution misses a non-structured leaf type.
///
/// The mapper does not participate in malformed structured texts such as `Dictionary[String]` or
/// nested structured containers like `Array[Array[int]]`; those remain hard failures handled by the
/// shared strict parser itself.
@FunctionalInterface
public interface UnresolvedTypeMapper {
    @Nullable GdType mapUnresolvedType(Scope scope, String unresolvedTypeText);
}
