@file:Suppress("unused", "DuplicatedCode")

import dev.kikugie.fletching_table.extension.FletchingTableExtension
import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import me.modmuss50.mpp.ModPublishExtension
import me.modmuss50.mpp.ReleaseType
import me.modmuss50.mpp.platforms.modrinth.ModrinthEnvironment
import org.gradle.api.JavaVersion
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Copy
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.internal.extensions.stdlib.toDefaultLowerCase
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.*
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.plugins.ide.idea.model.IdeaModel
import java.util.*
import javax.inject.Inject
import kotlin.apply

fun Project.prop(name: String): String = (findProperty(name) ?: "") as String

fun Project.env(variable: String): String? {
	var value = providers.environmentVariable(variable).orNull
	if (value != null) return value

	val envFile = rootProject.file(".env")
	if (envFile.exists()) {
		val props = java.util.Properties()
		envFile.inputStream().use { props.load(it) }
		value = props.getProperty(variable)
		if (value != null) return value
	}

	return findProperty(variable) as? String
}

fun Project.envTrue(variable: String): Boolean = env(variable)?.toDefaultLowerCase() == "true"

fun RepositoryHandler.strictMaven(
	url: String, vararg groups: String, configure: MavenArtifactRepository.() -> Unit = {}
) = exclusiveContent {
	forRepository { maven(url) { configure() } }
	filter { groups.forEach(::includeGroup) }
}

abstract class ModPlatformPlugin @Inject constructor() : Plugin<Project> {
	override fun apply(project: Project) = with(project) {
		val inferredLoader = project.buildFile.name.substringAfter('.').replace(".gradle.kts", "")
		val inferredLoaderIsFabric = inferredLoader == "fabric-legacy"
		val inferredLoaderIsForge = inferredLoader == "forge"

		val extension = extensions.create("platform", ModPlatformExtension::class.java).apply {
			loader.convention(inferredLoader)
			jarTask.convention(
				when {
					inferredLoaderIsFabric -> "remapJar"
					inferredLoaderIsForge -> "reobfJar"
					else -> "jar"
				}
			)
			sourcesJarTask.convention(if (inferredLoaderIsFabric) "remapSourcesJar" else "sourcesJar")
		}

		listOf(
			"org.jetbrains.kotlin.jvm",
			"com.google.devtools.ksp",
			"dev.kikugie.fletching-table"
		).forEach { apply(plugin = it) }

		afterEvaluate {
			configureProject(extension)
		}
	}

	private fun Project.configureProject(extension: ModPlatformExtension) {
		val loader = extension.loader.get()
		val isFabric = loader == "fabric"
		val isNeoForge = loader == "neoforge"
		val isForge = loader == "forge"

		val modId = prop("mod.id")
		val modVersionClean = prop("mod.version")
		val originalModVersion = prop("mod.version_prefix")+prop("mod.version")+prop("mod.version_suffix")
		var modVersion = prop("mod.version_prefix")+prop("mod.version")+prop("mod.version_suffix")
		val mcVersion = prop("deps.minecraft")
		var mcRange = prop("mod.mc_range").ifBlank { "[$mcVersion]" }
		if (env("BUILD_UNBOUND_VERSION_RANGE") == "true") {
			mcRange = "*"
		}
		val isNoDowngraderTaskRequested = gradle.startParameter.taskNames.any {
			it.contains("buildAndCollectNoDowngrader", ignoreCase = true)
		}

		val stonecutter = extensions.getByType<StonecutterBuildExtension>()
		configureStonecutterReplacements(stonecutter)

		listOf(
			"java",
			"me.modmuss50.mod-publish-plugin",
			"idea",
		).forEach { apply(plugin = it) }

		extension.requiredJava.set(
			when {
				stonecutter.eval(stonecutter.current.version, ">=26") -> JavaVersion.VERSION_25
				stonecutter.eval(stonecutter.current.version, ">=1.20.5") -> JavaVersion.VERSION_21
				stonecutter.eval(stonecutter.current.version, ">=1.18") -> JavaVersion.VERSION_17
				stonecutter.eval(stonecutter.current.version, ">=1.17") -> JavaVersion.VERSION_16
				else -> JavaVersion.VERSION_1_8
			}
		)

		var compileJavaVersion = extension.requiredJava.get()
		if (compileJavaVersion < JavaVersion.VERSION_17) {
			compileJavaVersion = JavaVersion.VERSION_17
			if (!isNoDowngraderTaskRequested) {
				configureDowngrade(extension, compileJavaVersion)
			}
			else {
				modVersion += "-nodowngrader"
			}
		}

		val fullVersion = "$modVersion+$mcVersion-$loader"
		val publishDisplayVersion = "$loader-$modVersion+$mcVersion"
		version = fullVersion

		extension.dependencies {
			required.maybeCreate("minecraft").apply {
				modid.set("minecraft")
				versionRange.set(mcRange)
				forgeVersionRange.set(mcRange)
			}

			if (isFabric) {
				required.maybeCreate("java").apply {
					modid.set("java")
					versionRange.set(">=${extension.requiredJava.get().majorVersion}")
				}

				required.maybeCreate("fabricloader").apply {
					modid.set("fabricloader")
					versionRange.set(">=0.12.0")
				}

				required.maybeCreate("fabric-api").apply {
					val legacyFabricApi = stonecutter.eval(stonecutter.current.version, "<=1.19.4")
					modid.set(if (legacyFabricApi) "fabric" else "fabric-api")
					versionRange.set(prop("mod.api_range").ifBlank { "*" })
				}
			}
		}

		configureFletchingTable()
		configureJarTask(modId, loader, stonecutter)
		configureIdea()
		configureProcessResources(
			isFabric,
			isNeoForge,
			isForge,
			modId,
			modVersionClean,
			mcVersion,
			extension,
			extension.requiredJava.get(),
			stonecutter
		)
		configureJava(stonecutter, compileJavaVersion)
		registerBuildAndCollectTask(extension, modVersion)
		registerBuildAndCollectNoDowngraderTask(extension, originalModVersion)
		configurePublishing(extension, loader, stonecutter, modVersion, publishDisplayVersion)
	}

