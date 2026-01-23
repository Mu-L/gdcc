package dev.superice.gdcc.gdextension;

import java.util.List;

public record ExtensionBuiltinClassSizes(String buildConfiguration, List<ClassSizeInfo> sizes) {
    public record ClassSizeInfo(String name, int size) { }
}
