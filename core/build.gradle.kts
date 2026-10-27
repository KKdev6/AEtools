// Shared, read-only code for the customer app and the factory app: Bluetooth, the brand-neutral
// data model, the JBD read protocol, QR parsing, theme, fonts and brand assets.
// Nothing here may write BMS settings (see CLAUDE.md, Factory app).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.anantenergies.bms.core"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.code.scanner)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
