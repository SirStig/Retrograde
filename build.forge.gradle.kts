import java.util.zip.GZIPInputStream

plugins {
	id("mod-platform")
	id("net.minecraftforge.gradle")
	id("net.minecraftforge.jarjar")
	id("net.minecraftforge.renamer") version "1.1.7" apply false
}

// Pre-1.20.5 Forge uses obfuscated names at runtime, so the packaged jar
// needs Renamer Gradle's reobf step to work outside the dev environment —
// ForgeGradle 7 warns (legacy-missing-renamer) without it. Newer Forge
// (>=1.20.5) uses official Mojang names directly and doesn't need this.
//
// enableMixinRefmaps is the other half of this: applying the plugin alone
// only fixes the packaged-jar warning. The Mixin annotation processor
// itself still needs Renamer wired in to resolve obfuscation mappings for
// @Inject targets at compile time — without it, compiling any mixin that
// targets a real (non-constructor) vanilla method fails with "Unable to
// locate obfuscation mapping for @Inject target <method>" even though the
// plugin is applied.
if (stonecutter.eval(stonecutter.current.version, "<1.20.5")) {
	apply(plugin = "net.minecraftforge.renamer")
	configure<net.minecraftforge.renamer.gradle.RenamerExtension> {
		// ForgeGradle 7 already fetches the official<->obfuscated SRG mapping
		// data Renamer needs into its own local "mavenizer" repo as part of
		// resolving the `minecraft { mappings("official", ...) }` call above
		// — it's just never wired into Renamer automatically. Referenced by
		// glob rather than the exact (timestamp-suffixed) coordinate since
		// that timestamp isn't ours to predict and can change whenever
		// Forge republishes mapping data.
		setMappings(rootProject.fileTree(".gradle/mavenizer/repo/net/minecraft/mappings_official") {
			include("**/*-map2obf.tsrg.gz")
		})
		enableMixinRefmaps {
			source(sourceSets["main"])
		}
	}
}

fun prop(key: String) = project.property(key) as String

val unobfuscated = stonecutter.eval(stonecutter.current.version, ">=26.1")
val legacyForge = stonecutter.eval(stonecutter.current.version, "<=1.20")
val usesOfficialMappings = stonecutter.eval(stonecutter.current.version, ">=1.16")
val mergedDevOutput = stonecutter.eval(stonecutter.current.version, ">=1.17")
val modernRuntimeLibs = stonecutter.eval(stonecutter.current.version, ">=1.18")
val hasMixins = stonecutter.eval(stonecutter.current.version, ">=1.15")

platform {
	loader = "forge"
	jarTask.set("jarJar")
	dependencies {
		required("minecraft") {
			forgeVersionRange = "[${prop("deps.minecraft")}]"
		}
		required("forge") {
			forgeVersionRange = "[1,)"
		}
	}
}

minecraft {
	if (usesOfficialMappings) {
		mappings("official", prop("deps.minecraft"))
	}
	else {
		mappings(prop("deps.mappings_channel"), prop("deps.mappings_version"))
	}

	val atFile = rootProject.file("src/main/resources/aw/${stonecutter.current.version}.cfg")
	if (atFile.exists()) {
		accessTransformers = files(atFile)
	}

	runs {
		configureEach {
			workingDir.convention(layout.projectDirectory.dir("run"))
			systemProperty("forge.logging.console.level", "debug")
			if (hasMixins) {
				systemProperty("mixin.env.disableRefMap", "true")
				args("--mixin.config=${prop("mod.id")}.mixins.json")
			}
			@Suppress("UNCHECKED_CAST")
			val modConfigs = mods as NamedDomainObjectContainer<net.minecraftforge.gradle.SlimeLauncherOptionsNested.ModConfig>
			modConfigs.maybeCreate(prop("mod.id")).source(sourceSets["main"])
		}
		register("client") {
			args("--username", "Player")
		}
		register("server") {
			args("--nogui")
		}
	}
}

val runJavaVersion = when {
	stonecutter.eval(stonecutter.current.version, ">=26") -> 25
	stonecutter.eval(stonecutter.current.version, ">=1.20.5") -> 21
	// dev classes are compiled as J17 bytecode even for <=1.16
	else -> 17
}
tasks.withType<JavaExec>().matching { it.name.startsWith("run") }.configureEach {
	javaLauncher.set(javaToolchains.launcherFor {
		languageVersion.set(JavaLanguageVersion.of(runJavaVersion))
	})
	if (stonecutter.eval(stonecutter.current.version, ">=1.17") && stonecutter.eval(stonecutter.current.version, "<=1.18")) {
		jvmArgs("--add-opens=java.base/java.lang.invoke=ALL-UNNAMED")
	}
}
if (mergedDevOutput) {
	sourceSets.configureEach {
		val dir = layout.buildDirectory.dir("sourcesSets/$name")
		output.setResourcesDir(dir)
		java.destinationDirectory.set(dir)
	}
}
else {
	sourceSets.configureEach {
		output.setResourcesDir(layout.buildDirectory.dir("sourcesSets/$name/resources"))
		java.destinationDirectory.set(layout.buildDirectory.dir("sourcesSets/$name/classes"))
	}
}

