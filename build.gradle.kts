// Top-level build file where you can add configuration options common to all sub-projects/modules.
// Aquí se declaran plugins que se aplican a módulos individuales (apply false)
// y configuraciones compartidas entre todos los módulos del proyecto.
plugins {
    // Plugin de Android — se aplica en app/build.gradle.kts con 'alias'
    alias(libs.plugins.android.application) apply false
}

// Desarrollado por rcktsm
