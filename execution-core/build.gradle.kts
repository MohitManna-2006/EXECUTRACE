plugins { `java-library` }

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.1")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testImplementation("org.assertj:assertj-core:3.27.6")
    testImplementation("net.jqwik:jqwik:1.9.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }
tasks.withType<JavaCompile>().configureEach { options.release = 21 }

val jmh by sourceSets.creating {
    java.srcDir("src/jmh/java")
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += output + compileClasspath
}
configurations[jmh.implementationConfigurationName].extendsFrom(configurations.implementation.get())

dependencies {
    add(jmh.implementationConfigurationName, "org.openjdk.jmh:jmh-core:1.37")
    add(jmh.annotationProcessorConfigurationName, "org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

tasks.register<JavaExec>("jmhPhase1") {
    dependsOn(tasks.named(jmh.classesTaskName))
    classpath = jmh.runtimeClasspath
    mainClass = "org.openjdk.jmh.Main"
    args("io.executrace.core.ReducerBenchmark", "-wi", "2", "-i", "3", "-w", "1s", "-r", "1s", "-f", "1")
}
