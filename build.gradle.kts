plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "An OpenRewrite module automating best practices and migrations for GitHub Actions"

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
    implementation("org.openrewrite:rewrite-yaml:${rewriteVersion}")
    runtimeOnly("com.fasterxml.jackson.core:jackson-core")
}
