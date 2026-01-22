package dev.superice.gdcc.lir;

import org.jetbrains.annotations.NotNull;

import java.util.*;

/** XML entity: <class_def ...> ... </class_def>. */
public final class LirClassDef {
    private @NotNull String name;
    private @NotNull String superName;
    private boolean isAbstract;
    private boolean isTool;
    private Map<String, String> annotations;
    private List<LirSignalDef> signals;
    private List<LirPropertyDef> properties;
    private List<LirFunctionDef> functions;

    public LirClassDef(
            @NotNull String name,
            @NotNull String superName,
            boolean isAbstract,
            boolean isTool,
            Map<String, String> annotations,
            List<LirSignalDef> signals,
            List<LirPropertyDef> properties,
            List<LirFunctionDef> functions
    ) {
        this.name = name;
        this.superName = superName;
        this.isAbstract = isAbstract;
        this.isTool = isTool;
        this.annotations = new HashMap<>(annotations);
        this.signals = new ArrayList<>(signals);
        this.properties = new ArrayList<>(properties);
        this.functions = new ArrayList<>(functions);
    }

    public @NotNull String getName() {
        return name;
    }

    public void setName(@NotNull String name) {
        this.name = name;
    }

    public @NotNull String getSuperName() {
        return superName;
    }

    public void setSuperName(@NotNull String superName) {
        this.superName = superName;
    }

    public boolean isAbstract() {
        return isAbstract;
    }

    public void setAbstract(boolean anAbstract) {
        isAbstract = anAbstract;
    }

    public boolean isTool() {
        return isTool;
    }

    public void setTool(boolean tool) {
        isTool = tool;
    }

    public Map<String, String> getAnnotations() {
        return annotations;
    }

    public void setAnnotations(Map<String, String> annotations) {
        this.annotations = annotations;
    }

    public List<LirSignalDef> getSignals() {
        return signals;
    }

    public void setSignals(List<LirSignalDef> signals) {
        this.signals = signals;
    }

    public List<LirPropertyDef> getProperties() {
        return properties;
    }

    public void setProperties(List<LirPropertyDef> properties) {
        this.properties = properties;
    }

    public List<LirFunctionDef> getFunctions() {
        return functions;
    }

    public void setFunctions(List<LirFunctionDef> functions) {
        this.functions = functions;
    }
}
