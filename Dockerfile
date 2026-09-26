FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline
COPY src/ src/
# Run the isolated regression suite; explicit PostgreSQL ITs are operator-only.
RUN ./mvnw -B -ntp package

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --gid 10001 app && useradd --uid 10001 --gid app --no-create-home app
WORKDIR /app
COPY --from=build --chown=app:app /build/target/airline-exam-prep-bot-0.0.1.jar app.jar
USER 10001:10001
EXPOSE 10000
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50.0", "-jar", "/app/app.jar"]
