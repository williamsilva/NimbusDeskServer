plugins {
	java
	id("org.springframework.boot") version "4.0.2"
	id("io.spring.dependency-management") version "1.1.6"
}

group = "com.nimbusdesk"
version = "0.1.0-SNAPSHOT"

// Mesma versão já usada pelo NimbusFlowServer (build.gradle.kts de lá) pro storage de anexos
// (com.nimbusdesk.common.storage, MinIO S3-compatible) - módulo de negócio "Chamados de TI".
val awssdkVersion = "2.46.18"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
	maven {
		name = "GitHubPackages"
		url = uri("https://maven.pkg.github.com/williamsilva/NimbusCommonsServer")
		credentials {
			username = (project.findProperty("gpr.user") as String?) ?: System.getenv("GITHUB_ACTOR")
			password = (project.findProperty("gpr.token") as String?) ?: System.getenv("GITHUB_TOKEN")
		}
	}
}

dependencies {
	// Base do NimbusDesk (2026-09-07) criada a partir do NimbusNovaxServer/NimbusFlowServer -
	// com.nimbusdesk.common (config/security/notification.mail/backup) reaproveita o que já foi
	// extraído pro NimbusCommonsServer em vez de duplicar (ver README do NimbusCommonsServer pro
	// que NÃO foi extraído e por quê).
	implementation("com.nimbussystems:nimbus-commons-server:0.6.0")

	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-hateoas")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
	implementation("org.springframework.session:spring-session-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	// @EnableCaching (NimbusDeskApplication) - Boot 4 tirou a autoconfiguração de cache do jar
	// monolítico de autoconfigure e virou módulo/starter próprio (mesmo achado do NimbusFlowServer);
	// sem isto, @EnableCaching falha a subida do contexto por falta de CacheManager.
	implementation("org.springframework.boot:spring-boot-starter-cache")

	// Boot 4 moveu a autoconfiguração do Flyway pro módulo/starter próprio
	// spring-boot-starter-flyway (achado real no NimbusFlowServer: sem isto, o Flyway nunca dispara
	// e o Hibernate falha a validação de schema). Mesmo padrão já em produção no
	// CardsyncServer/NimbusAuthServer.
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.flywaydb:flyway-database-postgresql")

	// com.nimbusdesk.common.storage (StorageService/S3StorageService, MinIO S3-compatible) - anexos
	// de Chamado, portado de com.nimbusflow.common.storage (mesma versão do AWS SDK usada lá).
	implementation(platform("software.amazon.awssdk:bom:$awssdkVersion"))
	implementation("software.amazon.awssdk:s3")
	implementation("software.amazon.awssdk:url-connection-client")

	// com.nimbussystems.commons.audit (@Auditable/AuditAspect via Spring AOP). Boot 4 renomeou
	// spring-boot-starter-aop -> spring-boot-starter-aspectj (mesmo achado do NimbusCommonsServer/
	// NimbusFlowServer/NimbusNovaxServer).
	implementation("org.springframework.boot:spring-boot-starter-aspectj")

	// com.nimbussystems.commons.notification.mail (Configuração de E-mail + com.nimbusdesk.common.
	// notification.mail, Auditoria de E-mail local) - mesmas starters usadas pelo NimbusAuth/
	// CardsyncServer/NimbusFlowServer/NimbusNovaxServer pra isso.
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("org.springframework.boot:spring-boot-starter-mail")

	runtimeOnly("org.postgresql:postgresql")

	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
