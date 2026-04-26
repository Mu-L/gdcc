package gd.script.gdcc.lir;

import gd.script.gdcc.scope.ParameterEntityDef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface LirParameterEntity extends ParameterEntityDef permits LirFunctionDef, LirSignalDef {
    void addParameter(@NotNull LirParameterDef parameter);

    void addParameter(int index, @NotNull LirParameterDef parameter);

    int getParameterCount();

    @Nullable LirParameterDef getParameter(int index);

    @Nullable LirParameterDef getParameter(@NotNull String name);

    boolean removeParameter(@NotNull String name);

    boolean removeParameter(int index);

    void clearParameters();
}
