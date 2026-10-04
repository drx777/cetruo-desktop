plugins {
    id("org.jetbrains.kotlin.jvm") version "2.4.20"
    id("application")
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.example.cardforge"
version = "0.14.4"

kotlin {
    jvmToolchain(21)
}

javafx {
    version = "21.0.5"
    modules("javafx.base", "javafx.graphics", "javafx.controls", "javafx.swing")
}

dependencies {
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.3")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    implementation("org.apache.xmlgraphics:batik-transcoder:1.19")
    implementation("org.apache.pdfbox:pdfbox:3.0.8")
}

application {
    mainClass.set("com.example.cardforge.MainKt")
}
