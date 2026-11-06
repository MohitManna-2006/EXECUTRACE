plugins { base }

allprojects {
    group = "io.executrace"
    version = "0.1.0-SNAPSHOT"
    repositories { mavenCentral() }
    dependencyLocking { lockAllConfigurations() }
}
