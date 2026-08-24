dependencies {
    api(project(":libs:common-domain"))
    api(libs.nimbus.jose.jwt)

    testImplementation(libs.spring.boot.starter.test)
}
