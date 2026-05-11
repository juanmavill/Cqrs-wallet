# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml .
COPY command-service/pom.xml command-service/
COPY query-service/pom.xml query-service/
COPY monolith-reference/pom.xml monolith-reference/
COPY monolith-reference/src monolith-reference/src

RUN mvn -B -q -pl monolith-reference -am -DskipTests package

# Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/monolith-reference/target/monolith-reference.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
