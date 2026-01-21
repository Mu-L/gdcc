package dev.superice.gdcc.type;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import static dev.superice.gdcc.type.GdFloatVectorType.VECTOR3;

public final class GdBasisType implements GdCompoundVectorType{
    public static final GdBasisType BASIS = new GdBasisType();

    @Override
    public @NotNull String getTypeName() {
        return "Basis";
    }

    @Override
    public @NotNull List<GdVectorType> getBaseComponentTypes() {
        return List.of(VECTOR3, VECTOR3, VECTOR3);
    }

    @Override
    public @NotNull GdExtensionTypeEnum getGdExtensionType() {
        return GdExtensionTypeEnum.BASIS;
    }
}
