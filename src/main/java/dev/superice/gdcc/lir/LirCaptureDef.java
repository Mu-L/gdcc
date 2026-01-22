package dev.superice.gdcc.lir;

import dev.superice.gdcc.type.GdType;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;


public record LirCaptureDef(
        @NotNull String name,
        @NotNull GdType type,
        @NotNull LirFunctionDef definedInFunction) {

}
