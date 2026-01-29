package dev.superice.gdcc.lir;

import dev.superice.gdcc.scope.CaptureDef;
import dev.superice.gdcc.scope.FunctionDef;
import dev.superice.gdcc.type.GdType;
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
