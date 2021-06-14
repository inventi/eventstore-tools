import org.gradle.api.plugins.JavaPlugin.JAR_TASK_NAME
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.plugin.SpringBootPlugin.BOOT_JAR_TASK_NAME

plugins {
    id("org.springframework.boot") version "2.5.1"
    id("io.spring.dependency-management") version "1.0.10.RELEASE"
    kotlin("jvm") version "1.5.10"
    kotlin("plugin.spring") version "1.5.10"
    `maven-publish`
}

group = "com.github.inventi"
version = "1.0-SNAPSHOT"

java {
    targetCompatibility = JavaVersion.VERSION_11
    sourceCompatibility = JavaVersion.VERSION_11
}

repositories {
    mavenCentral()
}

dependencies {
    val jacksonVersion = "2.11.3"
    val testContainersVersion = "1.15.3"
    val micrometerVersion = "1.7.0"

    api("com.github.msemys:esjc:2.3.0")

    compileOnly("com.google.cloud:spring-cloud-gcp-starter-trace:2.0.3")
    compileOnly("io.micrometer:micrometer-registry-stackdriver:$micrometerVersion")
    compileOnly("org.springframework.boot:spring-boot-starter-data-redis")

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework:spring-tx")

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    implementation("org.jdbi:jdbi3-core:3.8.0")
    implementation("org.jdbi:jdbi3-kotlin:3.8.0")
    implementation("org.jdbi:jdbi3-kotlin-sqlobject:3.8.0")

    implementation("org.flywaydb:flyway-core:6.0.8")

    implementation("com.google.guava:guava:30.0-jre")
    implementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }

    testCompileOnly("io.micrometer:micrometer-registry-stackdriver:$micrometerVersion")
    testImplementation("io.mockk:mockk:1.9.3")
    testImplementation("org.testcontainers:testcontainers:$testContainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testContainersVersion")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict", "-Xopt-in=kotlin.RequiresOptIn")
        jvmTarget = JavaVersion.VERSION_11.toString()
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