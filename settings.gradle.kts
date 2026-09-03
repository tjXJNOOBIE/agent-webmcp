rootProject.name = "agent-webmcp"

listOf(
    "tavall-logging",
    "tavall-di",
    "tavall-cache",
    "tavall-registry",
    "tavall-concurrency",
    "tavall-scheduler"
).forEach { dependency ->
    val source = file(".tavall-source-deps/$dependency")
    if (source.isDirectory) {
        includeBuild(source)
    }
}

sourceControl {
    gitRepository(uri("https://github.com/TavallStudios/tavall-logging.git")) { producesModule("org.tavall:tavall-logging") }
    gitRepository(uri("https://github.com/TavallStudios/tavall-di.git")) { producesModule("org.tavall:tavall-di") }
    gitRepository(uri("https://github.com/TavallStudios/tavall-concurrency.git")) { producesModule("org.tavall:tavall-concurrency") }
    gitRepository(uri("https://github.com/TavallStudios/tavall-registry.git")) { producesModule("org.tavall:tavall-registry") }
    gitRepository(uri("https://github.com/TavallStudios/tavall-scheduler.git")) { producesModule("org.tavall:tavall-scheduler") }
    // tavall-registry exposes abstract-cache-system as an API dependency.
    gitRepository(uri("https://github.com/TavallStudios/tavall-cache.git")) { producesModule("org.tavall:abstract-cache-system") }
}
