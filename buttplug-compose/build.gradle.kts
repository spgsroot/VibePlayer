plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
    signing
}

group = "io.github.spgsroot"
version = "1.0.0"

android {
    namespace = "io.github.spgsroot.buttplug.compose"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    api(project(":buttplug-core"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.ui.tooling.preview)
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation(libs.androidx.ui.tooling)
}

publishing {
    publications {
        create<MavenPublication>("mavenAndroid") {
            afterEvaluate {
                from(components["release"])
            }
            pom {
                name.set("buttplug-compose")
                description.set("Jetpack Compose UI components for buttplug-core client library")
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
