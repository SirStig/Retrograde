plugins {
	id("mod-platform")
	id("net.fabricmc.fabric-loom-remap")
}

platform {
	loader = "fabric"
	dependencies {
		required("minecraft") {
			versionRange = prop("deps.minecraft")
		}
		required("fabric-api") {
			slug("fabric-api")
			versionRange = ">=${prop("deps.fabric-api")}"
		}
		required("fabricloader") {
			versionRange = ">=${libs.fabric.loader.get().version}"
		}
	}
}

loom {
	accessWidenerPath = rootProject.file("src/main/resources/aw/${stonecutter.current.version}.accesswidener")
	val isJbr = System.getProperty("java.vendor")?.contains("JetBrains", ignoreCase = true) == true
	runs.named("client") {
		client()
		ideConfigGenerated(false)
		runDir = "run/"
		environment = "client"
		programArgs("--username=Player")
		configName = "Fabric Client"
		if (isJbr) vmArg("-XX:+AllowEnhancedClassRedefinition")
	}
	runs.named("server") {
		server()
		ideConfigGenerated(false)
		runDir = "run/"
		environment = "server"
		configName = "Fabric Server"
		if (isJbr) vmArg("-XX:+AllowEnhancedClassRedefinition")
	}
}

fabricApi {
	configureDataGeneration {
		outputDirectory = file("${rootDir}/versions/datagen/${stonecutter.current.version.split("-")[0]}/src/main/generated")
		client = true
	}
}

repositories {
	mavenCentral()
	strictMaven("https://api.modrinth.com/maven", "maven.modrinth") { name = "Modrinth" }
}

dependencies {
	minecraft("com.mojang:minecraft:${prop("deps.minecraft")}")
	mappings(
		loom.layered {
			officialMojangMappings()
		})
	modImplementation(libs.fabric.loader)
	modImplementation("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric-api")}")
}

project.afterEvaluate {
	val mixinJarPath = configurations.compileClasspath.get().files
		.firstOrNull { it.name.contains("sponge-mixin") || (it.name.contains("mixin") && !it.name.contains("fabric-mixin-compile-extensions")) }
		?.absolutePath

	if (mixinJarPath != null) {
		loom {
			runs.named("client") {
				vmArg("-javaagent:$mixinJarPath")
			}
			runs.named("server") {
				vmArg("-javaagent:$mixinJarPath")
			}
		}
	}
}
