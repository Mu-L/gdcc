package dev.superice.gdcc.backend;

import dev.superice.gdcc.lir.LirModule;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface Codegen {
    List<GeneratedFile> generate(@NotNull CodegenContext ctx, @NotNull LirModule module);
}
