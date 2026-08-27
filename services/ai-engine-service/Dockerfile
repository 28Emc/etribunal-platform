# Imagen construida desde la raíz del monorepo:
#   docker build -f services/ai-engine-service/Dockerfile -t etribunal/ai-engine-service .
FROM eclipse-temurin:25-jre

WORKDIR /app
RUN useradd --system --uid 1001 app

COPY services/ai-engine-service/build/libs/*.jar app.jar

USER app
EXPOSE 8083
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
