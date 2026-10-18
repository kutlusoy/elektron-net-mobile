import javax.inject.Inject
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val shippedLocales = file("shipped-locales.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
val designDir = rootProject.file("design")

abstract class GenThemeTask @Inject constructor(private val execOps: ExecOperations) : DefaultTask() {
    @get:InputFile
    abstract val palettes: RegularFileProperty

    @get:InputFile
    abstract val script: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        execOps.exec {
            commandLine("python3", "-I", script.get().asFile.path, "--palettes", palettes.get().asFile.path, "--out", outputDir.get().asFile.path)
        }
    }
}

android {
    namespace = "org.elektronnet.mobile"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    androidResources {
        localeFilters += shippedLocales
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    flavorDimensions += "chain"
    productFlavors {
        create("mainnet") {
            dimension = "chain"
            applicationId = "org.elektronnet.mobile"
            resValue("string", "app_name", "Elektron")
            manifestPlaceholders["uriScheme"] = "elek"
            buildConfigField("String", "CHAIN", "\"main\"")
            buildConfigField("String", "NODE_CHAIN_ARG", "\"\"")
            buildConfigField("String", "DATADIR_NAME", "\"node\"")
            buildConfigField("String", "BECH32_HRP", "\"be\"")
            buildConfigField("int", "P2P_PORT", "8333")
            buildConfigField("int", "RPC_PORT", "8332")
            buildConfigField("String", "DEFAULT_THEME", "\"blue\"")
            buildConfigField("boolean", "IS_TESTNET", "false")
        }
        create("testnet") {
            dimension = "chain"
            applicationId = "org.elektronnet.mobiletestnet"
            resValue("string", "app_name", "Elektron (T)")
            manifestPlaceholders["uriScheme"] = "elektest"
            buildConfigField("String", "CHAIN", "\"testnet\"")
            buildConfigField("String", "NODE_CHAIN_ARG", "\"-testnet\"")
            buildConfigField("String", "DATADIR_NAME", "\"node-testnet\"")
            buildConfigField("String", "BECH32_HRP", "\"tb\"")
            buildConfigField("int", "P2P_PORT", "18333")
            buildConfigField("int", "RPC_PORT", "18332")
            buildConfigField("String", "DEFAULT_THEME", "\"red\"")
            buildConfigField("boolean", "IS_TESTNET", "true")
        }
    }

    signingConfigs {
        val keystorePath = providers.environmentVariable("ELEKTRON_KEYSTORE").orNull
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = providers.environmentVariable("ELEKTRON_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("ELEKTRON_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("ELEKTRON_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        create("nightly") {
            initWith(getByName("release"))
            applicationIdSuffix = ".nightly"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs.useLegacyPackaging = true
    }

    lint {
        error += listOf("MissingTranslation", "HardcodedText")
        abortOnError = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

val genTheme by tasks.registering(GenThemeTask::class) {
    palettes.set(designDir.resolve("palettes.json"))
    script.set(designDir.resolve("gen-theme.py"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.java?.addGeneratedSourceDirectory(genTheme, GenThemeTask::outputDir)
    }
}

val checkContrast by tasks.registering(Exec::class) {
    inputs.file(designDir.resolve("palettes.json"))
    inputs.file(designDir.resolve("check-contrast.py"))
    commandLine("python3", "-I", designDir.resolve("check-contrast.py").path)
}

val checkLocales by tasks.registering(Exec::class) {
    inputs.dir("src/main/res")
    inputs.file("shipped-locales.txt")
    commandLine("python3", "-I", rootProject.file("tools/check-locales.py").path, rootProject.projectDir.path)
}

val checkRng by tasks.registering(Exec::class) {
    inputs.dir("src/main/java")
    commandLine("python3", "-I", rootProject.file("tools/check-rng.py").path)
}

tasks.named("preBuild") { dependsOn(checkContrast, checkLocales, checkRng) }

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.core)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
}
