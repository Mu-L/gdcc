package gd.script.gdcc.frontend.lowering.cfg.item;

import gd.script.gdcc.frontend.lowering.cfg.FrontendCfgGraph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class FrontendCfgItemSupport {
    private FrontendCfgItemSupport() {
    }

    static @Nullable String validateOptionalValueId(@Nullable String id, @NotNull String fieldName) {
        return id == null ? null : FrontendCfgGraph.validateValueId(id, fieldName);
    }

    static @NotNull List<String> copyValueIds(@Nullable List<String> ids, @NotNull String fieldName) {
        var source = Objects.requireNonNull(ids, fieldName + " must not be null");
        var copied = new ArrayList<String>(source.size());
        for (var index = 0; index < source.size(); index++) {
            copied.add(FrontendCfgGraph.validateValueId(source.get(index), fieldName + "[" + index + "]"));
        }
        return List.copyOf(copied);
    }
}
