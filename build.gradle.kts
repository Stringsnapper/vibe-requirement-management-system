plugins {
    val kotlinVersion = "1.9.25"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.spring") version kotlinVersion
    kotlin("plugin.jpa") version kotlinVersion
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.6"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
    jacoco
}

group = "com.rms"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

// JPA entities written in Kotlin need a no-arg constructor and to be open.
// The kotlin-jpa plugin marks @Entity/@MappedSuperclass/@Embeddable classes open automatically.
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

// --- CI-1 quality gates (see CI-PLAN.md §5, §10.2) ---

jacoco {
    toolVersion = "0.8.12"
}

// Boilerplate excluded from the coverage denominator so the number is meaningful (§10.2):
// the application entry point, the data initializer, and Spring config classes.
val coverageExclusions =
    listOf(
        "com/rms/RmsApplicationKt.class",
        "com/rms/RmsApplication.class",
        "com/rms/bootstrap/**",
        "**/*Config.class",
    )

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it) { exclude(coverageExclusions) }
            },
        ),
    )
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it) { exclude(coverageExclusions) }
            },
        ),
    )
    violationRules {
        // Whole-repo floor — modest on day one, ratcheted upward per phase (§10.2).
        rule {
            limit {
                counter = "LINE"
                minimum = "0.60".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "0.45".toBigDecimal()
            }
        }
        // Compliance-critical core held to a higher bar.
        rule {
            element = "PACKAGE"
            includes = listOf("com.rms.domain", "com.rms.service", "com.rms.audit")
            limit {
                counter = "LINE"
                minimum = "0.75".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

ktlint {
    version = "1.3.1"
}
