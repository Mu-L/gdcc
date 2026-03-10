package dev.superice.gdcc.frontend.sema;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

/// Identity-based side table keyed by AST object identity instead of structural equality.
///
/// `gdparser` models most syntax nodes as `Node`, but `SourceFile` itself is not a `Node`.
/// This table intentionally accepts any AST object so later phases can attach facts to the
/// whole source file as well as ordinary statement/expression nodes.
///
/// The table now also implements `Map` so semantic phases can use ordinary map-style helpers
/// such as `containsKey`, `entrySet`, `keySet`, and bulk `putAll` operations while still
/// preserving identity-key semantics through the underlying `IdentityHashMap`.
public final class FrontendAstSideTable<V> extends AbstractMap<Object, V> {
    private final IdentityHashMap<Object, V> values = new IdentityHashMap<>();

    @Override
    public @Nullable V put(@NotNull Object astObject, @NotNull V value) {
        return values.put(
                requireAstObject(astObject),
                requireValue(value)
        );
    }

    @Override
    public @Nullable V get(Object astObject) {
        return values.get(requireAstObject(astObject));
    }

    public boolean contains(@NotNull Object astObject) {
        return containsKey(astObject);
    }

    @Override
    public boolean containsKey(Object astObject) {
        return values.containsKey(requireAstObject(astObject));
    }

    @Override
    public boolean containsValue(Object value) {
        return values.containsValue(requireValue(value));
    }

    public void putAll(@NotNull FrontendAstSideTable<? extends V> other) {
        Objects.requireNonNull(other, "other must not be null");
        values.putAll(other.values);
    }

    @Override
    public void putAll(@NotNull Map<?, ? extends V> other) {
        Objects.requireNonNull(other, "other must not be null");
        other.forEach(this::put);
    }

    @Override
    public @Nullable V remove(Object astObject) {
        return values.remove(requireAstObject(astObject));
    }

    @Override
    public void clear() {
        values.clear();
    }

    @Override
    public void forEach(@NotNull BiConsumer<? super Object, ? super V> consumer) {
        Objects.requireNonNull(consumer, "consumer must not be null");
        values.forEach(consumer);
    }

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public boolean isEmpty() {
        return values.isEmpty();
    }

    @Override
    public @NotNull Set<Object> keySet() {
        return values.keySet();
    }

    @Override
    public @NotNull Collection<V> values() {
        return values.values();
    }

    @Override
    public @NotNull Set<Entry<Object, V>> entrySet() {
        return values.entrySet();
    }

    private static @NotNull Object requireAstObject(@Nullable Object astObject) {
        return Objects.requireNonNull(astObject, "astObject must not be null");
    }

    private static <T> @NotNull T requireValue(@Nullable T value) {
        return Objects.requireNonNull(value, "value must not be null");
    }
}
