package dev.superice.gdcc.scope;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.List;
import java.util.Map;

public interface ClassDef {
    @NotNull String getName();

    @NotNull String getSuperName();

    boolean isAbstract();

    boolean isTool();

    @NotNull
    @UnmodifiableView
    Map<String, String> getAnnotations();

    boolean hasAnnotation(@NotNull String key);

    String getAnnotation(@NotNull String key);

    @NotNull
    @UnmodifiableView
    List<? extends SignalDef> getSignals();

    @NotNull
    @UnmodifiableView
    List<? extends PropertyDef> getProperties();

    @NotNull
    @UnmodifiableView
    List<? extends FunctionDef> getFunctions();
    /**
     * For LIR-described user classes this is always true.
     */
    boolean isGdccClass();
}
