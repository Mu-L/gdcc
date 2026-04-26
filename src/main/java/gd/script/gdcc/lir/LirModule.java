package gd.script.gdcc.lir;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class LirModule {
    private @NotNull String moduleName;
    private final @NotNull List<LirClassDef> classDefs;

    public LirModule(@NotNull String moduleName, @NotNull List<LirClassDef> classDefs) {
        this.moduleName = moduleName;
        this.classDefs = new ArrayList<>(classDefs);
    }

    public @NotNull String getModuleName() {
        return moduleName;
    }

    public void setModuleName(@NotNull String moduleName) {
        this.moduleName = moduleName;
    }

    public @NotNull List<LirClassDef> getClassDefs() {
        return classDefs;
    }

    public void addClassDef(@NotNull LirClassDef classDef) {
        this.classDefs.add(classDef);
    }
}
