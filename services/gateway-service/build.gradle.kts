plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(libs.spring.cloud.starter.gateway)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.redis)

    // OpenTelemetry tracing
    implementation(libs.micrometer.tracing.bridge.brave)
    implementation(libs.zipkin.reporter.brave)
    implementation(libs.otel.exporter.zipkin)
    implementation(libs.springdoc.openapi.starter.webflux.ui)
    implementation(project(":libs:common-security"))

    testImplementation(libs.spring.boot.starter.test)
    testImplementation("io.projectreactor:reactor-test")
}

// Solo interesa el fat-jar ejecutable (bootJar) - evita *.jar ambiguo en el Dockerfile
tasks.jar {
    enabled = false
}
