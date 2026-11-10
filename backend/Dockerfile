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
# Usuário não-root + diretório de storage com dono correto.
# Um volume nomeado montado em /app/storage herda este dono quando criado vazio,
# permitindo que o app (uid 1001) escreva os PDFs arquivados.
RUN useradd -r -u 1001 app \
 && mkdir -p /app/storage \
 && chown -R app:app /app/storage
COPY --from=build /app/build/libs/*.jar app.jar
USER app
EXPOSE 8080
VOLUME ["/app/storage"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
