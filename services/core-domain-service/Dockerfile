# Imagen construida desde la raíz del monorepo:
#   docker build -f services/core-domain-service/Dockerfile -t etribunal/core-domain-service .
FROM eclipse-temurin:25-jre

WORKDIR /app
RUN useradd --system --uid 1001 app

COPY services/core-domain-service/build/libs/*.jar app.jar

USER app
EXPOSE 8082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
