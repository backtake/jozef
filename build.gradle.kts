plugins {
    java
    id("org.springframework.boot") version "3.4.4"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.jozef"
version = "0.0.1"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot — tylko kontener DI, zero web/REST
    implementation("org.springframework.boot:spring-boot-starter")

    // YAML configuration
    implementation("org.springframework.boot:spring-boot-configuration-processor")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Jackson (YAML parsing, JSON for tool calls)
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")

    // MP3 playback (Edge TTS neural voices)
    implementation("com.googlecode.soundlibs:jlayer:1.0.1.4")

    // Browser automation (Playwright Java)
    implementation("com.microsoft.playwright:playwright:1.49.0")

    // Global hotkey (Windows/macOS/Linux)
    implementation("com.github.kwhat:jnativehook:2.2.2")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    jvmArgs = listOf("-Xmx512m", "-XX:+UseG1GC")
}
