package dev.superice.gdcc.scope;

import dev.superice.gdcc.type.GdType;
import org.jetbrains.annotations.NotNull;

public interface CaptureDef {
    @NotNull String getName();

    @NotNull GdType getType();

    @NotNull FunctionDef getDefinedInFunction();
}
