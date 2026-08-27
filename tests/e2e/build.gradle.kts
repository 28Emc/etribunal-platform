plugins {
    `java-library`
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.3")
}

tasks.jar {
    enabled = false
}

tasks.test {
    useJUnitPlatform()
    systemProperty("junit.jupiter.execution.timeout.default", "300s")
    onlyIf { System.getProperty("e2e.enabled") == "true" }
}