	private fun Project.configureDowngrade(extension: ModPlatformExtension, compileJavaVersion: JavaVersion) {

		val requiredTarget = extension.requiredJava.get()
		val needsDowngrade = requiredTarget < compileJavaVersion

		val pluginsToApply = mutableListOf(
			"java",
			"me.modmuss50.mod-publish-plugin",
			"idea"
		)

		if (needsDowngrade) {
			pluginsToApply.add("xyz.wagyourtail.jvmdowngrader")
		}

		pluginsToApply.forEach { apply(plugin = it) }

		if (needsDowngrade) {
			extensions.configure<Any>("jvmdg") {
				withGroovyBuilder {
					setProperty("downgradeTo", requiredTarget)
				}
			}

			val originalTarget = extension.jarTask.get()

			tasks.named("downgradeJar") {
				val originalTask = tasks.named(originalTarget, org.gradle.jvm.tasks.Jar::class.java)
				dependsOn(originalTask)
				withGroovyBuilder {
					setProperty("inputFile", originalTask.flatMap { it.archiveFile })
					setProperty("destinationDirectory", layout.buildDirectory.dir("../build/devlibs"))
				}
			}

			tasks.named("shadeDowngradedApi") {
				withGroovyBuilder {
					setProperty("archiveClassifier", "")
				}
			}

			tasks.named("build") {
				dependsOn("shadeDowngradedApi")
			}

			extension.jarTask.set("shadeDowngradedApi")
		}
	}

	private fun Project.configureJarTask(modId: String, loader: String, stonecutter: StonecutterBuildExtension) {
		val isForge = loader == "forge"
		val needsFmlAt = isForge
			&& stonecutter.eval(stonecutter.current.version, "<=1.12")
			&& rootProject.file("src/main/resources/aw/${stonecutter.current.version}.cfg").exists()

		tasks.withType<Jar>().configureEach {
			archiveBaseName.set(modId)
			if (isForge) {
				manifest.attributes(
					"MixinConfigs" to "${modId}.mixins.json"
				)
				if (needsFmlAt) {
					manifest.attributes(
						"FMLAT" to "accesstransformer.cfg"
					)
				}
			}
		}
	}