repositories {
	minecraft.mavenizer(this)
	maven(fg.forgeMaven)
	maven(fg.minecraftLibsMaven)
	strictMaven("https://api.modrinth.com/maven", "maven.modrinth") { name = "Modrinth" }
	mavenCentral()
	if (stonecutter.eval(stonecutter.current.version, "<1.13")) {
		maven("https://maven.minecraftforge.net") {
			metadataSources {
				mavenPom()
				artifact()
			}
		}
	}
}

jarJar.register()

tasks.named<Jar>("jarJar") {
	archiveClassifier.set("")
	dependsOn("jar")
}

dependencies {
	implementation(minecraft.dependency("net.minecraftforge:forge:${prop("deps.forge")}"))

	if (!unobfuscated && hasMixins) {
		annotationProcessor("org.spongepowered:mixin:${libs.versions.mixin.get()}:processor")
		annotationProcessor("io.github.llamalad7:mixinextras-common:${libs.versions.mixinextras.get()}")

		compileOnly("io.github.llamalad7:mixinextras-common:${libs.versions.mixinextras.get()}")
		if (modernRuntimeLibs) {
			implementation("io.github.llamalad7:mixinextras-forge:${libs.versions.mixinextras.get()}")
		}
		else {
			compileOnly("io.github.llamalad7:mixinextras-forge:${libs.versions.mixinextras.get()}")
		}
		if (hasMixins) "jarJar"("io.github.llamalad7:mixinextras-forge:${libs.versions.mixinextras.get()}")
	}

	if (modernRuntimeLibs) {
		implementation(libs.moulberry.mixinconstraints)
	} else {
		compileOnly(libs.moulberry.mixinconstraints)
	}
	if (hasMixins) "jarJar"(libs.moulberry.mixinconstraints)

	if (stonecutter.eval(stonecutter.current.version, "<=1.14.4")) {
		compileOnly("org.spongepowered:mixin:${libs.versions.mixin.get()}")
	}
}

if (legacyForge) {
	val mappingsRepoBase = rootProject.file(".gradle/mavenizer/repo/net/minecraft")
	val mappingsChannel = if (usesOfficialMappings) "official" else prop("deps.mappings_channel")
	val mappingsVersion = if (usesOfficialMappings) prop("deps.minecraft") else prop("deps.mappings_version")

	val extractMcpToSrg by tasks.registering {
		val outputFile = layout.buildDirectory.file("mappings/map2srg.tsrg")
		outputs.file(outputFile)
		doLast {
			val mcVersion = prop("deps.minecraft")
			val groupDir = File(mappingsRepoBase, "mappings_$mappingsChannel")

			val candidates = groupDir.listFiles { d -> d.isDirectory }
				?.filter { it.name.startsWith(mcVersion) || it.name.contains(mappingsVersion) }
				?.mapNotNull { d -> d.listFiles { f -> f.name.endsWith("-map2srg.tsrg.gz") }?.firstOrNull() }
				?: emptyList()

			val gzFile = candidates.firstOrNull { it.parentFile.name.contains(mappingsVersion) }
				?: candidates.firstOrNull()
				?: throw GradleException(
					"No map2srg.tsrg.gz for channel '$mappingsChannel', mappings '$mappingsVersion', mc '$mcVersion' in $groupDir.\n" +
							"Available: ${groupDir.listFiles()?.joinToString { it.name } ?: "(group dir missing)"}"
				)

			logger.lifecycle("extractMcpToSrg[$mcVersion]: using ${gzFile.absolutePath}")
			val out = outputFile.get().asFile
			out.parentFile.mkdirs()
			GZIPInputStream(gzFile.inputStream()).use { gz -> out.outputStream().use { os -> gz.copyTo(os) } }
		}
	}

	tasks.withType<JavaCompile>().configureEach {
		dependsOn(extractMcpToSrg)
		val refMapFile = layout.buildDirectory.file("sourcesSets/main/${prop("mod.id")}.mixins.refmap.json")
		val outTsrgFile = layout.buildDirectory.file("mappings/compileJava-mappings.tsrg")
		options.compilerArgs.addAll(listOf(
			"-AoutRefMapFile=${refMapFile.get().asFile}",
			"-AreobfTsrgFile=${extractMcpToSrg.get().outputs.files.singleFile}",
			"-AoutTsrgFile=${outTsrgFile.get().asFile}",
			"-AmappingTypes=tsrg",
			"-AdefaultObfuscationEnv=searge"
		))
	}

	val srgMemberNames: Map<String, String> by lazy {
		val tsrg = layout.buildDirectory.file("mappings/map2srg.tsrg").get().asFile
		val names = HashMap<String, String>()
		if (tsrg.exists()) {
			var owner = ""
			tsrg.forEachLine { raw ->
				if (raw.isBlank() || raw.startsWith("tsrg2")) return@forEachLine
				if (!raw.startsWith("\t")) {
					owner = raw.trim().substringBefore(' ').replace('/', '.')
					return@forEachLine
				}
				// Two leading tabs mark member metadata (static, parameter names, ...).
				if (raw.startsWith("\t\t")) return@forEachLine
				val parts = raw.trim().split(' ')
				when {
					parts.size == 2 -> names["$owner/${parts[0]}"] = parts[1]
					parts.size == 3 && parts[1].startsWith("(") -> names["$owner/${parts[0]}${parts[1]}"] = parts[2]
					parts.size == 3 -> names["$owner/${parts[0]}"] = parts[2]
				}
			}
		}
		names
	}

	fun remapAccessTransformerLine(line: String): String {
		val comment = line.indexOf('#')
		val code = (if (comment >= 0) line.substring(0, comment) else line).trim()
		if (code.isEmpty()) return line

		val tokens = code.split(Regex("\\s+"))
		// "<access> <class>" widens the class itself and "*" / "*()" are wildcards - nothing to translate.
		if (tokens.size < 3) return line
		val owner = tokens[1]
		val member = tokens[2]
		if (member.startsWith("*")) return line

		val descriptor = member.indexOf('(')
		val srg = srgMemberNames["$owner/$member"]?.let {
			if (descriptor >= 0) it + member.substring(descriptor) else it
		}
		if (srg == null) {
			logger.warn("Access transformer: no SRG name for '$owner $member', shipping it untranslated.")
			return line
		}

		val trailing = if (comment >= 0) " " + line.substring(comment) else " # $member"
		return "${tokens[0]} $owner $srg$trailing"
	}

	tasks.withType<Jar>().configureEach {
		dependsOn(extractMcpToSrg)
		filesMatching("META-INF/accesstransformer.cfg") {
			filter { line: String -> remapAccessTransformerLine(line) }
		}
	}
}

