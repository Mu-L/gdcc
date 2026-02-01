package dev.superice.gdcc.gdextension;

import com.google.gson.annotations.SerializedName;
import dev.superice.gdcc.scope.*;
import dev.superice.gdcc.type.GdType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ExtensionGdClass(
        @SerializedName("name") String name,
        @SerializedName("is_refcounted") boolean isRefcounted,
        @SerializedName("is_instantiable") boolean isInstantiable,
        @SerializedName("inherits") String inherits,
        @SerializedName("api_type") String apiType,
        @SerializedName("enums") List<ClassEnum> enums,
        @SerializedName("methods") List<ClassMethod> methods,
        @SerializedName("properties") List<PropertyInfo> properties,
        @SerializedName("constants") List<ConstantInfo> constants
) implements ClassDef {
    @Override
    public @NotNull String getName() {
        return name;
    }

    @Override
    public @NotNull String getSuperName() {
        return inherits == null ? "" : inherits;
    }

    @Override
    public boolean isAbstract() {
        return false;
    }

    @Override
    public boolean isTool() {
        return false;
    }

    @Override
    public @NotNull @UnmodifiableView Map<String, String> getAnnotations() {
        return Map.of();
    }

    @Override
    public boolean hasAnnotation(@NotNull String key) {
        return false;
    }

    @Override
    public String getAnnotation(@NotNull String key) {
        return null;
    }

    @Override
    public @NotNull @UnmodifiableView List<? extends SignalDef> getSignals() {
        return List.of(); // TODO: Signals are not yet parsed
    }

    @Override
    public @NotNull @UnmodifiableView List<? extends PropertyDef> getProperties() {
        return Collections.unmodifiableList(properties);
    }

    @Override
    public @NotNull @UnmodifiableView List<? extends FunctionDef> getFunctions() {
        return Collections.unmodifiableList(methods);
    }

    @Override
    public boolean hasFunction(@NotNull String functionName) {
        for (var function : methods) {
            if (function.getName().equals(functionName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isGdccClass() {
        return false;
    }

    public record ClassEnum(String name, boolean isBitfield, List<ExtensionEnumValue> values) {
    }

    public record ClassMethod(
            String name,
            boolean isConst,
            boolean isVararg,
            boolean isStatic,
            boolean isVirtual,
            long hash,
            List<Long> hashCompatibility,
            ClassMethodReturn returnValue,
            List<ExtensionFunctionArgument> arguments
    ) implements FunctionDef {
        @Override
        public @NotNull String getName() {
            return name;
        }

        @Override
        public boolean isAbstract() {
            return isVirtual;
        }

        @Override
        public boolean isLambda() {
            return false;
        }

        @Override
        public boolean isHidden() {
            return false;
        }

        @Override
        public @NotNull @UnmodifiableView Map<String, String> getAnnotations() {
            return Map.of();
        }

        @Override
        public @Nullable ParameterDef getParameter(int index) {
            if (index < 0 || index >= arguments.size()) {
                return null;
            }
            return arguments.get(index);
        }

        @Override
        public @Nullable ParameterDef getParameter(@NotNull String name) {
            for (var arg : arguments) {
                if (arg.name().equals(name)) {
                    return arg;
                }
            }
            return null;
        }

        @Override
        public int getParameterCount() {
            return arguments.size();
        }

        @Override
        public @NotNull @UnmodifiableView List<? extends ParameterDef> getParameters() {
            return Collections.unmodifiableList(arguments);
        }

        @Override
        public @Nullable CaptureDef getCapture(@NotNull String name) {
            return null;
        }

        @Override
        public int getCaptureCount() {
            return 0;
        }

        @Override
        public @UnmodifiableView Map<String, ? extends CaptureDef> getCaptures() {
            return Map.of();
        }

        @Override
        public @NotNull GdType getReturnType() {
            return Objects.requireNonNull(ClassRegistry.tryParseTextType(returnValue.type));
        }

        public record ClassMethodReturn(String type) {
        }
    }

    public record PropertyInfo(String name, String type, boolean isReadable, boolean isWritable, String defaultValue) implements PropertyDef {
        @Override
        public @NotNull String getName() {
            return name;
        }

        @Override
        public @NotNull GdType getType() {
            return Objects.requireNonNull(ClassRegistry.tryParseTextType(type));
        }

        @Override
        public boolean isStatic() {
            return false;
        }

        @Override
        public @Nullable String getInitFunc() {
            return null;
        }

        @Override
        public @Nullable String getGetterFunc() {
            return null;
        }

        @Override
        public @Nullable String getSetterFunc() {
            return null;
        }

        @Override
        public @NotNull @UnmodifiableView Map<String, String> getAnnotations() {
            return Map.of();
        }
    }

    public record ConstantInfo(String name, String value) {
    }
}
