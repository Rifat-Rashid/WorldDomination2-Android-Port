import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
}

val gdxVersion = "1.14.2"
val natives = configurations.create("natives")
val generatedNativesDirectory = layout.buildDirectory.dir("generated/gdx-jniLibs").get().asFile

dependencies {
    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
    implementation("com.caverock:androidsvg-aar:1.4")
    add(natives.name, "com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-arm64-v8a")
    add(natives.name, "com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-armeabi-v7a")
    add(natives.name, "com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86")
    add(natives.name, "com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86_64")
}

val gdxNativeArchives = natives.files
val copyGdxAndroidNatives = tasks.register<Copy>("copyGdxAndroidNatives") {
    check(gdxNativeArchives.isNotEmpty()) { "No libGDX Android native archives were resolved" }
    gdxNativeArchives.forEach { archive ->
        val abi = archive.name.substringAfter("natives-").substringBefore(".jar")
        from(zipTree(archive)) {
            include("libgdx.so")
            into(abi)
        }
    }
    into(generatedNativesDirectory)
}

// Set these outside the repository (environment or user-level Gradle properties).
val betaApplicationId = providers.gradleProperty("wd2ApplicationId")
    .getOrElse("com.example.worlddomination")
fun uploadSetting(property: String, environment: String) =
    providers.gradleProperty(property).orElse(providers.environmentVariable(environment)).orNull
val uploadStoreFile = uploadSetting("wd2UploadStoreFile", "WD2_UPLOAD_STORE_FILE")
val uploadStorePassword = uploadSetting("wd2UploadStorePassword", "WD2_UPLOAD_STORE_PASSWORD")
val uploadKeyAlias = uploadSetting("wd2UploadKeyAlias", "WD2_UPLOAD_KEY_ALIAS")
val uploadKeyPassword = uploadSetting("wd2UploadKeyPassword", "WD2_UPLOAD_KEY_PASSWORD")
val uploadSettings = listOf(uploadStoreFile, uploadStorePassword, uploadKeyAlias, uploadKeyPassword)
val hasUploadSigning = uploadSettings.all { !it.isNullOrBlank() }
check(uploadSettings.all { it.isNullOrBlank() } || hasUploadSigning) {
    "Upload signing is incomplete: provide all four wd2Upload properties or WD2_UPLOAD variables."
}
val uploadKeyExists = uploadStoreFile?.let { file(it).isFile } ?: false
val qwenLlamaSourceArchive = providers.gradleProperty("wd2LlamaSourceArchive").orNull
val includeDeviceQa = providers.gradleProperty("wd2DeviceQa").getOrElse("false") == "true"
val qwenDebugModelHash = providers.gradleProperty("wd2QwenDebugModelSha256").orNull
val qwenDebugModelBytes = providers.gradleProperty("wd2QwenDebugModelSize").orNull?.toLongOrNull()
val packagedModel = providers.gradleProperty("wd2QwenPackagedModel").orNull?.let { file(it) }
val packagedHash = providers.gradleProperty("wd2QwenPackagedSha256").orNull
check((packagedModel == null && packagedHash == null) ||
        (packagedModel?.isFile == true && packagedHash?.matches(Regex("[0-9a-f]{64}")) == true && qwenDebugModelHash == null)) {
    "Provide a GGUF and its lowercase SHA-256 together for a self-contained offline distribution."
}
val packagedAssets = layout.buildDirectory.dir("generated/qwen-distribution")
val stageModel = packagedModel?.let { source ->
    val expected = requireNotNull(packagedHash)
    tasks.register<Sync>("stageVerifiedOpponentModel") {
        inputs.property("expectedSha256", expected)
        from(source) { rename { "opponent.gguf" } }
        into(packagedAssets.map { it.dir("wd2-local-ai") })
        doFirst {
            val digest = MessageDigest.getInstance("SHA-256")
            source.inputStream().buffered().use { input ->
                val buffer = ByteArray(1024 * 1024)
                var length = input.read(buffer)
                while (length >= 0) {
                    digest.update(buffer, 0, length)
                    length = input.read(buffer)
                }
            }
            check(digest.digest().joinToString("") { "%02x".format(it) } == expected) {
                "The packaged opponent model differs from the supplied SHA-256."
            }
        }
    }
}
check(qwenDebugModelHash == null && !providers.gradleProperty("wd2QwenDebugModelSize").isPresent ||
        qwenDebugModelHash?.matches(Regex("[0-9a-f]{64}")) == true && qwenDebugModelBytes != null && qwenDebugModelBytes > 0) {
    "Provide both wd2QwenDebugModelSha256 (lowercase) and wd2QwenDebugModelSize (bytes)."
}

