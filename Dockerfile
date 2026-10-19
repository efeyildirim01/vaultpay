# One image with the whole app: the React dashboard is built first and served as static files
# by the Spring Boot jar, so the browser talks to a single origin and no CORS setup is needed.

# 1) Build the dashboard
FROM node:22-alpine AS web
WORKDIR /web
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

# 2) Build the Spring Boot jar with the dashboard inside it
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
# Dependencies in their own layer: rebuilt only when the build files change.
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
COPY --from=web /web/dist src/main/resources/static
RUN ./gradlew --no-daemon bootJar

# 3) Small runtime image, running as a non-root user
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 vaultpay
WORKDIR /app
COPY --from=backend /app/build/libs/vaultpay.jar app.jar
USER vaultpay
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
