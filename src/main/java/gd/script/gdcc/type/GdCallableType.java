package gd.script.gdcc.type;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public final class GdCallableType implements GdMetaType {
    public @NotNull GdType returnType = GdVariantType.VARIANT;
    public @Nullable List<@NotNull GdType> arguments = null;
    public boolean nativeCallable = false;

    @Override
    public @NotNull String getTypeName() {
        return "Callable";
    }

    @Override
    public boolean isNullable() {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof GdCallableType that)) return false;
        return Objects.equals(returnType, that.returnType) &&
               Objects.equals(arguments, that.arguments) &&
                nativeCallable == that.nativeCallable;
    }

    @Override
    public int hashCode() {
        return Objects.hash(returnType, arguments);
    }

    @Override
    public @NotNull GdExtensionTypeEnum getGdExtensionType() {
        return GdExtensionTypeEnum.CALLABLE;
    }
}
