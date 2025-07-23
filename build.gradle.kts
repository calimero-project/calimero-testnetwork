plugins {
	java
	application
	eclipse
	id("com.github.ben-manes.versions") version "0.52.0"
	`maven-publish`
	signing
}

application {
	mainModule.set("io.calimero.testnetwork")
	mainClass.set("io.calimero.testnetwork.TestNetwork")
}

tasks.withType<JavaExec> {
	@Suppress("UNCHECKED_CAST")
	systemProperties(System.getProperties() as Map<String, *>)
	args("src/main/resources/server-config.xml")
	standardInput = System.`in`
}

tasks.compileJava {
	options.encoding = "UTF-8"
}
tasks.compileTestJava {
	options.encoding = "UTF-8"
}
tasks.javadoc {
	options.encoding = "UTF-8"
}

group = "io.calimero"
version = "3.0-SNAPSHOT"

repositories {
	mavenLocal()
	mavenCentral()
	maven("https://central.sonatype.com/repository/maven-snapshots/")
}

tasks.compileJava {
	options.compilerArgs.addAll(listOf(
		"-Xlint:all",
		"-Xlint:-options"
	))
}

java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(21))
	}
	withSourcesJar()
	withJavadocJar()
}

tasks.withType<Jar>().configureEach {
	from("$projectDir") {
		include("LICENSE")
		into("META-INF")
	}
	if (name == "sourcesJar") {
		from("$projectDir") {
			include("README.md", "build.gradle", "settings.gradle", "gradle*/**")
		}
	}
}

dependencies {
	implementation("$group:calimero-server:$version")
	implementation("$group:calimero-core:$version")
	implementation("$group:calimero-device:$version")

	runtimeOnly("org.slf4j:slf4j-jdk-platform-logging:2.0.17")
	runtimeOnly("org.slf4j:slf4j-simple:2.0.17")
}

publishing {
	publications {
		create<MavenPublication>("mavenJava") {
			artifactId = rootProject.name
			from(components["java"])
			pom {
				name.set("Calimero test network")
				description.set("Test network with KNXnet/IP server and virtual subnet")
				url.set("https://github.com/calimero-project/calimero-testnetwork")
				licenses {
					license {
						name.set("GNU General Public License, version 2, with the Classpath Exception")
						url.set("LICENSE")
					}
				}
				developers {
					developer {
						name.set("Boris Malinowsky")
						email.set("b.malinowsky@gmail.com")
					}
				}
				scm {
					connection.set("scm:git:git://github.com/calimero-project/calimero-testnetwork.git")
					url.set("https://github.com/calimero-project/calimero-testnetwork.git")
				}
			}
		}
	}
	repositories {
		maven {
			name = "maven"
			val releasesRepoUrl = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
			val snapshotsRepoUrl = uri("https://central.sonatype.com/repository/maven-snapshots/")
			url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
			credentials(PasswordCredentials::class)
		}
	}
}

signing {
	if (project.hasProperty("signing.keyId")) {
		sign(publishing.publications["mavenJava"])
	}
}