	private fun Project.configureProcessResources(
		isFabric: Boolean,
		isNeoForge: Boolean,
		isForge: Boolean,
		modId: String,
		modVersionClean: String,
		mcVersion: String,
		extension: ModPlatformExtension,
		requiredJava: JavaVersion,
		stonecutter: StonecutterBuildExtension
	) {
		tasks.named<ProcessResources>("processResources") {
			dependsOn(tasks.named("stonecutterGenerate"))
			dependsOn("kspKotlin")

			filesMatching("*.mixins.json") {
				val needsRefmap = isForge && stonecutter.eval(stonecutter.current.version, "<=1.20") // legacyForge
				if (!needsRefmap) {
					filter { line: String ->
						if (line.trimStart().startsWith("\"refmap\"")) null else line
					}
				}

				val mixinJava = if (isForge && requiredJava > JavaVersion.VERSION_17) {
					"JAVA_17"
				} else {
					"JAVA_${requiredJava.majorVersion}"
				}

				expand(
					"java" to mixinJava,
					"id" to modId
				)
			}

			var contributors = prop("mod.contributors")
			var authors = prop("mod.authors")
			var issuesUrl = prop("mod.issues_url")
			if (issuesUrl == "") issuesUrl = prop("mod.sources_url") + "/issues"

			if (isFabric) {
				contributors = contributors.replace(", ", "\", \"")
				authors = authors.replace(", ", "\", \"")
			}

			val dependencies = buildDependenciesBlock(isFabric, modId, extension.dependencies)
			val breaks = buildFabricBreaksBlock(extension.dependencies)
			val cfgResourcePath = "aw/${stonecutter.current.version}.cfg"
			val awResourcePath = "aw/${stonecutter.current.version}.accesswidener"
			val ctResourcePath = "aw/${stonecutter.current.version}.classtweaker"

			val props = mapOf(
				"version" to modVersionClean,
				"minecraft" to mcVersion,
				"sc_version" to stonecutter.current.version,
				"id" to modId,
				"name" to prop("mod.name"),
				"group" to prop("mod.group"),
				"authors" to authors,
				"contributors" to contributors,
				"license" to prop("mod.license"),
				"description" to prop("mod.description"),
				"issues_url" to issuesUrl,
				"homepage_url" to prop("mod.homepage_url"),
				"sources_url" to prop("mod.sources_url"),
				"discord_url" to prop("mod.discord_url"),
				"dependencies" to dependencies
			)

			when {
				isFabric -> {
					filesMatching("fabric.mod.json") {
						filter { line ->
							val trimmed = line.trim()
							val comma = if (trimmed.endsWith(",")) "," else ""
							when {
								trimmed.startsWith("\"depends\": {}") ->
									"  \"depends\": {$dependencies\n  }$comma"
								trimmed.startsWith("\"breaks\": {}") ->
									if (breaks.isEmpty()) line else "  \"breaks\": {$breaks\n  }$comma"
								else -> line
							}
						}
						expand(props.filterKeys { it != "dependencies" })
					}
					filesMatching("aw/*.accesswidener") {
						if (path != awResourcePath) exclude()
					}
					filesMatching("aw/*.classtweaker") {
						if (path != ctResourcePath) exclude()
					}
					exclude("META-INF/mods.toml", "META-INF/neoforge.mods.toml", "aw/*.cfg", ".cache", "pack.mcmeta")
				}

				isNeoForge -> {
					val usesLegacyToml = stonecutter.eval(stonecutter.current.version, "<=1.20.3")
					filesMatching("aw/*.cfg") {
						if (path == cfgResourcePath) path = "META-INF/accesstransformer.cfg" else exclude()
					}
					if (usesLegacyToml) {
						filesMatching("META-INF/mods.toml") { expand(props) }
						filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
						exclude("fabric.mod.json", "aw/*.accesswidener", "aw/*.classtweaker", ".cache", "pack.mcmeta")
					} else {
						filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
						exclude("META-INF/mods.toml", "fabric.mod.json", "aw/*.accesswidener", "aw/*.classtweaker", ".cache", "pack.mcmeta")
					}
				}

				isForge -> {
					filesMatching("META-INF/mods.toml") { expand(props) }
					filesMatching("aw/*.cfg") {
						if (path == cfgResourcePath) path = "META-INF/accesstransformer.cfg" else exclude()
					}
					exclude("META-INF/neoforge.mods.toml", "fabric.mod.json", "aw/*.accesswidener", "aw/*.classtweaker", ".cache")
				}
			}
		}
	}

	private fun buildFabricBreaksBlock(deps: DependenciesConfig): String = buildString {
		val entries = deps.incompatible.map { "    \"${it.modid.get()}\": \"${it.versionRange.get()}\"" }
		if (entries.isNotEmpty()) append("\n" + entries.joinToString(",\n"))
	}

