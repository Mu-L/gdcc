package gd.script.gdcc.lir;

import gd.script.gdcc.scope.ParameterDef;
import gd.script.gdcc.scope.ParameterEntityDef;
import gd.script.gdcc.type.GdType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** XML entity: <parameter .../>. Used in function parameters and signal parameters. */
public record LirParameterDef(
        @NotNull String name,
        @NotNull GdType type,
        @Nullable String defaultValueFunc,
        @NotNull LirParameterEntity definedInEntity
) implements ParameterDef {
    @Override
    public @NotNull String getName() {
        return name();
    }

    @Override
    public @NotNull GdType getType() {
        return type();
    }

    @Override
    public @Nullable String getDefaultValueFunc() {
        return defaultValueFunc();
    }

    @Override
    public @NotNull ParameterEntityDef getDefinedIn() {
        return definedInEntity;
    }
}
