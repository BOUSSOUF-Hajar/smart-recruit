# Backend SmartRecruit (US-54) : build Maven, puis JRE 25 seul, sous un utilisateur non root.

# --- 1. Build ---------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
# Dépendances d'abord : la couche (et le cache Maven) sont réutilisés tant que le pom ne change pas
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp dependency:go-offline
COPY src ./src
# Les tests tournent dans la pipeline CI, pas pendant la construction de l'image
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp package -DskipTests \
 && java -Djarmode=tools -jar target/backend-*.jar extract --layers --launcher --destination target/extracted

# --- 2. Exécution -----------------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine
# Correctifs de sécurité publiés depuis la construction de l'image de base (l'analyse Trivy de la CI
# refuse toute vulnérabilité critique corrigeable)
# hadolint ignore=DL3017
RUN apk upgrade --no-cache \
 && addgroup -S smartrecruit && adduser -S -G smartrecruit smartrecruit \
 && mkdir -p /data/resumes && chown smartrecruit:smartrecruit /data/resumes
WORKDIR /app
# Couches de la moins à la plus changeante : une modification du code ne repousse que la dernière
COPY --from=build /build/target/extracted/dependencies/ ./
COPY --from=build /build/target/extracted/spring-boot-loader/ ./
COPY --from=build /build/target/extracted/snapshot-dependencies/ ./
COPY --from=build /build/target/extracted/application/ ./
USER smartrecruit
EXPOSE 8080
VOLUME /data/resumes
# Mémoire du tas proportionnelle à la limite du conteneur ; arrêt net plutôt qu'un état dégradé
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    STORAGE_ROOT=/data/resumes
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health/readiness | grep -q '"UP"' || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
