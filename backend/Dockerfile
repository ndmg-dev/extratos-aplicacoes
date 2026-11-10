# ---- build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
# baixa o Gradle e as dependências primeiro (cache de camadas)
RUN ./gradlew --no-daemon dependencies >/dev/null 2>&1 || true
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---- runtime ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN useradd -r -u 1001 app
COPY --from=build /app/build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
