plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(libs.spring.cloud.starter.gateway)
    implementation(libs.spring.boot.starter.actuator)

    testImplementation(libs.spring.boot.starter.test)
}

// Solo interesa el fat-jar ejecutable (bootJar) — evita *.jar ambiguo en el Dockerfile
tasks.jar {
    enabled = false
}
