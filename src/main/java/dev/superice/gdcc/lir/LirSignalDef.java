package dev.superice.gdcc.lir;

import dev.superice.gdcc.scope.ParameterDef;
import dev.superice.gdcc.scope.SignalDef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.*;

public final class LirSignalDef implements LirParameterEntity, SignalDef {
    private @NotNull String name;
    private final Map<String, String> annotations;
    private final List<LirParameterDef> parameters;

    public LirSignalDef(@NotNull String name, Map<String, String> annotations, List<LirParameterDef> parameters) {
        this.name = Objects.requireNonNull(name);
        this.annotations = new HashMap<>(Objects.requireNonNull(annotations));
        this.parameters = new ArrayList<>(Objects.requireNonNull(parameters));
    }

    public LirSignalDef(@NotNull String name) {
        this.name = Objects.requireNonNull(name);
        this.annotations = new HashMap<>();
        this.parameters = new ArrayList<>();
    }

    public @NotNull String getName() {
        return name;
    }

    public void setName(@NotNull String name) {
        this.name = name;
    }

    public @NotNull Map<String, String> getAnnotations() {
        return annotations;
    }

    public void setAnnotations(Map<String, String> annotations) {
        this.annotations.clear();
        this.annotations.putAll(annotations);
    }

    @Override
    public void addParameter(@NotNull LirParameterDef parameter) {
        this.parameters.add(parameter);
    }

    @Override
    public void addParameter(int index, @NotNull LirParameterDef parameter) {
        this.parameters.add(index, parameter);
    }

    @Override
    public int getParameterCount() {
        return this.parameters.size();
    }

    @Override
    public @Nullable LirParameterDef getParameter(int index) {
        if (index < 0 || index >= this.parameters.size()) {
            return null;
        }
        return this.parameters.get(index);
    }

    @Override
    public @Nullable LirParameterDef getParameter(@NotNull String name) {
        for (var param : parameters) {
            if (param.name().equals(name)) {
                return param;
            }
        }
        return null;
    }

    @Override
    public @NotNull @UnmodifiableView List<? extends ParameterDef> getParameters() {
        return Collections.unmodifiableList(this.parameters);
    }

    @Override
    public boolean removeParameter(@NotNull String name) {
        return this.parameters.removeIf(parameter -> parameter.name().equals(name));
    }

    @Override
    public boolean removeParameter(int index) {
        if (index < 0 || index >= this.parameters.size()) {
            return false;
        }
        this.parameters.remove(index);
        return true;
    }

    @Override
    public void clearParameters() {
        this.parameters.clear();
    }
}
