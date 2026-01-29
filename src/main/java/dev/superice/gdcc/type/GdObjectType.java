package dev.superice.gdcc.type;

import org.jetbrains.annotations.NotNull;

public final class GdObjectType implements GdType {
    public final static GdObjectType OBJECT = new GdObjectType();

    public final String className;
    public boolean engineType = false;

    public GdObjectType(@NotNull String className) {
        this.className = className;
        this.engineType = false;
    }

    public GdObjectType(@NotNull String className, boolean engineType) {
        this.className = className;
        this.engineType = engineType;
    }

    public GdObjectType() {
        this.className = "Object";
    }

    public boolean isEngineType() {
        return engineType;
    }

    @Override
    public @NotNull String getTypeName() {
        return this.className;
    }

    @Override
    public boolean isNullable() {
        return true;
    }

    @Override
    public @NotNull GdExtensionTypeEnum getGdExtensionType() {
        return GdExtensionTypeEnum.OBJECT;
    }
}
