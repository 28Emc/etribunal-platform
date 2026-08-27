# Imagen construida desde la raíz del monorepo:
#   docker build -f services/identity-service/Dockerfile -t etribunal/identity-service .
FROM eclipse-temurin:21-jre

WORKDIR /app
RUN useradd --system --uid 1001 app

COPY services/identity-service/build/libs/*.jar app.jar

USER app
EXPOSE 8081

# Graceful shutdown (Spring Boot 3.2+)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Dserver.shutdown=graceful -Dspring.lifecycle.timeout-per-shutdown-phase=30s"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]