plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "etribunal-platform"

include(
    "libs:common-domain",
    "libs:common-security",
    "libs:common-kafka",
    "libs:common-test",
    "services:gateway-service",
    "services:identity-service",
    "services:core-domain-service",
    "services:ai-engine-service",
)
