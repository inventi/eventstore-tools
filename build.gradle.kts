import org.gradle.api.plugins.JavaPlugin.JAR_TASK_NAME
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.plugin.SpringBootPlugin.BOOT_JAR_TASK_NAME

plugins {
    id("org.springframework.boot") version "3.0.1"
    id("io.spring.dependency-management") version "1.1.0"
    kotlin("jvm") version "1.8.0"
    kotlin("plugin.spring") version "1.8.0"
    `maven-publish`
}

group = "com.github.inventi"
version = "1.0-SNAPSHOT"

java {
    targetCompatibility = JavaVersion.VERSION_17
    sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    val jacksonVersion = "2.14.0"
    val testContainersVersion = "1.17.6"

    api("com.github.msemys:esjc:2.5.0")

    compileOnly("org.springframework.boot:spring-boot-starter-data-redis")

    implementation("io.zipkin.brave:brave:5.14.1")
    implementation("io.micrometer:micrometer-core:1.10.2")

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.integration:spring-integration-jdbc")

    implementation("org.jdbi:jdbi3-core:3.36.0")
    implementation("org.jdbi:jdbi3-kotlin:3.36.0")
    implementation("org.jdbi:jdbi3-kotlin-sqlobject:3.36.0")

    implementation("org.flywaydb:flyway-core")

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")

    implementation("com.google.guava:guava:31.1-jre")
    implementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    implementation("org.springframework.boot:spring-boot-starter-jdbc")

    testImplementation("io.mockk:mockk:1.13.2")
    testImplementation("org.awaitility:awaitility:4.2.0")
    testImplementation("org.amshove.kluent:kluent:1.72")
    testImplementation("org.testcontainers:junit-jupiter:$testContainersVersion")
    testImplementation("org.testcontainers:postgresql:$testContainersVersion")
    testImplementation("org.postgresql:postgresql:42.5.1")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict", "-opt-in=kotlin.RequiresOptIn")
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
}

tasks.named(BOOT_JAR_TASK_NAME) {
    enabled = false
}

tasks.named<Jar>(JAR_TASK_NAME) {
    archiveClassifier.set("") // Spring boot jar task appends -plain classifier by default
    enabled = true
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register<Jar>("sourcesJar") {
    dependsOn("classes")
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

publishing {
    publications {
        create<MavenPublication>("sources") {
            from(components["java"])
            artifact(tasks["sourcesJar"])
            artifactId = project.name
            groupId = project.group.toString()
            version = project.version.toString()
        }
    }
}