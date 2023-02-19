plugins {
    id("org.openrewrite.build.recipe-library") version "1.8.2"
}

group = "org.openrewrite.recipe"
description = "An OpenRewrite module automating best practices and migrations for GitHub Actions"

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
    implementation("org.openrewrite:rewrite-yaml:${rewriteVersion}")
    runtimeOnly("com.fasterxml.jackson.core:jackson-core")
}
