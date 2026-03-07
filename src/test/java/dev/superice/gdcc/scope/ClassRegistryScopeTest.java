package dev.superice.gdcc.scope;

import dev.superice.gdcc.gdextension.ExtensionAPI;
import dev.superice.gdcc.gdextension.ExtensionGdClass;
import dev.superice.gdcc.gdextension.ExtensionGlobalEnum;
import dev.superice.gdcc.gdextension.ExtensionHeader;
import dev.superice.gdcc.gdextension.ExtensionSingleton;
import dev.superice.gdcc.gdextension.ExtensionUtilityFunction;
import dev.superice.gdcc.gdextension.ExtensionEnumValue;
import dev.superice.gdcc.lir.LirClassDef;
import dev.superice.gdcc.type.GdDictionaryType;
import dev.superice.gdcc.type.GdIntType;
import dev.superice.gdcc.type.GdObjectType;
import dev.superice.gdcc.type.GdStringType;
import dev.superice.gdcc.type.GdVariantType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ClassRegistryScopeTest {
    @Test
    void classRegistryRemainsGlobalRootScope() {
        var registry = new ClassRegistry(createScopeFixtureApi());

        assertNull(registry.getParentScope());
        assertDoesNotThrow(() -> registry.setParentScope(null));
        assertNull(registry.getParentScope());
        assertThrows(IllegalArgumentException.class, () -> registry.setParentScope(new ParentScopeStub()));
    }

    @Test
    void resolveValueAndFunctionsExposeGlobalBindings() {
        var registry = new ClassRegistry(createScopeFixtureApi());

        var singletonValue = registry.resolveValue("GameSingleton");
        assertNotNull(singletonValue);
        assertEquals(ScopeValueKind.SINGLETON, singletonValue.kind());
        assertEquals("Node", singletonValue.type().getTypeName());
        assertTrue(singletonValue.constant());
        assertInstanceOf(ExtensionSingleton.class, singletonValue.declaration());

        var enumValue = registry.resolveValue("GameFlags");
        assertNotNull(enumValue);
        assertEquals(ScopeValueKind.GLOBAL_ENUM, enumValue.kind());
        assertEquals(GdIntType.INT, enumValue.type());
        assertTrue(enumValue.constant());
        assertInstanceOf(ExtensionGlobalEnum.class, enumValue.declaration());

        var utilityFunctions = registry.resolveFunctions("print_line");
        assertEquals(1, utilityFunctions.size());
        var utilityFunction = assertInstanceOf(ExtensionUtilityFunction.class, utilityFunctions.getFirst());
        assertEquals("print_line", utilityFunction.getName());
        assertEquals(GdStringType.STRING, utilityFunction.getReturnType());

        assertNull(registry.resolveValue("print_line"));
        assertTrue(registry.resolveFunctions("GameSingleton").isEmpty());
        assertNull(registry.resolveTypeMeta("print_line"));
    }

    @Test
    void resolveTypeMetaUsesStrictGlobalTypeNamespace() {
        var registry = new ClassRegistry(createScopeFixtureApi());
        registry.addGdccClass(new LirClassDef("InventoryItem", "Object"));

        var builtinMeta = registry.resolveTypeMeta("String");
        assertNotNull(builtinMeta);
        assertEquals(ScopeTypeMetaKind.BUILTIN, builtinMeta.kind());
        assertEquals(GdStringType.STRING, builtinMeta.instanceType());

        var engineMeta = registry.resolveTypeMeta("Node");
        assertNotNull(engineMeta);
        assertEquals(ScopeTypeMetaKind.ENGINE_CLASS, engineMeta.kind());
        var engineType = assertInstanceOf(GdObjectType.class, engineMeta.instanceType());
        assertEquals("Node", engineType.getTypeName());

        var gdccMeta = registry.resolveTypeMeta("InventoryItem");
        assertNotNull(gdccMeta);
        assertEquals(ScopeTypeMetaKind.GDCC_CLASS, gdccMeta.kind());
        assertEquals("InventoryItem", gdccMeta.instanceType().getTypeName());

        var enumMeta = registry.resolveTypeMeta("GameFlags");
        assertNotNull(enumMeta);
        assertEquals(ScopeTypeMetaKind.GLOBAL_ENUM, enumMeta.kind());
        assertEquals(GdIntType.INT, enumMeta.instanceType());
        assertTrue(enumMeta.pseudoType());

        var dictionaryMeta = registry.resolveTypeMeta("Dictionary[String, InventoryItem]");
        assertNotNull(dictionaryMeta);
        assertEquals(ScopeTypeMetaKind.BUILTIN, dictionaryMeta.kind());
        var dictionaryType = assertInstanceOf(GdDictionaryType.class, dictionaryMeta.instanceType());
        assertEquals(GdStringType.STRING, dictionaryType.getKeyType());
        assertEquals("InventoryItem", dictionaryType.getValueType().getTypeName());
    }

    @Test
    void sameNameCanResolveIndependentlyInValueAndTypeNamespaces() {
        var registry = new ClassRegistry(createScopeFixtureApi());
        registry.addGdccClass(new LirClassDef("SharedSymbol", "Object"));

        var valueBinding = registry.resolveValue("SharedSymbol");
        assertNotNull(valueBinding);
        assertEquals(ScopeValueKind.SINGLETON, valueBinding.kind());
        assertEquals("Node", valueBinding.type().getTypeName());
        assertNotSame(valueBinding.kind(), ScopeValueKind.TYPE_META);

        var typeBinding = registry.resolveTypeMeta("SharedSymbol");
        assertNotNull(typeBinding);
        assertEquals(ScopeTypeMetaKind.GDCC_CLASS, typeBinding.kind());
        assertEquals("SharedSymbol", typeBinding.instanceType().getTypeName());

        assertNull(registry.resolveValue("MissingSymbol"));
        assertTrue(registry.resolveFunctions("MissingSymbol").isEmpty());
        assertNull(registry.resolveTypeMeta("MissingSymbol"));
    }

    private static @NotNull ExtensionAPI createScopeFixtureApi() {
        return new ExtensionAPI(
                new ExtensionHeader(4, 4, 0, "stable", "test", "test", "single"),
                List.of(),
                List.of(),
                List.of(new ExtensionGlobalEnum("GameFlags", false, List.of(new ExtensionEnumValue("READY", 1)))),
                List.of(new ExtensionUtilityFunction("print_line", "String", "debug", false, 1, List.of())),
                List.of(),
                List.of(new ExtensionGdClass("Node", false, true, "Object", "core", List.of(), List.of(), List.of(), List.of(), List.of())),
                List.of(
                        new ExtensionSingleton("GameSingleton", "Node"),
                        new ExtensionSingleton("SharedSymbol", "Node")
                ),
                List.of()
        );
    }

    private static final class ParentScopeStub implements Scope {
        @Override
        public @Nullable Scope getParentScope() {
            return null;
        }

        @Override
        public void setParentScope(@Nullable Scope parentScope) {
        }

        @Override
        public @Nullable ScopeValue resolveValueHere(@NotNull String name) {
            return new ScopeValue(name, GdVariantType.VARIANT, ScopeValueKind.LOCAL, null, false, false);
        }

        @Override
        public @NotNull List<? extends FunctionDef> resolveFunctionsHere(@NotNull String name) {
            return List.of();
        }

        @Override
        public @Nullable ScopeTypeMeta resolveTypeMetaHere(@NotNull String name) {
            return null;
        }
    }
}
