package dev.superice.gdcc.frontend.lowering.pass;

import dev.superice.gdcc.frontend.lowering.FrontendLoweringContext;
import dev.superice.gdcc.frontend.lowering.FrontendLoweringPass;
import dev.superice.gdcc.frontend.lowering.FunctionLoweringContext;
import dev.superice.gdcc.lir.LirModule;
import dev.superice.gdparser.frontend.ast.Block;
import org.jetbrains.annotations.NotNull;

/// Freezes the first CFG-pass contract without materializing any block skeleton yet.
///
/// This step only validates that later CFG work will see compile-ready executable lowering units
/// that already crossed the compile gate and therefore satisfy the current compile surface.
public final class FrontendLoweringCfgPass implements FrontendLoweringPass {
    @Override
    public void run(@NotNull FrontendLoweringContext context) {
        var analysisData = context.requireAnalysisData();
        var lirModule = context.requireLirModule();
        var functionLoweringContexts = context.requireFunctionLoweringContexts();

        for (var functionContext : functionLoweringContexts) {
            if (functionContext.analysisData() != analysisData) {
                throw new IllegalStateException("Function lowering context must reuse the published analysis snapshot");
            }
            validateTargetFunctionMembership(functionContext, lirModule);
            switch (functionContext.kind()) {
                case EXECUTABLE_BODY -> validateExecutableContext(functionContext);
                case PROPERTY_INIT -> validatePropertyInitContext(functionContext);
                case PARAMETER_DEFAULT_INIT -> throw new IllegalStateException(
                        "CFG pass does not support parameter default initializer contexts yet"
                );
            }
        }
    }

    private void validateExecutableContext(@NotNull FunctionLoweringContext functionContext) {
        if (!(functionContext.sourceOwner() instanceof dev.superice.gdparser.frontend.ast.FunctionDeclaration)
                && !(functionContext.sourceOwner() instanceof dev.superice.gdparser.frontend.ast.ConstructorDeclaration)) {
            throw new IllegalStateException(describeContext(functionContext) + " must keep a callable declaration as sourceOwner");
        }
        if (!(functionContext.loweringRoot() instanceof Block)) {
            throw new IllegalStateException(describeContext(functionContext) + " must expose a Block loweringRoot");
        }
        validateShellOnlyTarget(functionContext);
        // Unsupported executable subtrees are fenced off by the compile gate before lowering
        // starts, so the CFG pass only relies on that published invariant instead of re-scanning
        // the AST for the same blocked-node categories here.
    }

    private void validatePropertyInitContext(@NotNull FunctionLoweringContext functionContext) {
        validateShellOnlyTarget(functionContext);
    }

    private void validateTargetFunctionMembership(
            @NotNull FunctionLoweringContext functionContext,
            @NotNull LirModule lirModule
    ) {
        if (lirModule.getClassDefs().stream().noneMatch(classDef -> classDef == functionContext.owningClass())) {
            throw new IllegalStateException(
                    describeContext(functionContext) + " references an owningClass outside the published LIR module"
            );
        }
        if (functionContext.owningClass().getFunctions().stream().noneMatch(function -> function == functionContext.targetFunction())) {
            throw new IllegalStateException(
                    describeContext(functionContext) + " references a targetFunction outside the owning LIR class"
            );
        }
    }

    private void validateShellOnlyTarget(@NotNull FunctionLoweringContext functionContext) {
        if (functionContext.targetFunction().getBasicBlockCount() != 0
                || !functionContext.targetFunction().getEntryBlockId().isEmpty()) {
            throw new IllegalStateException(
                    describeContext(functionContext) + " must remain shell-only before CFG skeleton materialization"
            );
        }
    }

    private static @NotNull String describeContext(@NotNull FunctionLoweringContext functionContext) {
        return "Function lowering context "
                + functionContext.kind()
                + " "
                + functionContext.owningClass().getName()
                + "."
                + functionContext.targetFunction().getName();
    }
}
