# CampusUTE backend — multi-stage build
# (P8: publish to ghcr.io/jasontm17/campusute-backend)
FROM gradle:8.14.3-jdk21 AS build
WORKDIR /home/gradle/project
COPY . .
RUN gradle bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /home/gradle/project/build/libs/*.jar app.jar
EXPOSE 8080
USER 1000
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
