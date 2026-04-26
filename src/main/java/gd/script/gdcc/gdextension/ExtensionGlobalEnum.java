package gd.script.gdcc.gdextension;

import java.util.List;

public record ExtensionGlobalEnum(
        String name,
        boolean isBitfield,
        List<ExtensionEnumValue> values
) { }
