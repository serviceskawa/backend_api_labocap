# ─── Stage 1 : build ─────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /build

# Téléchargement des dépendances en cache séparé
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Compilation
COPY src/ src/
RUN mvn package -Dmaven.test.skip=true -B

# ─── Stage 2 : runtime ───────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

# Utilisateur non-root pour la sécurité
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=build /build/target/app.jar app.jar

RUN chown appuser:appgroup app.jar

USER appuser

# Port d'écoute dans le conteneur. Doit rester identique à PORT ci-dessous :
# l'application lit server.port=${PORT}, et actuator n'a pas de port distinct,
# donc le healthcheck du compose interroge ce même port.
EXPOSE 7001

# Variables d'environnement attendues (à fournir au runtime)
ENV DB_URL="" \
    DB_USERNAME="" \
    DB_PASSWORD="" \
    JWT_SECRET="" \
    PORT="7001" \
    SPRING_PROFILES_ACTIVE="prod"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