android {
    namespace = "com.example.worlddomination"
    ndkVersion = "28.2.13676358"
    buildFeatures {
        buildConfig = true
    }
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = betaApplicationId
        minSdk = 24
        targetSdk = 37
        versionCode = 13
        versionName = "0.1.0-beta.11"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "QWEN_DEBUG_MODEL_SHA256", "\"\"")
        buildConfigField("long", "QWEN_DEBUG_MODEL_SIZE", "0L")
        buildConfigField("String", "QWEN_PACKAGED_SHA256", "\"${packagedHash.orEmpty()}\"")
        buildConfigField("long", "QWEN_PACKAGED_SIZE", "${packagedModel?.length() ?: 0L}L")

        externalNativeBuild {
            cmake {
                // Limit the optional runtime, while retaining libGDX's existing 32-bit ABIs.
                abiFilters("arm64-v8a", "x86_64")
                targets("wd2_qwen")
                arguments("-DANDROID_STL=c++_static")
                qwenLlamaSourceArchive?.let {
                    arguments("-DWD2_LLAMA_SOURCE_ARCHIVE=${file(it).absolutePath}")
                }
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    androidResources {
        if (packagedModel != null) noCompress += "gguf"
        // The Android splash does not play the desktop opening video.
        ignoreAssetsPatterns.add("<file>5911.webp")
    }

    sourceSets.getByName("main").jniLibs.srcDir(generatedNativesDirectory)
    if (packagedModel != null) sourceSets.getByName("main").assets.srcDir(packagedAssets.get().asFile)
    // Share deterministic source fixtures with device tests, never the game APK.
    sourceSets.getByName("androidTest").assets.srcDir("src/test/resources")
    if (includeDeviceQa) {
        sourceSets.getByName("androidTest").java.srcDir("src/deviceQa/java")
    }

    val uploadSigning = if (hasUploadSigning) signingConfigs.create("upload") {
        storeFile = file(uploadStoreFile!!)
        storePassword = uploadStorePassword
        keyAlias = uploadKeyAlias
        keyPassword = uploadKeyPassword
    } else null

    buildTypes {
        debug {
            if (qwenDebugModelHash != null && qwenDebugModelBytes != null) {
                buildConfigField("String", "QWEN_DEBUG_MODEL_SHA256", "\"$qwenDebugModelHash\"")
                buildConfigField("long", "QWEN_DEBUG_MODEL_SIZE", "${qwenDebugModelBytes}L")
            }
        }
        release {
            isDebuggable = false
            if (uploadSigning != null) signingConfig = uploadSigning
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) {
        it.hostTests[com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
    }
}

tasks.named("preBuild").configure {
    dependsOn(copyGdxAndroidNatives)
    if (stageModel != null) dependsOn(stageModel)
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}

val verifyPlayUploadConfiguration = tasks.register("verifyPlayUploadConfiguration") {
    group = "verification"
    description = "Rejects placeholder application IDs and missing upload signing before a Play build."
    val applicationIdForUpload = betaApplicationId
    val signingAvailable = hasUploadSigning && uploadKeyExists
    doLast {
        check(!applicationIdForUpload.startsWith("com.example.") &&
                applicationIdForUpload.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) {
            "Set -Pwd2ApplicationId to the permanent Play application ID before preparing an upload."
        }
        check(signingAvailable) {
            "Configure a real upload keystore using the external wd2Upload properties or WD2_UPLOAD variables."
        }
    }
}

tasks.register("preparePlayBeta") {
    group = "build"
    description = "Builds the release AAB only after validating the permanent ID and upload signing."
    dependsOn(verifyPlayUploadConfiguration, "bundleRelease")
}

tasks.matching { it.name == "bundleRelease" }.configureEach {
    mustRunAfter(verifyPlayUploadConfiguration)
}
