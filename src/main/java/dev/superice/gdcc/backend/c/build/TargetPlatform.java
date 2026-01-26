package dev.superice.gdcc.backend.c.build;

import dev.superice.gdcc.enums.HardwareArchitecture;
import org.jetbrains.annotations.NotNull;

public enum TargetPlatform {
    WINDOWS_X86_64(HardwareArchitecture.X86_64),
    ;

    public final @NotNull HardwareArchitecture architecture;

    TargetPlatform(@NotNull HardwareArchitecture architecture) {
        this.architecture = architecture;
    }
}
