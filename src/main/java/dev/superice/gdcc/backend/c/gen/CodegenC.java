package dev.superice.gdcc.backend.c.gen;

import dev.superice.gdcc.backend.Codegen;
import dev.superice.gdcc.backend.CodegenContext;
import dev.superice.gdcc.backend.GeneratedFile;
import dev.superice.gdcc.lir.LirModule;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CodegenC implements Codegen {
    @Override
    public List<GeneratedFile> generate(@NotNull CodegenContext ctx, @NotNull LirModule module) {
        // TODO: Implement C code generation
        return List.of();
    }
}
