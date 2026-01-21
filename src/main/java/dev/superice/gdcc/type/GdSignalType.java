package dev.superice.gdcc.type;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public final class GdSignalType implements GdMetaType{
    public @Nullable List<@NotNull GdType> arguments = null;

    @Override
    public @NotNull String getTypeName() {
        return "Signal";
    }

    @Override
    public boolean isNullable() {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof GdSignalType that)) return false;
        return Objects.equals(arguments, that.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(arguments);
    }

    @Override
    public @NotNull GdExtensionTypeEnum getGdExtensionType() {
        return GdExtensionTypeEnum.SIGNAL;
    }
}
