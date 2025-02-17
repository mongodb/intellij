plugins {
    id("com.mongodb.intellij.plugin-component")
}

dependencies {
    implementation(project(":packages:mongodb-mql-engines"))
    implementation(project(":packages:mongodb-mql-model"))
    implementation(project(":packages:mongodb-dialects"))

    testImplementation(project(":packages:mongodb-mql-model"))
    testImplementation(libs.mongodb.driver)
}
