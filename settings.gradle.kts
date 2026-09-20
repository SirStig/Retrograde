pluginManagement {
	repositories {
		mavenLocal()
		mavenCentral()
		gradlePluginPortal()
		maven("https://maven.fabricmc.net/") { name = "Fabric" }
		maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
		maven("https://maven.minecraftforge.net/") { name = "MinecraftForge" }
		maven("https://repo.spongepowered.org/repository/maven-public/") { name = "Sponge" }
		maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
		maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
		exclusiveContent {
			forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
			filter { includeGroup("maven.modrinth") }
		}
	}
	includeBuild("build-logic")
}

plugins {
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
	id("dev.kikugie.stonecutter") version "0.9"
}

stonecutter {
	create(rootProject) {
		fun match(version: String, vararg loaders: String) {
			loaders.forEach { loader ->
				val buildscriptName = when {
					version.startsWith("1.") && loader == "fabric" -> "build.fabric-legacy.gradle.kts"
					else -> "build.$loader.gradle.kts"
				}
				version("$version-$loader", version).buildscript = buildscriptName
			}
		}

		// Retrograde's version matrix. NeoForge doesn't exist for 1.20.1 (the
		// Forge/NeoForge split happened at 1.20.2), so 1.20.1 pairs with
		// plain Forge instead — everything newer pairs with NeoForge.
		match("1.20.1", "fabric", "forge")
		match("26.1", "fabric", "neoforge")
		match("26.3", "fabric", "neoforge")

		vcsVersion = "1.20.1-fabric"
	}
}
