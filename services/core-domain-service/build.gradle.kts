buildscript {
    dependencies {
        classpath("org.flywaydb:flyway-core:11.8.2")
        classpath("org.flywaydb:flyway-database-postgresql:11.8.2")
        classpath("org.postgresql:postgresql:42.7.3")
    }
}

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
    implementation(libs.spring.boot.starter.mail)
    implementation(libs.spring.kafka)

    // OpenTelemetry tracing
    implementation(libs.micrometer.tracing.bridge.brave)
    implementation(libs.zipkin.reporter.brave)
    implementation(libs.otel.exporter.zipkin)
    implementation(libs.springdoc.openapi.starter.webmvc.ui)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.aws.s3)

    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(project(":libs:common-test"))
}

tasks.register("flywayMigrate") {
    group = "flyway"
    description = "Ejecuta migraciones Flyway en la base de datos core"
    dependsOn("processResources") // Asegura que los recursos estén copiados
    doLast {
        val buildResourcesDir = project.layout.buildDirectory.dir("resources/main").get().asFile.absolutePath
        val flyway = org.flywaydb.core.Flyway.configure()
            .dataSource(
                "jdbc:postgresql://${System.getProperty("FLOCI_HOST", "localhost")}:${System.getProperty("FLOCI_CORE_PORT", "7003")}/etribunal_core",
                "etribunal_user",
                "etribunal_pass"
            )
            .locations("filesystem:$buildResourcesDir/db/migration")
            .load()
        flyway.migrate()
    }
}

tasks.register("flywayClean") {
    group = "flyway"
    description = "Limpia la base de datos core (¡cuidado: borra todo!)"
    doLast {
        val flyway = org.flywaydb.core.Flyway.configure()
            .dataSource(
                "jdbc:postgresql://${System.getProperty("FLOCI_HOST", "localhost")}:${System.getProperty("FLOCI_CORE_PORT", "7003")}/etribunal_core",
                "etribunal_user",
                "etribunal_pass"
            )
            .cleanDisabled(false) // Habilitar clean
            .load()
        flyway.clean()
    }
}

// Solo interesa el fat-jar ejecutable (bootJar) — evita *.jar ambiguo en el Dockerfile
tasks.jar {
    enabled = false
}
