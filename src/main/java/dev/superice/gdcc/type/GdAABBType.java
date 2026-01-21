package dev.superice.gdcc.type;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import static dev.superice.gdcc.type.GdFloatVectorType.VECTOR3;

public final class GdAABBType implements GdCompoundVectorType {
    public static final GdAABBType AABB = new GdAABBType();

    @Override
    public @NotNull String getTypeName() {
        return "AABB";
    }

    @Override
    public @NotNull List<GdVectorType> getBaseComponentTypes() {
        return List.of(VECTOR3, VECTOR3);
    }

    @Override
    public @NotNull GdExtensionTypeEnum getGdExtensionType() {
        return GdExtensionTypeEnum.AABB;
    }
}
