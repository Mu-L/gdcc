package dev.superice.gdcc.lir;

import dev.superice.gdcc.type.GdType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** XML entity: <parameter .../>. Used in function parameters and signal parameters. */
public record LirParameterDef(
        @NotNull String name,
        @NotNull GdType type,
        @Nullable String defaultValueFunc,
        @NotNull LirParameterEntity definedInEntity
) {}
