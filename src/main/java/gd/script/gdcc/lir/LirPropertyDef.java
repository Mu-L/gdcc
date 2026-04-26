package gd.script.gdcc.lir;

import gd.script.gdcc.scope.PropertyDef;
import gd.script.gdcc.type.GdType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class LirPropertyDef implements PropertyDef {
    private @NotNull String name;
    private @NotNull GdType type;
    private boolean isStatic;
    private @Nullable String initFunc;
    private @Nullable String getterFunc;
    private @Nullable String setterFunc;
    private Map<String, String> annotations;

    public LirPropertyDef(
            @NotNull String name,
            @NotNull GdType type,
            boolean isStatic,
            @Nullable String initFunc,
            @Nullable String getterFunc,
            @Nullable String setterFunc,
            @NotNull Map<String, String> annotations
    ) {
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
        this.isStatic = isStatic;
        this.initFunc = initFunc;
        this.getterFunc = getterFunc;
        this.setterFunc = setterFunc;
        this.annotations = new HashMap<>(Objects.requireNonNull(annotations));
    }

    public LirPropertyDef(
            @NotNull String name,
            @NotNull GdType type
    ) {
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
        this.isStatic = false;
        this.initFunc = null;
        this.getterFunc = null;
        this.setterFunc = null;
        this.annotations = new HashMap<>();
    }

    public @NotNull String getName() {
        return name;
    }

    public void setName(@NotNull String name) {
        this.name = name;
    }

    public @NotNull GdType getType() {
        return type;
    }

    public void setType(@NotNull GdType type) {
        this.type = type;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public void setStatic(boolean aStatic) {
        isStatic = aStatic;
    }

    public @Nullable String getInitFunc() {
        return initFunc;
    }

    public void setInitFunc(@Nullable String initFunc) {
        this.initFunc = initFunc;
    }

    public @Nullable String getGetterFunc() {
        return getterFunc;
    }

    public void setGetterFunc(@Nullable String getterFunc) {
        this.getterFunc = getterFunc;
    }

    public @Nullable String getSetterFunc() {
        return setterFunc;
    }

    public void setSetterFunc(@Nullable String setterFunc) {
        this.setterFunc = setterFunc;
    }

    public @NotNull Map<String, String> getAnnotations() {
        return annotations;
    }

    public void setAnnotations(Map<String, String> annotations) {
        this.annotations = annotations;
    }
}
