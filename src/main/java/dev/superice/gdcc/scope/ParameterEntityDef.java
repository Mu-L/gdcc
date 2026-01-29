package dev.superice.gdcc.scope;

import dev.superice.gdcc.lir.LirParameterEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.List;

public sealed interface ParameterEntityDef permits LirParameterEntity, FunctionDef, SignalDef {
    int getParameterCount();

    @Nullable ParameterDef getParameter(int index);

    @Nullable ParameterDef getParameter(@NotNull String name);

    @NotNull @UnmodifiableView
    List<? extends ParameterDef> getParameters();
}
