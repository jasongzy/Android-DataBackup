import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties

private fun Project.configureCommon() {
    pluginManager.apply("com.android.application")
    pluginManager.apply("org.jetbrains.kotlin.android")

    val signingProperties = Properties()
    val signingPropertiesFile = rootProject.file("keystore.properties")
    if (signingPropertiesFile.isFile) {
        signingPropertiesFile.inputStream().use(signingProperties::load)
    }
    fun signingValue(environmentName: String, propertyName: String): String? =
        System.getenv(environmentName)?.takeIf(String::isNotBlank)
            ?: signingProperties.getProperty(propertyName)?.takeIf(String::isNotBlank)

    val storePath = signingValue("STORE_FILE", "storeFile")
    val storePassword = signingValue("STORE_PASSWORD", "storePassword")
    val keyAlias = signingValue("KEY_ALIAS", "keyAlias")
    val keyPassword = signingValue("KEY_PASSWORD", "keyPassword")
    val hasReleaseSigning = listOf(storePath, storePassword, keyAlias, keyPassword).all { it != null }

    extensions.getByType<ApplicationExtension>().apply {
        buildToolsVersion = catalogLibs.findVersion("buildTools").get().toString()

        val releaseSigningConfig = if (hasReleaseSigning) {
            signingConfigs.create("release") {
                storeFile = rootProject.file(storePath!!)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
                enableV2Signing = true
                enableV3Signing = true
            }
        } else {
            null
        }

        buildTypes {
            release {
                isMinifyEnabled = true
                isShrinkResources = true
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                buildConfigField("Boolean", "ENABLE_VERBOSE", "false")
                signingConfig = releaseSigningConfig
            }
            debug {
                isMinifyEnabled = false
                isShrinkResources = false
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                buildConfigField("Boolean", "ENABLE_VERBOSE", "false")
            }
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        buildFeatures {
            buildConfig = true
        }

        packaging {
            resources {
                excludes += "/META-INF/{AL2.0,LGPL2.1}"
                excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            }
        }

        tasks.withType<KotlinCompile>().configureEach {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
                freeCompilerArgs.add("-Xcontext-receivers")
            }
        }
    }
}

class ApplicationCommonConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            configureCommon()
        }
    }
}
