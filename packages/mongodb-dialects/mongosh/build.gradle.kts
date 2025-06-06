plugins {
    id("com.mongodb.intellij.isolated-module")
}

dependencies {
    implementation(libs.bson.kotlin)
    implementation(libs.owasp.encoder)
    implementation(libs.semver.parser)

    implementation(project(":packages:mongodb-mql-model"))
    implementation(project(":packages:mongodb-mql-engines"))
    implementation(project(":packages:mongodb-dialects"))
}
