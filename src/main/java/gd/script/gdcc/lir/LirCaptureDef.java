package gd.script.gdcc.lir;

import gd.script.gdcc.scope.CaptureDef;
import gd.script.gdcc.scope.FunctionDef;
import gd.script.gdcc.type.GdType;
import org.jetbrains.annotations.NotNull;


public record LirCaptureDef(
        @NotNull String name,
        @NotNull GdType type,
        @NotNull LirFunctionDef definedInFunction) implements CaptureDef {

    @Override
    public @NotNull String getName() {
        return name;
    }

    @Override
    public @NotNull GdType getType() {
        return type;
    }

    @Override
    public @NotNull FunctionDef getDefinedInFunction() {
        return definedInFunction;
    }
}
