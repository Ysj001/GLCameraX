// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    repositories {
        applyMavenLocal(this)
        maven { setUrl("https://maven.aliyun.com/nexus/content/groups/public/") }
        google()
        gradlePluginPortal()
        mavenCentral()
    }

    dependencies {
        classpath("com.vanniktech:gradle-maven-publish-plugin:0.14.2")
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:1.4.30")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.20")

    }
}

allprojects {
    repositories {
        applyMavenLocal(this)
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}