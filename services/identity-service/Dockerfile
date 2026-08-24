# Imagen construida desde la raíz del monorepo:
#   docker build -f services/identity-service/Dockerfile -t etribunal/identity-service .
FROM eclipse-temurin:21-jre

WORKDIR /app
RUN useradd --system --uid 1001 app

COPY services/identity-service/build/libs/*.jar app.jar

USER app
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
