package dev.superice.gdcc.scope;

import dev.superice.gdcc.type.GdType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ParameterDef {
    @NotNull String getName();

    @NotNull GdType getType();

    @Nullable String getDefaultValueFunc();

    @NotNull ParameterEntityDef getDefinedIn();
}
