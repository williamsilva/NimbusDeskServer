# syntax=docker/dockerfile:1
# Nota: se um deploy no Railway falhar na inicialização com algo como
# "ls: cannot access '*/build/libs/*jar'" mesmo com o build acima concluído com sucesso, é cache
# de build corrompido/parcial no Railway (não um problema deste Dockerfile - ENTRYPOINT abaixo
# nunca chama ls) - geralmente após uma tentativa anterior falhar no meio do processo. Um commit
# novo (mesmo pequeno, tocando este arquivo) força invalidar o cache e resolve.
#
# Nota 2 (2026-08-18): também já vimos o Build > Build image falhar direto, sem log nenhum na aba
# Build Logs e sem botão de redeploy/retry na UI, num commit que builda limpo local (Gradle +
# docker compose build) - mesmo diagnóstico (infra do Railway, não este Dockerfile/código). Fix
# idêntico: um commit novo pra forçar uma tentativa de build do zero.
# Nota 3 (2026-09-06): "Could not resolve com.nimbussystems:nimbus-commons-server... Username must
# not be null!" no ./gradlew bootJar, mesmo com GITHUB_ACTOR/GITHUB_TOKEN configurados nas
# Variables do serviço no Railway - variáveis de serviço só chegam ao CONTAINER em runtime, nunca
# ao processo de build do Dockerfile, a menos que sejam declaradas explicitamente com ARG no stage
# que precisa delas (Railway então injeta como --build-arg automaticamente). Sem os ARG abaixo,
# System.getenv("GITHUB_ACTOR")/("GITHUB_TOKEN") do build.gradle.kts sempre voltam null aqui dentro.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
ARG GITHUB_ACTOR
ARG GITHUB_TOKEN
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --version
COPY src ./src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
# Nota 4 (2026-09-07): achado real revisando as variáveis do Railway - JAVA_OPTS estava
# configurada no serviço (-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport) mas nunca tinha efeito
# nenhum: ENTRYPOINT em forma exec (array JSON) não passa por shell nenhum, então "${JAVA_OPTS}"
# nunca seria expandido mesmo se estivesse escrito aqui. Forma shell (mesmo padrão já usado no
# CardsyncServer/NimbusAuthServer) resolve.
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
