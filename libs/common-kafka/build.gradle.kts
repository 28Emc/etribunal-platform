dependencies {
    api(project(":libs:common-domain"))
    api(libs.spring.kafka)

    testImplementation(libs.spring.boot.starter.test)
}
