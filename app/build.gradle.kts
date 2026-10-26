import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// Optional -Pomnicalc.buildDir=<path> places outputs outside the workspace, where IDE indexers
// cannot hold files open and make Gradle's cleanup fail with "Unable to delete directory".
layout.buildDirectory.set(file(providers.gradleProperty("omnicalc.buildDir").getOrElse("build_clean")))

// Release signing key, kept outside the repository. The properties file (storeFile,
// storePassword, keyAlias, keyPassword; a relative storeFile is resolved against the file's own
// folder) is found through the BAZIQ_SIGNING_PROPERTIES environment variable (used by CI), the
// baziq.signingProperties Gradle property (e.g. in ~/.gradle/gradle.properties), or an ignored
// keystore.properties in the project root.
val releaseSigningFile: File? = listOfNotNull(
    providers.environmentVariable("BAZIQ_SIGNING_PROPERTIES").orNull,
    providers.gradleProperty("baziq.signingProperties").orNull
).map { file(it) }.firstOrNull { it.exists() }
    ?: rootProject.file("keystore.properties").takeIf { it.exists() }
val releaseKeystoreProperties: Properties? = releaseSigningFile
    ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }

android {
    namespace = "com.omnicalc.platform"
    compileSdk = 34

    defaultConfig {
        // Keep this ID: it is the identity of the installed app; changing it would install a
        // separate app instead of updating it (and leave the user's saved data behind).
        applicationId = "com.omnicalc.platform"
        minSdk = 26
        targetSdk = 34
        versionCode = 6
        versionName = "1.1.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        getByName("debug") {
            enableV1Signing = true
            enableV2Signing = true
        }
        if (releaseKeystoreProperties != null) {
            create("release") {
                storeFile = releaseSigningFile!!.parentFile.resolve(releaseKeystoreProperties.getProperty("storeFile"))
                storePassword = releaseKeystoreProperties.getProperty("storePassword")
                keyAlias = releaseKeystoreProperties.getProperty("keyAlias")
                keyPassword = releaseKeystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Signed with the dedicated release key when it is configured. Without it the APK is
            // left unsigned (app-release-unsigned.apk): a release is never signed with a debug key.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Compose UI tests run as local (Robolectric) tests, so they never install anything on a
    // phone; connectedAndroidTest would replace the installed app with the debug variant.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // DataStore Preferences
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // OkHttp & Moshi for the currency rate APIs. Moshi adapters are generated at build time
    // (KSP) for the @JsonClass response models, avoiding kotlin-reflect at runtime.
    implementation("com.squareup.moshi:moshi:1.15.1")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Unit Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.24")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    // IdlingPolicies, to give the Robolectric UI tests' first, cold start more than 60 s
    testImplementation("androidx.test.espresso:espresso-core:3.5.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
