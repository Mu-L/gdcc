package dev.superice.gdcc.frontend.sema.analyzer;

import dev.superice.gdcc.frontend.diagnostic.DiagnosticManager;
import dev.superice.gdcc.frontend.parse.GdScriptParserService;
import dev.superice.gdcc.frontend.sema.FrontendExpressionTypeStatus;
import dev.superice.gdcc.gdextension.ExtensionApiLoader;
import dev.superice.gdcc.scope.ClassRegistry;
import dev.superice.gdparser.frontend.ast.AttributeExpression;
import dev.superice.gdparser.frontend.ast.ExpressionStatement;
import dev.superice.gdparser.frontend.ast.FunctionDeclaration;
import dev.superice.gdparser.frontend.ast.IdentifierExpression;
import dev.superice.gdparser.frontend.ast.LiteralExpression;
import dev.superice.gdparser.frontend.ast.Node;
import dev.superice.gdparser.frontend.ast.SelfExpression;
import dev.superice.gdcc.type.GdVariantType;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class FrontendExprTypeAnalyzerTest {
    @Test
    void analyzePublishesResolvedAtomicAndChainExpressionTypes() throws Exception {
        var analyzed = analyze(
                "expr_type_resolved.gd",
                """
                        class_name ExprTypeResolved
                        extends Node
                        
                        var payload: int = 1
                        
                        class Worker:
                            static func build(seed) -> String:
                                return ""
                        
                        func ping(seed):
                            1
                            self
                            seed
                            self.payload
                            Worker.build(seed)
                        """
        );

        var pingFunction = findFunction(analyzed.ast(), "ping");
        var literalStatement = assertInstanceOf(ExpressionStatement.class, pingFunction.body().statements().getFirst());
        var selfStatement = assertInstanceOf(ExpressionStatement.class, pingFunction.body().statements().get(1));
        var seedStatement = assertInstanceOf(ExpressionStatement.class, pingFunction.body().statements().get(2));
        var payloadStatement = assertInstanceOf(ExpressionStatement.class, pingFunction.body().statements().get(3));
        var buildStatement = assertInstanceOf(ExpressionStatement.class, pingFunction.body().statements().get(4));

        var literal = assertInstanceOf(LiteralExpression.class, literalStatement.expression());
        var selfExpression = assertInstanceOf(SelfExpression.class, selfStatement.expression());
        var seedIdentifier = assertInstanceOf(IdentifierExpression.class, seedStatement.expression());
        var payloadExpression = assertInstanceOf(AttributeExpression.class, payloadStatement.expression());
        var buildExpression = assertInstanceOf(AttributeExpression.class, buildStatement.expression());

        var literalType = analyzed.analysisData().expressionTypes().get(literal);
        assertNotNull(literalType);
        assertEquals(FrontendExpressionTypeStatus.RESOLVED, literalType.status());
        assertEquals("int", literalType.publishedType().getTypeName());

        var selfType = analyzed.analysisData().expressionTypes().get(selfExpression);
        assertNotNull(selfType);
        assertEquals(FrontendExpressionTypeStatus.RESOLVED, selfType.status());
        assertEquals("ExprTypeResolved", selfType.publishedType().getTypeName());

        var seedType = analyzed.analysisData().expressionTypes().get(seedIdentifier);
        assertNotNull(seedType);
        assertEquals(FrontendExpressionTypeStatus.RESOLVED, seedType.status());
        assertEquals("Variant", seedType.publishedType().getTypeName());

        var payloadType = analyzed.analysisData().expressionTypes().get(payloadExpression);
        assertNotNull(payloadType);
        assertEquals(FrontendExpressionTypeStatus.RESOLVED, payloadType.status());
        assertEquals("int", payloadType.publishedType().getTypeName());

        var buildType = analyzed.analysisData().expressionTypes().get(buildExpression);
        assertNotNull(buildType);
        assertEquals(FrontendExpressionTypeStatus.RESOLVED, buildType.status());
        assertEquals("String", buildType.publishedType().getTypeName());
    }

    @Test
    void analyzeDistinguishesExactVariantFromDynamicVariantDegradation() throws Exception {
        var analyzed = analyze(
                "expr_type_dynamic.gd",
                """
                        class_name DynamicRoute
                        extends RefCounted
                        
                        func ping(worker):
                            worker
                            worker.ping().length
                        """
        );

        var pingFunction = findFunction(analyzed.ast(), "ping");
        var variantStatement = assertInstanceOf(ExpressionStatement.class, pingFunction.body().statements().getFirst());
        var dynamicStatement = assertInstanceOf(ExpressionStatement.class, pingFunction.body().statements().get(1));

        var workerIdentifier = assertInstanceOf(IdentifierExpression.class, variantStatement.expression());
        var dynamicExpression = assertInstanceOf(AttributeExpression.class, dynamicStatement.expression());

        var exactVariantType = analyzed.analysisData().expressionTypes().get(workerIdentifier);
        assertNotNull(exactVariantType);
        assertEquals(FrontendExpressionTypeStatus.RESOLVED, exactVariantType.status());
        assertEquals(GdVariantType.VARIANT, exactVariantType.publishedType());

        var dynamicType = analyzed.analysisData().expressionTypes().get(dynamicExpression);
        assertNotNull(dynamicType);
        assertEquals(FrontendExpressionTypeStatus.DYNAMIC, dynamicType.status());
        assertEquals(GdVariantType.VARIANT, dynamicType.publishedType());
        assertNotNull(dynamicType.detailReason());
    }

    @Test
    void analyzeUsesDynamicArgumentVariantToKeepOuterCallResolvable() throws Exception {
        var analyzed = analyze(
                "expr_type_dynamic_arg.gd",
                """
                        class_name DynamicArgumentRoute
                        extends RefCounted
                        
                        func consume(value) -> int:
                            return 1
                        
                        func ping(worker):
                            self.consume(worker.ping())
                        """
        );

        var pingFunction = findFunction(analyzed.ast(), "ping");
        var consumeStatement = assertInstanceOf(ExpressionStatement.class, pingFunction.body().statements().getFirst());
        var consumeExpression = assertInstanceOf(AttributeExpression.class, consumeStatement.expression());
        var innerDynamic = findNode(
                consumeExpression,
                AttributeExpression.class,
                candidate -> candidate != consumeExpression
        );

        var innerType = analyzed.analysisData().expressionTypes().get(innerDynamic);
        assertNotNull(innerType);
        assertEquals(FrontendExpressionTypeStatus.DYNAMIC, innerType.status());
        assertEquals(GdVariantType.VARIANT, innerType.publishedType());

        var outerType = analyzed.analysisData().expressionTypes().get(consumeExpression);
        assertNotNull(outerType);
        assertEquals(
                FrontendExpressionTypeStatus.RESOLVED,
                outerType.status(),
                outerType.detailReason()
        );
        assertEquals("int", outerType.publishedType().getTypeName());
    }

    @Test
    void analyzePreservesBlockedStatusAcrossNestedArgumentDependency() throws Exception {
        var analyzed = analyze(
                "expr_type_blocked_arg.gd",
                """
                        class_name BlockedArgumentRoute
                        extends RefCounted
                        
                        class Target:
                            func consume(value) -> int:
                                return 1
                        
                        var payload: int = 1
                        
                        static func ping(target: Target):
                            self.payload
                            target.consume(self.payload)
                        """
        );

        var pingFunction = findFunction(analyzed.ast(), "ping");
        var blockedReadStatement = assertInstanceOf(ExpressionStatement.class, pingFunction.body().statements().getFirst());
        var blockedCallStatement = assertInstanceOf(ExpressionStatement.class, pingFunction.body().statements().get(1));

        var blockedRead = assertInstanceOf(AttributeExpression.class, blockedReadStatement.expression());
        var blockedCall = assertInstanceOf(AttributeExpression.class, blockedCallStatement.expression());

        var blockedReadType = analyzed.analysisData().expressionTypes().get(blockedRead);
        assertNotNull(blockedReadType);
        assertEquals(FrontendExpressionTypeStatus.BLOCKED, blockedReadType.status());
        assertNull(blockedReadType.publishedType());

        var blockedCallType = analyzed.analysisData().expressionTypes().get(blockedCall);
        assertNotNull(blockedCallType);
        assertEquals(FrontendExpressionTypeStatus.BLOCKED, blockedCallType.status());
        assertNull(blockedCallType.publishedType());
        assertNotNull(blockedCallType.detailReason());
    }

    private static @NotNull AnalyzedScript analyze(
            @NotNull String fileName,
            @NotNull String source
    ) throws Exception {
        var diagnostics = new DiagnosticManager();
        var parserService = new GdScriptParserService();
        var unit = parserService.parseUnit(Path.of("tmp", fileName), source, diagnostics);
        var analysisData = new FrontendSemanticAnalyzer().analyze(
                "test_module",
                List.of(unit),
                new ClassRegistry(ExtensionApiLoader.loadDefault()),
                diagnostics
        );
        return new AnalyzedScript(unit.ast(), analysisData);
    }

    private static @NotNull FunctionDeclaration findFunction(@NotNull Node root, @NotNull String name) {
        return findNode(root, FunctionDeclaration.class, function -> function.name().equals(name));
    }

    private static <T extends Node> @NotNull T findNode(
            @NotNull Node root,
            @NotNull Class<T> nodeType,
            @NotNull Predicate<T> predicate
    ) {
        return findNodes(root, nodeType, predicate).stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("Node not found: " + nodeType.getSimpleName()));
    }

    private static <T extends Node> @NotNull List<T> findNodes(
            @NotNull Node root,
            @NotNull Class<T> nodeType,
            @NotNull Predicate<T> predicate
    ) {
        var matches = new ArrayList<T>();
        collectMatchingNodes(root, nodeType, predicate, matches);
        return List.copyOf(matches);
    }

    private static <T extends Node> void collectMatchingNodes(
            @NotNull Node node,
            @NotNull Class<T> nodeType,
            @NotNull Predicate<T> predicate,
            @NotNull List<T> matches
    ) {
        if (nodeType.isInstance(node)) {
            var candidate = nodeType.cast(node);
            if (predicate.test(candidate)) {
                matches.add(candidate);
            }
        }
        for (var child : node.getChildren()) {
            collectMatchingNodes(child, nodeType, predicate, matches);
        }
    }

    private record AnalyzedScript(
            @NotNull Node ast,
            @NotNull dev.superice.gdcc.frontend.sema.FrontendAnalysisData analysisData
    ) {
    }
}
