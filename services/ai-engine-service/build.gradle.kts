plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:1.1.8")
    }
}

dependencies {
    implementation(project(":libs:common-kafka"))

    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.postgresql)
    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.kafka)
    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    // Spring AI for Gemini (1.1.8 - compatible with Boot 3.5.x)
    implementation("org.springframework.ai:spring-ai-starter-model-vertex-ai-gemini")

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(project(":libs:common-test"))
    testRuntimeOnly("com.h2database:h2:2.2.224")
}

// Solo interesa el fat-jar ejecutable (bootJar) — evita *.jar ambiguo en el Dockerfile
tasks.jar {
    enabled = false
}
