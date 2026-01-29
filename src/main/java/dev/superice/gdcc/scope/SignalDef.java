package dev.superice.gdcc.scope;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.List;
import java.util.Map;

public non-sealed interface SignalDef extends ParameterEntityDef {
    @NotNull String getName();

    void setName(@NotNull String name);

    @NotNull
    @UnmodifiableView
    Map<String, String> getAnnotations();

    int getParameterCount();

    @Nullable ParameterDef getParameter(int index);

    @Nullable ParameterDef getParameter(@NotNull String name);

    @NotNull
    @UnmodifiableView
    List<? extends ParameterDef> getParameters();
}
