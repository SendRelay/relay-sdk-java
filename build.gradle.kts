plugins {
    `java-library`
    // Publishes to Maven Central through the Central Portal (sources, javadoc and signatures included).
    id("com.vanniktech.maven.publish") version "0.34.0"
}

repositories { mavenCentral() }

dependencies {
    api("com.fasterxml.jackson.core:jackson-databind:2.19.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Java 17: the baseline for current server frameworks (Spring Boot 3, Quarkus 3, Micronaut 4).
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
tasks.withType<JavaCompile>().configureEach { options.release.set(17) }

val conformanceDir = System.getenv("RELAY_CONFORMANCE_DIR") ?: file("../conformance").absolutePath

tasks.test {
    useJUnitPlatform()
    environment("RELAY_CONFORMANCE_DIR", conformanceDir)
    // Fixture changes must re-run the tests, not hit Gradle's up-to-date cache.
    inputs.dir(conformanceDir).optional().withPropertyName("conformance")
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    // Signs only when release credentials are present (CI); local builds and publishToMavenLocal skip it.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
    coordinates("ng.com.sendrelay", "relay-java", version.toString())
    pom {
        name.set("Relay Java SDK")
        description.set("Official Relay server SDK for Java and Kotlin: quotes, deliveries, webhooks and tracking tokens")
        url.set("https://docs.sendrelay.com.ng/sdks/java-sdk")
        licenses { license { name.set("MIT"); url.set("https://opensource.org/licenses/MIT") } }
        developers { developer { id.set("sendrelay"); name.set("SendRelay") } }
        scm {
            url.set("https://github.com/SendRelay/relay-sdk-java")
            connection.set("scm:git:https://github.com/SendRelay/relay-sdk-java.git")
        }
    }
}
