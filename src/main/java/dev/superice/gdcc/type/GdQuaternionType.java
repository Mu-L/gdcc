package dev.superice.gdcc.type;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import static dev.superice.gdcc.type.GdFloatVectorType.VECTOR4;

public final class GdQuaternionType implements GdCompoundVectorType {
    public static final GdQuaternionType QUATERNION = new GdQuaternionType();

    @Override
    public @NotNull List<GdVectorType> getBaseComponentTypes() {
        return List.of(VECTOR4);
    }

    @Override
    public @NotNull String getTypeName() {
        return "Quaternion";
    }

    @Override
    public @NotNull GdExtensionTypeEnum getGdExtensionType() {
        return GdExtensionTypeEnum.QUATERNION;
    }
}
