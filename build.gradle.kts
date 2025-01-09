plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "An OpenRewrite module automating best practices and migrations for GitHub Actions"

val rewriteVersion = "latest.release"
dependencies {
    implementation("org.openrewrite:rewrite-yaml:8.41.1")
    runtimeOnly("com.fasterxml.jackson.core:jackson-core")
    testImplementation("org.openrewrite:rewrite-test:8.41.1")
}
