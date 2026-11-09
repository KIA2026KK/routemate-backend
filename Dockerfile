FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew
COPY src ./src
# Database-backed tests run in CI before main deployment, not inside the image build.
RUN ./gradlew bootJar --no-daemon --console=plain

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --gid 10001 routemate && useradd --system --uid 10001 --gid 10001 --create-home routemate
WORKDIR /app
COPY --from=build --chown=routemate:routemate /workspace/build/libs/*.jar /app/app.jar
USER routemate
ENV PORT=10000
EXPOSE 10000
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65.0", "-jar", "/app/app.jar"]
