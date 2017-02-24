import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal

buildscript {

    repositories {
        mavenCentral()
        gradleScriptKotlin()
    }

    dependencies {
        classpath(kotlinModule("gradle-plugin", "1.1.0-rc-91"))
    }
}

apply {
    plugin("kotlin")
    plugin("maven")
}

repositories {
    mavenCentral()
    gradleScriptKotlin()
}

dependencies {
    compile(kotlinModule("stdlib", "1.1.0-rc-91"))
    testCompile("com.github.elect86:kotlintest:c4b7b397a0d182d1adaf61f71a9423c228dc0106")
    compile("com.github.elect86:glm:caaf5141fc6a914dda1ed9d6a4443fc33b6d2238")
    compile("com.github.elect86:openvr:45176d8ddd6f874102654bef1440f4b94e41ad58")
    compile("com.github.elect86:uno-sdk:b98f310924e16ef42ce6722f3b1783bfe98933b6")
    compile("net.java.dev.jna", "jna", "4.3.0")

    val jogl = "2.3.2"

    compile("org.jogamp.gluegen:gluegen-rt:$jogl")
    compile("org.jogamp.jogl:jogl-all:$jogl")

    runtime("org.jogamp.gluegen:gluegen-rt:$jogl:natives-android-aarch64")
    runtime("org.jogamp.gluegen:gluegen-rt:$jogl:natives-android-armv6")
    runtime("org.jogamp.gluegen:gluegen-rt:$jogl:natives-linux-amd64")
    runtime("org.jogamp.gluegen:gluegen-rt:$jogl:natives-linux-armv6")
    runtime("org.jogamp.gluegen:gluegen-rt:$jogl:natives-linux-armv6hf")
    runtime("org.jogamp.gluegen:gluegen-rt:$jogl:natives-linux-i586")
    runtime("org.jogamp.gluegen:gluegen-rt:$jogl:natives-macosx-universal")
    runtime("org.jogamp.gluegen:gluegen-rt:$jogl:natives-solaris-amd64")
    runtime("org.jogamp.gluegen:gluegen-rt:$jogl:natives-solaris-i586")
    runtime("org.jogamp.gluegen:gluegen-rt:$jogl:natives-windows-amd64")
    runtime("org.jogamp.gluegen:gluegen-rt:$jogl:natives-windows-i586")

    runtime("org.jogamp.jogl:jogl-all:$jogl:natives-android-aarch64")
    runtime("org.jogamp.jogl:jogl-all:$jogl:natives-android-armv6")
    runtime("org.jogamp.jogl:jogl-all:$jogl:natives-linux-amd64")
    runtime("org.jogamp.jogl:jogl-all:$jogl:natives-linux-armv6")
    runtime("org.jogamp.jogl:jogl-all:$jogl:natives-linux-armv6hf")
    runtime("org.jogamp.jogl:jogl-all:$jogl:natives-linux-i586")
    runtime("org.jogamp.jogl:jogl-all:$jogl:natives-macosx-universal")
    runtime("org.jogamp.jogl:jogl-all:$jogl:natives-solaris-amd64")
    runtime("org.jogamp.jogl:jogl-all:$jogl:natives-solaris-i586")
    runtime("org.jogamp.jogl:jogl-all:$jogl:natives-windows-amd64")
    runtime("org.jogamp.jogl:jogl-all:$jogl:natives-windows-i586")
}

configurations.all {
//    ResolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

allprojects {
    repositories {
        maven { setUrl("https://jitpack.io") }
    }
}