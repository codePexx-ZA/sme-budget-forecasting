# Builds the Spring Boot API the Android app calls.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY src src
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew bootJar --no-daemon \
    && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /budget-api.jar \;

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /budget-api.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