	private fun buildDependenciesBlock(
		isFabric: Boolean, modId: String, deps: DependenciesConfig
	): String = if (isFabric) {
		buildString {
			fun joinEntries(container: NamedDomainObjectContainer<Dependency>): List<String> =
				container.map { "    \"${it.modid.get()}\": \"${it.versionRange.get()}\"" }

			val entries = joinEntries(deps.required) + joinEntries(deps.optional)
			if (entries.isNotEmpty()) append("\n" + entries.joinToString(",\n"))
		}
	} else {
		buildString {
			fun appendBlock(container: NamedDomainObjectContainer<Dependency>, type: String) {
				container.forEach {
					appendLine(
						"""

						[[dependencies.$modId]]
						modId = "${it.modid.get()}"
						side = "${it.environment.get().uppercase(Locale.getDefault())}"
                        versionRange = "${it.forgeVersionRange.get()}"
						mandatory = ${if (type == "required") "true" else "false"}
                        type = "$type"
						""".replace("                  ", "").trimIndent()
					)
				}
			}

			appendBlock(deps.required, "required")
			appendBlock(deps.optional, "optional")
			appendBlock(deps.incompatible, "incompatible")
		}
	}

	private fun Project.configureJava(stonecutter: StonecutterBuildExtension, requiredJava: JavaVersion) {
		extensions.configure<JavaPluginExtension>("java") {
			//withSourcesJar()
			//withJavadocJar()
			sourceCompatibility = requiredJava
			targetCompatibility = requiredJava
			// Without an explicit toolchain, compileJava just uses whatever JVM
			// launched the Gradle daemon as the compiler and passes it
			// `--release <requiredJava>` — which fails outright once a target
			// (e.g. 26.x's Java 25 requirement) asks for a release newer than
			// that daemon JVM. Requesting the toolchain here makes Gradle
			// resolve (or auto-provision) a JDK that's actually new enough.
			toolchain {
				languageVersion.set(JavaLanguageVersion.of(requiredJava.majorVersion))
			}
		}
	}

	private fun Project.configureIdea() {
		extensions.configure<IdeaModel>("idea") {
			module {
				isDownloadJavadoc = true
				isDownloadSources = true
			}
		}
	}

	private fun Project.configureFletchingTable() {
		extensions.configure<FletchingTableExtension> {
			mixins.create("main").apply {
				mixin("default", "${prop("mod.id")}.mixins.json")
			}
		}
	}

	private fun Project.registerBuildAndCollectTask(extension: ModPlatformExtension, modVersion: String) {
		tasks.register<Copy>("buildAndCollect") {
			group = "build"
			from(
				tasks.named(extension.jarTask.get())
			)
			into(rootProject.file("output/$modVersion"))
			dependsOn("build")
		}
	}

	private fun Project.registerBuildAndCollectNoDowngraderTask(extension: ModPlatformExtension, modVersion: String) {
		tasks.register<Copy>("buildAndCollectNoDowngrader") {
			group = "build"
			from(
				tasks.named(extension.jarTask.get())
			)
			into(rootProject.file("output/$modVersion-nodowngrader"))
			dependsOn("build")
		}
	}

