# =====================================================
# STAGE 1: BUILD — Maven + JDK 17
# =====================================================
FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /build

# Кешуємо залежності Maven: спочатку копіюємо тільки pom.xml
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Потім весь код і збірка
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# =====================================================
# STAGE 2: RUNTIME — JRE 17 (alpine = маленький)
# =====================================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Non-root user (безпека)
RUN addgroup -S spring && adduser -S spring -G spring

# Директорія для H2 — сюди буде монтуватись Fly Volume
RUN mkdir -p /app/data && chown -R spring:spring /app

# Копіюємо jar з build-stage
COPY --from=build /build/target/*.jar app.jar
RUN chown spring:spring app.jar

USER spring

EXPOSE 8080

# Healthcheck для Fly (перевіряє /actuator/health)
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]