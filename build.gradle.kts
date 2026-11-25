plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "io.github.karashilys"
// リリース時はタグから版を渡す(-PreleaseVersion=0.1.0)。普段の開発ビルドはSNAPSHOT。
version = providers.gradleProperty("releaseVersion").getOrElse("0.1.0-SNAPSHOT")

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

// テスト専用ビルド(仕様書8章, 10.2, 10.3)。
// 実サーバーで分類と差し替え結果を検証する別プラグインを src/testServer に置き、
// 開発用サーバー(runServer)にだけ読み込む。リリース用のjar(jarタスク)には含めない。
val testServer: SourceSet = sourceSets.create("testServer") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations[testServer.compileOnlyConfigurationName].extendsFrom(configurations.compileOnly.get())

val testServerJar = tasks.register<Jar>("testServerJar") {
    description = "実サーバー検証用プラグインのjarを作る(リリースには含めない)"
    archiveBaseName = "${project.name}-testserver"
    from(testServer.output)
}

// 実サーバー検証を自動で行う。run/ でサーバーを起動し、対応表の適用後に全検証を行って停止する。
// 結果が FAIL ならビルドを失敗にする。
// EULAへの同意: run/eula.txt で同意済みでない環境(CIなど)では、-PacceptEula=true を明示したときだけ
// Paperのコマンドライン同意フラグ(-Dcom.mojang.eula.agree=true)を付ける。
// これを付けることは、実行者がMinecraftのEULA(https://aka.ms/MinecraftEULA)に同意することを意味する。
val serverTestReport = layout.projectDirectory.file("run/plugins/DailyRecipeShuffleTest/report.txt")
tasks.register<xyz.jpenilla.runpaper.task.RunServer>("serverTest") {
    group = "verification"
    description = "開発用サーバーを起動して実サーバー検証を行い、結果がFAILならビルドを失敗にする"
    minecraftVersion("26.3")
    pluginJars(tasks.jar, testServerJar)
    jvmArgs("-Ddrs.test.autorun=true")
    if (providers.gradleProperty("acceptEula").orNull == "true") {
        jvmArgs("-Dcom.mojang.eula.agree=true")
    }
    outputs.upToDateWhen { false }
    doFirst {
        serverTestReport.asFile.delete()
    }
    doLast {
        val report = serverTestReport.asFile
        if (!report.exists()) {
            throw GradleException("実サーバー検証の結果がない: $report (サーバーが起動できなかった可能性がある。ログは run/logs/latest.log)")
        }
        val summary = report.readLines().lastOrNull().orEmpty()
        logger.lifecycle("実サーバー検証: $summary")
        if (!summary.startsWith("PASS")) {
            throw GradleException("実サーバー検証が失敗した: $report")
        }
    }
}

tasks {
    runServer {
        minecraftVersion("26.3")
        pluginJars(testServerJar)
    }
    val props = mapOf("version" to project.version)
    processResources {
        inputs.properties(props)
        filesMatching("plugin.yml") { expand(props) }
    }
    named<ProcessResources>(testServer.processResourcesTaskName) {
        inputs.properties(props)
        filesMatching("plugin.yml") { expand(props) }
    }
    test {
        useJUnitPlatform()
    }
}
