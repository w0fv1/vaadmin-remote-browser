import java.util.Properties

plugins {
    `java-library`
    `maven-publish`
}

group = "dev.w0fv1"
version = "0.4.0"

java {
    withSourcesJar()
    withJavadocJar()
}

val springBootVersion = providers.gradleProperty("springBootVersion").get()
val vaadinVersion = providers.gradleProperty("vaadinVersion").get()

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.add("-parameters")
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}

tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("headed")
    }
}

tasks.register<Test>("headedTest") {
    description = "Runs browser tests that require a graphical desktop"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    dependsOn(tasks.named("testClasses"))
    useJUnitPlatform {
        includeTags("headed")
    }
    shouldRunAfter(tasks.test)
}
dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    api(platform("com.vaadin:vaadin-bom:$vaadinVersion"))
    api("com.vaadin:vaadin-spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-websocket")

    implementation("tools.jackson.core:jackson-databind")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.awaitility:awaitility")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "vaadmin-remote-browser"

            pom {
                name.set("Vaadmin Remote Browser")
                description.set("Vaadmin companion library: embed a real hosted Chrome inside Vaadin applications over the DevTools protocol")
                url.set("https://github.com/w0fv1/vaadmin-remote-browser")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("w0fv1")
                        name.set("w0fv1")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/w0fv1/vaadmin-remote-browser.git")
                    developerConnection.set("scm:git:ssh://github.com/w0fv1/vaadmin-remote-browser.git")
                    url.set("https://github.com/w0fv1/vaadmin-remote-browser")
                }
            }
        }
    }
}

val browserPlatform = providers.gradleProperty("browserPlatform").orElse(
    if (System.getProperty("os.name").startsWith("Windows")) "win64" else "linux64")
val browserRuntimeDirectory = providers.gradleProperty("browserRuntimeDirectory")
    .map { file(it) }.orElse(layout.buildDirectory.dir("browser-runtime").map { it.asFile })
val fetchBrowserRuntime by tasks.registering(Exec::class) {
    inputs.file("runtime/browser-lock.json")
    inputs.property("platform", browserPlatform)
    outputs.dir(browserRuntimeDirectory)
    enabled = !providers.gradleProperty("browserRuntimeDirectory").isPresent
    commandLine(providers.gradleProperty("pythonExecutable").getOrElse("python"),
        "runtime/fetch.py", "--platform", browserPlatform.get(), "--output", browserRuntimeDirectory.get().absolutePath)
}
tasks.processResources {
    from("runtime/browser-lock.json") {
        into("browser-runtime")
    }
}
tasks.processTestResources {
    dependsOn(fetchBrowserRuntime)
    from(browserRuntimeDirectory) {
        include("runtime.zip")
        into("browser-runtime")
    }
}
