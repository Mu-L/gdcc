package gd.script.gdcc.gdextension;

import com.google.gson.annotations.Expose;
import gd.script.gdcc.scope.FunctionDef;
import gd.script.gdcc.scope.ParameterDef;
import gd.script.gdcc.scope.ParameterEntityDef;
import gd.script.gdcc.scope.resolver.ScopeTypeParsers;
import gd.script.gdcc.type.GdType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public record ExtensionFunctionArgument(
        String name,
        String type,
        String defaultValue,
        @Expose(serialize = false, deserialize = false) FunctionDef definedIn
) implements ParameterDef {
    @Override
    public @NotNull String getName() {
        return name;
    }

    @Override
    public @NotNull GdType getType() {
        var parameterName = name == null || name.isBlank() ? "<unnamed>" : name;
        var callableName = definedIn != null ? definedIn.getName() : "<unknown>";
        return ScopeTypeParsers.parseExtensionTypeMetadata(
                type,
                "type of extension parameter '" + parameterName + "' in '" + callableName + "'"
        );
    }

    @Override
    public @Nullable String getDefaultValueFunc() {
        if (defaultValue == null) {
            return null;
        }
        return "(" + type + ")" + defaultValue;
    }

    @Override
    public @NotNull ParameterEntityDef getDefinedIn() {
        return definedIn;
    }

    @Override
    public @NotNull String toString() {
        return "ExtensionFunctionArgument{" +
                "defaultValue='" + defaultValue + '\'' +
                ", type='" + type + '\'' +
                ", name='" + name + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ExtensionFunctionArgument that)) return false;
        return Objects.equals(name, that.name) && Objects.equals(type, that.type) && Objects.equals(defaultValue, that.defaultValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, defaultValue);
    }
}
