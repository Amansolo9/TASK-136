plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("com.google.devtools.ksp")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    androidTarget()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.insert-koin:koin-core:3.5.6")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

                implementation("androidx.room:room-runtime:2.7.0-alpha09")
                implementation("androidx.room:room-ktx:2.7.0-alpha09")
                implementation("androidx.sqlite:sqlite:2.5.0-alpha09")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
            }
        }

        val androidMain by getting {
            dependencies {
                implementation("androidx.core:core-ktx:1.13.1")
                implementation("androidx.room:room-paging:2.7.0-alpha09")
                implementation("net.zetetic:android-database-sqlcipher:4.5.4")
            }
        }

        val androidUnitTest by getting {
            dependencies {
                implementation("junit:junit:4.13.2")
                implementation("androidx.test:core:1.6.1")
                implementation("androidx.test.ext:junit:1.2.1")
                implementation("org.robolectric:robolectric:4.14.1")
                implementation("androidx.room:room-testing:2.7.0-alpha09")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
            }
        }
    }
}

dependencies {
    add("kspCommonMainMetadata", "androidx.room:room-compiler:2.7.0-alpha09")
    add("kspAndroid", "androidx.room:room-compiler:2.7.0-alpha09")
}

android {
    namespace = "com.eaglepoint.task136.shared"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
