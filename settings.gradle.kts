rootProject.name = "agent-webmcp"

sourceControl {
    gitRepository(uri("https://github.com/TavallStudios/tavall-logging.git")) {
        producesModule("org.tavall:tavall-logging")
    }
    gitRepository(uri("https://github.com/TavallStudios/tavall-di.git")) {
        producesModule("org.tavall:tavall-di")
    }
    gitRepository(uri("https://github.com/TavallStudios/tavall-concurrency.git")) {
        producesModule("org.tavall:tavall-concurrency")
    }
    gitRepository(uri("https://github.com/TavallStudios/tavall-registry.git")) {
        producesModule("org.tavall:tavall-registry")
    }
    // tavall-registry currently exposes abstract-cache-system as an API dependency.
    // Pin its canonical public source as well so this public repository does not require
    // a private GitHub Packages credential merely to compile the shared registry.
    gitRepository(uri("https://github.com/TavallStudios/tavall-cache.git")) {
        producesModule("org.tavall:abstract-cache-system")
    }
}
