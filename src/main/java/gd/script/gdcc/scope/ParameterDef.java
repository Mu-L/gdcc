package gd.script.gdcc.scope;

import gd.script.gdcc.type.GdType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ParameterDef {
    @NotNull String getName();

    @NotNull GdType getType();

    @Nullable String getDefaultValueFunc();

    @NotNull ParameterEntityDef getDefinedIn();
}
