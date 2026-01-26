package dev.superice.gdcc.backend;

import dev.superice.gdcc.scope.ClassRegistry;
import org.jetbrains.annotations.NotNull;

public record CodegenContext(
        @NotNull ClassRegistry classRegistry
        ) {
}