	private fun Project.configurePublishing(
		ext: ModPlatformExtension,
		loader: String,
		stonecutter: StonecutterBuildExtension,
		modVersion: String,
		displayVersion: String,
	) {
		val additionalVersions = (findProperty("publish.additionalVersions") as String?)?.split(',')?.map(String::trim)
			?.filter(String::isNotEmpty).orEmpty()

		val releaseTypeRaw = prop("publish.release.$loader").ifBlank { "stable" }
		val releaseType = ReleaseType.of(
			releaseTypeRaw.let { if (it == "dev") "beta" else it }
		)

		extensions.configure<ModPublishExtension>("publishMods") {
			//val mrStaging = prop("publish.modrinth.staging") == "true"
			val mrStaging = false

			val modrinthAccessToken = env("MODRINTH_API_TOKEN")
			val curseforgeAccessToken = env("CURSEFORGE_API_TOKEN")
			val githubAccessToken = env("GITHUB_TOKEN")

			val modrinthProjectId = prop("publish.modrinth.id")
			val curseforgeProjectId = prop("publish.curseforge.id")

			val modrinthPublish = prop("publish.modrinth") == "true"
			val curseforgePublish = prop("publish.curseforge") == "true"
			val githubPublish = prop("publish.github") == "true"

			if (prop("publish.dryrun") == "true") {
				dryRun = true
			}

			val targetName = ext.jarTask.get()
			val jarTask = tasks.named(targetName).map { it as Jar }
			val currentVersion = prop("deps.minecraft")
			val deps = ext.dependencies

			file.set(jarTask.flatMap(Jar::getArchiveFile))
			type = releaseType
			version = displayVersion
			if (displayVersion.length > 32) {
				version = displayVersion.replace("snapshot", "snap").take(32)
			}
			val changelogFile = rootProject.file("CHANGELOG.md").readText()
			changelog.set(changelogFile.replace("\n","\n\n"))
			modLoaders.add(loader)
			if (loader == "fabric") {
				modLoaders.add("quilt")
			}
			if (loader == "forge" && stonecutter.current.version == "1.20" && additionalVersions.size <= 1) {
				modLoaders.add("neoforge")
			}

			val mcVersionRange = if (additionalVersions.isNotEmpty()) "$currentVersion-${additionalVersions.last()}" else currentVersion
			val fullDisplayName = "${prop("mod.name")} $modVersion for ${loader.replaceFirstChar(Char::titlecase)} $mcVersionRange"

			displayName = if (fullDisplayName.length > 64) {
				fullDisplayName.take(64).trim()
			} else {
				fullDisplayName
			}

			if (modrinthPublish && !modrinthAccessToken.isNullOrBlank() && modrinthProjectId.isNotBlank()) {
				modrinth(deps, currentVersion, additionalVersions, mrStaging, modrinthAccessToken)
			}

			if (curseforgePublish && !curseforgeAccessToken.isNullOrBlank() && curseforgeProjectId.isNotBlank()) {
				if (!mrStaging) curseforge(deps, currentVersion, additionalVersions, false, curseforgeAccessToken)
			}

			if (githubPublish && !githubAccessToken.isNullOrBlank()) {
				github {
					accessToken = githubAccessToken
					parent(project(":").tasks.named("publishGithub"))
				}
			}
		}
	}

	fun whenNotNull(stringProp: Property<String>, action: (String) -> Unit) {
		if (!stringProp.orNull.isNullOrBlank()) action(stringProp.get())
	}

	private fun ModPublishExtension.modrinth(
		deps: DependenciesConfig,
		currentVersion: String,
		additionalVersions: List<String>,
		staging: Boolean,
		accessToken: String?
	) = modrinth {
		if (staging) apiEndpoint = "https://staging-api.modrinth.com/v2"
		projectId = project.prop("publish.modrinth.id")
		this.accessToken = accessToken
		minecraftVersions.addAll(listOf(currentVersion) + additionalVersions)

		// Environment
		environment = ModrinthEnvironment.SERVER_ONLY_CLIENT_OPTIONAL

		if (!staging) {
			deps.required.forEach { dep -> whenNotNull(dep.modrinth) { requires(it) } }
			deps.optional.forEach { dep -> whenNotNull(dep.modrinth) { optional(it) } }
			deps.incompatible.forEach { dep -> whenNotNull(dep.modrinth) { incompatible(it) } }
			deps.embeds.forEach { dep -> whenNotNull(dep.modrinth) { embeds(it) } }
		}
	}

	private fun ModPublishExtension.curseforge(
		deps: DependenciesConfig,
		currentVersion: String,
		additionalVersions: List<String>,
		staging: Boolean,
		accessToken: String?
	) = curseforge {
		projectId = project.prop("publish.curseforge.id")
		this.accessToken = accessToken
		minecraftVersions.addAll(listOf(currentVersion) + additionalVersions)

		// Environment
		server = true
		client = false

		deps.required.forEach { dep -> whenNotNull(dep.curseforge) { requires(it) } }
		deps.optional.forEach { dep -> whenNotNull(dep.curseforge) { optional(it) } }
		deps.incompatible.forEach { dep -> whenNotNull(dep.curseforge) { incompatible(it) } }
		deps.embeds.forEach { dep -> whenNotNull(dep.curseforge) { embeds(it) } }
	}

	private fun configureStonecutterReplacements(stonecutter: StonecutterBuildExtension) {
		stonecutter.replacements.string(stonecutter.eval(stonecutter.current.version, ">=1.21.11"), "!renames_1_21_11") {
			replace("ResourceLocation", "Identifier")
			replace("location()", "identifier()")
		}
	}
}
