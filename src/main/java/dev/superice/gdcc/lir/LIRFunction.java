package dev.superice.gdcc.lir;

import dev.superice.gdcc.type.GdType;
import dev.superice.gdcc.type.GdVariantType;
import org.jetbrains.annotations.NotNull;

import java.util.NavigableMap;
import java.util.TreeMap;

public class LIRFunction {
    private final @NotNull String name;
    private final @NotNull NavigableMap<String, GdType> parameters = new TreeMap<>();
    private @NotNull GdType returnType = GdVariantType.VARIANT;

    public LIRFunction(@NotNull String name) {
        this.name = name;
    }

    public @NotNull String getName() {
        return name;
    }

    public @NotNull GdType getReturnType() {
        return returnType;
    }

    public void setReturnType(@NotNull GdType returnType) {
        this.returnType = returnType;
    }
}
