# syntax=docker/dockerfile:1
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    --mount=type=secret,id=maven_settings,target=/tmp/maven-settings.xml \
    if [ -s /tmp/maven-settings.xml ]; then \
      build_maven_settings=/tmp/maven-settings.xml; \
    else \
      build_maven_settings=/usr/share/maven/conf/settings.xml; \
    fi; \
    mvn --settings "$build_maven_settings" --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN command -v curl >/dev/null \
    && groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
RUN mkdir -p /app/data/uploads && chown -R 10001:10001 /app/data
COPY --from=build --chown=10001:10001 /build/target/liushao_community_backend-0.0.1-SNAPSHOT.jar /app/app.jar
ENV SPRING_PROFILES_ACTIVE=prod
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl --fail --silent --show-error --max-time 4 http://127.0.0.1:${SERVER_PORT:-8080}/actuator/health/readiness > /dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
