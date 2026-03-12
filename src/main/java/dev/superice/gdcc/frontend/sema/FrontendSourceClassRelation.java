package dev.superice.gdcc.frontend.sema;

import dev.superice.gdcc.frontend.parse.FrontendSourceUnit;
import dev.superice.gdcc.lir.LirClassDef;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Skeleton ownership relation for one parsed source file.
///
/// One `FrontendSourceUnit` always owns exactly:
/// - one top-level script `ClassDef`
/// - zero or more nested/inner `ClassDef`s discovered inside that file
///
/// Keeping this relation explicit removes the previous fragile "units and classDefs share the same
/// index" convention and gives later phases a stable place to hang source-local class skeleton
/// facts even when one file contributes multiple classes.
public record FrontendSourceClassRelation(
        @NotNull FrontendSourceUnit unit,
        @NotNull LirClassDef topLevelClassDef,
        @NotNull List<LirClassDef> innerClassDefs
) {
    public FrontendSourceClassRelation {
        Objects.requireNonNull(unit, "unit must not be null");
        Objects.requireNonNull(topLevelClassDef, "topLevelClassDef must not be null");
        innerClassDefs = List.copyOf(Objects.requireNonNull(innerClassDefs, "innerClassDefs must not be null"));
    }

    /// Returns the top-level class followed by every nested class discovered in the same source.
    public @NotNull List<LirClassDef> allClassDefs() {
        var classDefs = new ArrayList<LirClassDef>(1 + innerClassDefs.size());
        classDefs.add(topLevelClassDef);
        classDefs.addAll(innerClassDefs);
        return List.copyOf(classDefs);
    }
}
