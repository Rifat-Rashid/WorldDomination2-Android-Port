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

android {
    namespace = "com.example.worlddomination"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.worlddomination"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets.getByName("main").jniLibs.srcDir(generatedNativesDirectory)

    buildTypes {
        release {
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

tasks.named("preBuild").configure {
    dependsOn(copyGdxAndroidNatives)
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
