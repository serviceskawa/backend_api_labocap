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

# Racine de stockage des fichiers téléversés (images d'examen, documents, photos
# d'employés, justificatifs…). Elle DOIT exister dans l'image et appartenir à
# appuser AVANT que le volume ne soit monté dessus : quand Docker crée un volume
# nommé sur un chemin absent de l'image, il crée le point de montage en
# root:root 0755. Le conteneur tournant en non-root, toute écriture échouait
# alors avec « Permission denied » et chaque upload partait en erreur, alors
# qu'en local (processus lancé sous le compte du développeur) tout marchait.
# Docker recopie les droits du dossier de l'image dans le volume neuf.
ENV STORAGE_PATH=/var/lib/labo/storage
RUN mkdir -p "$STORAGE_PATH" && chown -R appuser:appgroup /var/lib/labo

USER appuser

EXPOSE 8086

# Variables d'environnement attendues (à fournir au runtime)
ENV DB_URL="" \
    DB_USERNAME="" \
    DB_PASSWORD="" \
    JWT_SECRET="" \
    SPRING_PROFILES_ACTIVE="prod"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
