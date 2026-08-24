plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":libs:common-domain"))
    implementation(project(":libs:common-security"))
    implementation(project(":libs:common-kafka"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.kafka)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)

    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(project(":libs:common-test"))
}

// Solo interesa el fat-jar ejecutable (bootJar) — evita *.jar ambiguo en el Dockerfile
tasks.jar {
    enabled = false
}
