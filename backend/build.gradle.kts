plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.mendoncagalvao"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Web / JSON
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Persistence
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Jackson JSR-310 (java.time) support comes with the web starter.

    // Export XLSX
    implementation("org.apache.poi:poi-ooxml:5.3.0")

    // Storage S3/MinIO (produção)
    implementation(platform("software.amazon.awssdk:bom:2.28.16"))
    implementation("software.amazon.awssdk:s3")

    // Tests
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.wiremock:wiremock-standalone:3.9.2")
    testImplementation("org.testcontainers:junit-jupiter:1.20.3")
    testImplementation("org.testcontainers:postgresql:1.20.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // docker-java assume por padrão a API 1.32; engines atuais exigem >= 1.40.
    // A estratégia que respeita DOCKER_API_VERSION é a baseada em DOCKER_HOST, então
    // repassamos ambos (do ambiente do Gradle) para o JVM de teste do Testcontainers.
    environment("DOCKER_API_VERSION", System.getenv("DOCKER_API_VERSION") ?: "1.43")
    System.getenv("DOCKER_HOST")?.let { environment("DOCKER_HOST", it) }
}
