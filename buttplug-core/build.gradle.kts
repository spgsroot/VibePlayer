plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
    signing
}

group = "io.github.spgsroot"
version = "1.0.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set("buttplug-core")
                description.set("Kotlin client library for the Buttplug intimate hardware control protocol")
                url.set("https://github.com/spgsroot/VibePlayer")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("spgsroot")
                        name.set("spgsroot")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/spgsroot/VibePlayer.git")
                    developerConnection.set("scm:git:ssh://github.com/spgsroot/VibePlayer.git")
                    url.set("https://github.com/spgsroot/VibePlayer")
                }
            }
        }
    }
    repositories {
        maven {
            name = "sonatype"
            val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            credentials {
                username = System.getenv("SONATYPE_USERNAME") ?: ""
                password = System.getenv("SONATYPE_PASSWORD") ?: ""
            }
        }
    }
}

signing {
    val signingKey = System.getenv("SIGNING_KEY") ?: ""
    val signingPassword = System.getenv("SIGNING_PASSWORD") ?: ""
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications)
}
