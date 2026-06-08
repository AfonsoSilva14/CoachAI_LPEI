plugins {
    // Plugin principal para projetos Android
    id("com.android.application")

    // Plugin para suporte da linguagem Kotlin no Android
    id("org.jetbrains.kotlin.android")
}

android {
    // Namespace da aplicação
    namespace = "com.example.coachai"

    // Versão do SDK usada para compilar o projeto
    compileSdk = 36

    defaultConfig {
        // Identificador único da aplicação
        applicationId = "com.example.coachai"

        // Versão mínima do Android suportada
        minSdk = 26

        // Versão do Android para a qual a aplicação é direcionada
        targetSdk = 36

        // Código interno da versão da aplicação
        versionCode = 1

        // Nome visível da versão da aplicação
        versionName = "1.0"

        // Runner utilizado para testes instrumentados
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Desativa a minificação/ofuscação do código na versão release
            isMinifyEnabled = false

            // Ficheiros de configuração do ProGuard
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        // Compatibilidade com Java 11
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Funcionalidades base do Android com Kotlin
    implementation("androidx.core:core-ktx:1.13.1")

    // Suporte para AppCompatActivity e componentes compatíveis
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Componentes visuais do Material Design
    implementation("com.google.android.material:material:1.12.0")

    // Extensões úteis para Activities em Kotlin
    implementation("androidx.activity:activity-ktx:1.9.2")

    // Suporte para ConstraintLayout
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Biblioteca MediaPipe Tasks Vision para estimativa de pose
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    // CameraX: acesso à câmara através da API Camera2
    implementation("androidx.camera:camera-camera2:1.3.4")

    // CameraX: integração da câmara com o ciclo de vida da Activity
    implementation("androidx.camera:camera-lifecycle:1.3.4")

    // CameraX: componente PreviewView para visualizar a câmara
    implementation("androidx.camera:camera-view:1.3.4")

    // Biblioteca para testes unitários
    testImplementation("junit:junit:4.13.2")

    // Suporte JUnit para testes instrumentados Android
    androidTestImplementation("androidx.test.ext:junit:1.2.1")

    // Biblioteca Espresso para testes de interface
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

kotlin {
    // Define a JVM Toolchain usada pelo Kotlin
    jvmToolchain(11)
}