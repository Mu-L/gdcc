package gd.script.gdcc.scope;

import gd.script.gdcc.type.GdType;
import org.jetbrains.annotations.NotNull;

public interface CaptureDef {
    @NotNull String getName();

    @NotNull GdType getType();

    @NotNull FunctionDef getDefinedInFunction();
}
