import java.util.Properties

plugins {
    id("com.android.application")
}

// The release key's details, kept out of git in keystore.properties (see README). Without it,
// release builds are signed with this computer's debug key, which is fine for installing yourself.
val release = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }
}

android {
    namespace = "io.github.snoyark.messagerelay"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.snoyark.messagerelay"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (release != null) {
            create("release") {
                storeFile = rootProject.file(release.getProperty("storeFile"))
                storePassword = release.getProperty("storePassword")
                keyAlias = release.getProperty("keyAlias")
                keyPassword = release.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (release != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
