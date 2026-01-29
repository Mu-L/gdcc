package dev.superice.gdcc.scope;

import dev.superice.gdcc.type.GdType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.Map;

public interface PropertyDef {
    @NotNull String getName();

    @NotNull GdType getType();

    boolean isStatic();

    @Nullable String getInitFunc();

    @Nullable String getGetterFunc();

    @Nullable String getSetterFunc();

    @NotNull
    @UnmodifiableView
    Map<String, String> getAnnotations();
}
