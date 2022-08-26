/*
 * Copyright 2022 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.cleanup;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.function.Supplier;

public class AssertEqualsNullToAssertNull extends Recipe {
    private static final MethodMatcher ASSERT_EQUALS = new MethodMatcher(
            "org.junit.jupiter.api.Assertions assertEquals(..)");

    @Override
    public String getDisplayName() {
        return "`assertEquals(a, null)` to `assertNull(a)`";
    }

    @Override
    public String getDescription() {
        return "Using `assertNull(a)` is simpler and more clear.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesMethod<>(ASSERT_EQUALS);
    }

    @Override
    protected JavaVisitor<ExecutionContext> getVisitor() {

        return new JavaIsoVisitor<ExecutionContext>() {
            final Supplier<JavaParser> javaParser = () -> JavaParser.fromJavaVersion()
                    //language=java
                    .dependsOn("" +
                            "package org.junit.jupiter.api;" +
                            "import java.util.function.Supplier;" +
                            "public class Assertions {" +
                            "  public static void assertNull(Object actual) {}" +
                            "  public static void assertNull(Object actual, String message) {}" +
                            "  public static void assertNull(Object actual, Supplier<String> messageSupplier) {}" +
                            "}")
                    .build();

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (ASSERT_EQUALS.matches(method)) {
                    if (!(mi.getArguments().get(0).getType() == JavaType.Primitive.Null || mi.getArguments().get(1).getType() == JavaType.Primitive.Null)) {
                        return mi;
                    }
                    StringBuilder sb = new StringBuilder();
                    Object[] args;
                    if (mi.getSelect() != null) {
                        sb.append("Assertions.");
                    }
                    sb.append("assertNull(#{any(java.lang.Object)}");
                    if (mi.getArguments().size() == 3) {
                        sb.append(", #{any()}");
                        args = new Object[]{(isNullLiteral(mi.getArguments().get(0)) ? mi.getArguments().get(1) : mi.getArguments().get(0)), mi.getArguments().get(2)};
                    } else {
                        args = new Object[]{(isNullLiteral(mi.getArguments().get(0)) ? mi.getArguments().get(1) : mi.getArguments().get(0))};
                    }
                    sb.append(")");
                    JavaTemplate t;
                    if(method.getSelect() == null) {
                        maybeRemoveImport("org.junit.jupiter.api.Assertions");
                        maybeAddImport("org.junit.jupiter.api.Assertions", "assertNull");
                        t = JavaTemplate.builder(this::getCursor, sb.toString()).javaParser(javaParser)
                                .staticImports("org.junit.jupiter.api.Assertions.assertNull").build();
                    } else {
                        t = JavaTemplate.builder(this::getCursor, sb.toString()).javaParser(javaParser)
                                .imports("org.junit.jupiter.api.Assertions.assertNull").build();
                    }
                    mi = mi.withTemplate(t, mi.getCoordinates().replace(), args);
                }
                return mi;
            }

            private boolean isNullLiteral(Expression expr) {
                return expr instanceof J.Literal && ((J.Literal) expr).getValue() == null;
            }
        };
    }
}
