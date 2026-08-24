# Imagen construida desde la raíz del monorepo:
#   docker build -f services/gateway-service/Dockerfile -t etribunal/gateway-service .
FROM eclipse-temurin:21-jre

WORKDIR /app
RUN useradd --system --uid 1001 app

COPY services/gateway-service/build/libs/*.jar app.jar

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
