import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    java
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.sonarqube) apply false
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

    // ─── SonarQube + JaCoCo ────────────────────────────────────────────
    // `common-test` y `tests:e2e` no son código de producción: quedan fuera
    // del análisis estático y de la cobertura. Los proyectos "grouper"
    // (:libs, :services, :tests) tampoco: se analiza cada módulo con
    // `src/main/java` como un proyecto independiente en SonarQube
    // (projectKey = nombre). Desde la raíz, la tarea `sonarAll` dispara
    // el análisis de todos los módulos de una vez.
    val sonarExcluded = setOf("common-test", "e2e")
    val hasMainSources = layout.projectDirectory.dir("src/main/java").asFile.isDirectory
    if (name !in sonarExcluded && hasMainSources) {
        apply(plugin = "jacoco")
        apply(plugin = "org.sonarqube")

        configure<JacocoPluginExtension> {
            toolVersion = "0.8.13"
        }

        tasks.withType<JacocoReport>().configureEach {
            reports {
                xml.required.set(true) // SonarQube consume el XML de JaCoCo
                html.required.set(false)
            }
        }

        tasks.withType<Test>().configureEach {
            finalizedBy(tasks.named("jacocoTestReport"))
        }

        val hasTestSources = layout.projectDirectory.dir("src/test/java").asFile.isDirectory
        configure<org.sonarqube.gradle.SonarExtension> {
            properties {
                // El plugin 7.x por defecto apunta a SonarCloud; apuntarlo al server local
                // (override con SONAR_HOST_URL para otros entornos).
                property("sonar.host.url", System.getenv("SONAR_HOST_URL") ?: "http://localhost:9000")
                property("sonar.projectKey", name)
                property("sonar.projectName", name)
                property("sonar.sources", layout.projectDirectory.dir("src/main/java").asFile.absolutePath)
                if (hasTestSources) {
                    property("sonar.tests", layout.projectDirectory.dir("src/test/java").asFile.absolutePath)
                    property(
                        "sonar.coverage.jacoco.xmlReportPaths",
                        layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml").get().asFile.absolutePath,
                    )
                }
                property("sonar.java.binaries", layout.buildDirectory.dir("classes/java/main").get().asFile.absolutePath)
                property(
                    "sonar.java.libraries",
                    // FileCollection: el plugin la expande a paths separados por coma.
                    // Un String con `File.pathSeparator` (o `.asPath`) rompe la serialización
                    // a URI ("Illegal char <:>") en Windows.
                    project.files(
                        *project.configurations["compileClasspath"].files.toTypedArray(),
                        *project.configurations["runtimeClasspath"].files.toTypedArray(),
                    ),
                )
            }
        }
    }
}

// Dispara el análisis SonarQube de todos los módulos backend con un solo
// comando:  gradlew sonarAll
// (el plugin registra `sonar` por módulo; esta tarea encadena los 7 de una vez).
tasks.register("sonarAll") {
    group = "verification"
    description = "Ejecuta el análisis SonarQube de todos los módulos backend (4 services + 3 libs)."
    dependsOn(
        ":libs:common-domain:sonar",
        ":libs:common-kafka:sonar",
        ":libs:common-security:sonar",
        ":services:ai-engine-service:sonar",
        ":services:core-domain-service:sonar",
        ":services:gateway-service:sonar",
        ":services:identity-service:sonar",
    )
}
