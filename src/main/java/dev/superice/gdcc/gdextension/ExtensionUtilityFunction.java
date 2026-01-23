package dev.superice.gdcc.gdextension;

import java.util.List;

public record ExtensionUtilityFunction(
        String name,
        String returnType,
        String category,
        boolean isVararg,
        int hash,
        List<ExtensionFunctionArgument> arguments
) { }
