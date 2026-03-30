package dev.superice.gdcc.frontend.lowering.cfg.item;

import dev.superice.gdparser.frontend.ast.Node;
import dev.superice.gdparser.frontend.ast.Statement;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/// Pure source anchor with no operand list and no published value id.
///
/// This item exists for statements that must remain visible in the frontend CFG for diagnostics,
/// lexical ordering, or future lowering hooks, but do not themselves evaluate a value or commit
/// state. `pass` is the canonical example.
public record SourceAnchorItem(@NotNull Statement statement) implements SequenceItem {
    public SourceAnchorItem {
        Objects.requireNonNull(statement, "statement must not be null");
    }

    @Override
    public @NotNull Node anchor() {
        return statement;
    }
}
