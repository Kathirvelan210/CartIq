FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S cartiq && adduser -S cartiq -G cartiq
WORKDIR /app
COPY --from=build /app/target/smartcart-api-0.2.0.jar app.jar
USER cartiq
EXPOSE 8080
ENTRYPOINT ["java","-Duser.timezone=UTC","-jar","app.jar"]

