package gd.script.gdcc.frontend.lowering;

import gd.script.gdcc.frontend.sema.FrontendCallResolutionKind;
import gd.script.gdcc.frontend.sema.FrontendCallResolutionStatus;
import gd.script.gdcc.frontend.sema.FrontendReceiverKind;
import gd.script.gdcc.frontend.sema.FrontendResolvedCall;
import gd.script.gdcc.gdextension.ExtensionBuiltinClass;
import gd.script.gdcc.gdextension.ExtensionGdClass;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/// Shared receiver-mutability rule for lowering-ready instance calls.
///
/// Writable-route call lowering only needs one narrow question here:
/// - should this already-resolved instance call keep the post-call reverse-commit path available?
///
/// The answer intentionally stays conservative:
/// - gdextension metadata is trusted when it explicitly marks a method `const`
/// - every other declaration source is treated as may-mutate so frontend does not silently skip
///   receiver writeback for GDCC/user-defined methods or future metadata carriers without constness
/// - dynamic instance routes also count as may-mutate because runtime-open dispatch has no reliable
///   constness fact at lowering time;
///   a const-like method name such as `size` is not enough to prove immutability on a runtime-open route
///   frontend must therefore preserve direct-slot alias/writeback eligibility instead of letting a
///   potentially mutating dynamic call tunnel through a temp snapshot
public final class FrontendCallMutabilitySupport {
    private FrontendCallMutabilitySupport() {
    }

    public static boolean mayMutateReceiver(@NotNull FrontendResolvedCall resolvedCall) {
        var actualResolvedCall = Objects.requireNonNull(resolvedCall, "resolvedCall must not be null");
        if (actualResolvedCall.receiverKind() != FrontendReceiverKind.INSTANCE) {
            return false;
        }
        if (actualResolvedCall.status() == FrontendCallResolutionStatus.DYNAMIC) {
            return actualResolvedCall.callKind() == FrontendCallResolutionKind.DYNAMIC_FALLBACK;
        }
        if (actualResolvedCall.status() != FrontendCallResolutionStatus.RESOLVED
                || actualResolvedCall.callKind() != FrontendCallResolutionKind.INSTANCE_METHOD) {
            return false;
        }
        return switch (actualResolvedCall.declarationSite()) {
            case ExtensionBuiltinClass.ClassMethod builtinMethod -> !builtinMethod.isConst();
            case ExtensionGdClass.ClassMethod engineMethod -> !engineMethod.isConst();
            case null, default -> true;
        };
    }
}
