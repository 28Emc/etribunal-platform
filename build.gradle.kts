import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    java
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.spring.boot) apply false
}

// Capturado fuera de subprojects{}: el catálogo libs no está registrado en los subproyectos.
val springBootVersion = libs.versions.springBoot.get()
val springCloudVersion = libs.versions.springCloud.get()

allprojects {
    group = "com.etribunal"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    // java-library habilita la configuración 'api' además de 'implementation'
    apply(plugin = "java-library")
    apply(plugin = "io.spring.dependency-management")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    configure<DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:$springCloudVersion")
        }
    }

    dependencies {
        // Gradle 9+ requiere el launcher explícito en el classpath de tests
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
    }
}
