# =====================================================
# STAGE 1: BUILD — Maven + JDK 17
# =====================================================
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Кеш залежностей: спочатку pom.xml
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Потім весь код
COPY src ./src
RUN mvn clean package -DskipTests

# =====================================================
# STAGE 2: RUNTIME — JRE 17 (alpine)
# =====================================================
FROM eclipse-temurin:17-jre-alpine

# dumb-init для правильних сигналів (SIGTERM → graceful shutdown)
RUN apk add --no-cache dumb-init

# Non-root user
RUN addgroup -g 1000 spring && adduser -u 1000 -G spring -s /bin/sh -D spring

WORKDIR /app

# Директорія для H2 (монтується Volume)
RUN mkdir -p /app/data && chown -R spring:spring /app

# JAR з build-stage
COPY --from=build /app/target/*.jar app.jar
RUN chown spring:spring app.jar

USER spring:spring

EXPOSE 8080

# Healthcheck для Fly auto_stop_machines
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["dumb-init", "--"]

# PORT підхоплюється з application.yaml через ${PORT:8080}
CMD ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]