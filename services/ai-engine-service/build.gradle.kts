plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":libs:common-kafka"))

    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.kafka)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(project(":libs:common-test"))
}

// Solo interesa el fat-jar ejecutable (bootJar) — evita *.jar ambiguo en el Dockerfile
tasks.jar {
    enabled = false
}
