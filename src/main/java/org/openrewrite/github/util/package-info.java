/*
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Utility interfaces for GitHub Actions YAML processing.
 *
 * <p>This package contains reusable utility interfaces that provide common patterns for working with
 * GitHub Actions workflow YAML files. These interfaces use default methods to provide mixins that
 * can be implemented by recipe visitors.</p>
 *
 * <h2>Available Utilities</h2>
 * <ul>
 *   <li>{@link org.openrewrite.github.util.YamlScalarAccessor} - Safe extraction of
 *       scalar values from YAML LST elements</li>
 * </ul>
 *
 * <h2>Using Utility Interfaces</h2>
 * <p>These interfaces are designed to be implemented by recipe visitors to provide reusable
 * functionality through default interface methods:</p>
 *
 * <pre>
 * private static class MyVisitor extends YamlIsoVisitor&lt;ExecutionContext&gt;
 *         implements YamlScalarAccessor {
 *
 *     {@literal @}Override
 *     public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
 *         String value = getScalarValue(entry.getValue());
 *         if (value != null) {
 *             // Process the scalar value
 *         }
 *         return super.visitMappingEntry(entry, ctx);
 *     }
 * }
 * </pre>
 *
 * <h2>Design Philosophy</h2>
 * <p>Utility interfaces in this package follow these principles:</p>
 * <ul>
 *   <li><strong>Composability</strong> - Multiple interfaces can be combined in a single visitor</li>
 *   <li><strong>Java 8 Compatibility</strong> - All code uses Java 8 syntax only</li>
 *   <li><strong>Null Safety</strong> - Methods handle null inputs gracefully using {@code @Nullable}</li>
 *   <li><strong>Documentation</strong> - Comprehensive JavaDoc with real-world examples</li>
 *   <li><strong>Testability</strong> - Utilities can be tested independently</li>
 * </ul>
 *
 * @see org.openrewrite.github.util.YamlScalarAccessor
 */
package org.openrewrite.github.util;
