package dev.superice.gdcc.gdextension;

import java.util.List;
import com.google.gson.annotations.SerializedName;

public record ExtensionAPI(
        @SerializedName("header") ExtensionHeader header,
        @SerializedName("builtin_class_sizes") List<ExtensionBuiltinClassSizes> builtinClassSizes,
        @SerializedName("builtin_class_member_offsets") List<ExtensionBuiltinClassMemberOffsets> builtinClassMemberOffsets,
        @SerializedName("global_enums") List<ExtensionGlobalEnum> globalEnums,
        @SerializedName("utility_functions") List<ExtensionUtilityFunction> utilityFunctions,
        @SerializedName("builtin_classes") List<ExtensionBuiltinClass> builtinClasses,
        @SerializedName("classes") List<ExtensionGdClass> classes,
        @SerializedName("singletons") List<ExtensionSingleton> singletons,
        @SerializedName("native_structures") List<ExtensionNativeStructure> nativeStructures
) {
}