if (legacyForge) {
	val fart: Configuration by configurations.creating {
		isTransitive = false
	}

	dependencies {
		fart("net.minecraftforge:ForgeAutoRenamingTool:1.1.0:all")
	}

	tasks.named<Jar>("jarJar") {
		dependsOn("extractMcpToSrg")
		doLast {
			val jarFile = archiveFile.get().asFile
			val vanillaMap = layout.buildDirectory.file("mappings/map2srg.tsrg").get().asFile
			val mixinMap = layout.buildDirectory.file("mappings/compileJava-mappings.tsrg").get().asFile

			fun runFart(input: File, output: File, map: File) {
				val javaBin = File(System.getProperty("java.home"), "bin/java")
				val args = mutableListOf(
					javaBin.absolutePath, "-jar", fart.singleFile.absolutePath,
					"--input", input.absolutePath,
					"--output", output.absolutePath,
					"--map", map.absolutePath,
					"--ann-fix", "--ids-fix", "--src-fix", "--record-fix"
				)
				sourceSets["main"].compileClasspath.files
					.filter { it.exists() && it.extension == "jar" }
					.forEach { args += listOf("--lib", it.absolutePath) }
				val proc = ProcessBuilder(args).redirectErrorStream(true).start()
				val output_ = proc.inputStream.bufferedReader().readText()
				if (proc.waitFor() != 0) throw GradleException("FART failed:\n$output_")
				logger.info(output_)
			}

			val tmp1 = File(jarFile.parentFile, jarFile.name + ".reobf1")
			val tmp2 = File(jarFile.parentFile, jarFile.name + ".reobf2")
			runFart(jarFile, tmp1, vanillaMap)
			if (mixinMap.exists() && mixinMap.length() > 0) {
				runFart(tmp1, tmp2, mixinMap)
				tmp1.delete()
				jarFile.delete(); tmp2.renameTo(jarFile)
			} else {
				jarFile.delete(); tmp1.renameTo(jarFile)
			}
		}
	}
}

tasks.named<Jar>("jar") {
	destinationDirectory.set(layout.buildDirectory.dir("intermediates/jar"))
	manifest {
		attributes["MixinConfigs"] = "${prop("mod.id")}.mixins.json"
	}
	from(layout.buildDirectory.file("sourcesSets/main/${prop("mod.id")}.mixins.refmap.json")) {
		into("/")
	}
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

sourceSets {
	main {
		resources.srcDir(
			"${rootDir}/versions/datagen/${stonecutter.current.version.split("-")[0]}/src/main/generated"
		)
	}
}
