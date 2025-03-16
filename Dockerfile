#FROM maven:3.8.7-amazoncorretto-17 AS build
FROM openjdk:17-jdk-alpine
WORKDIR /app
COPY target/SearchEngine-1.0-SNAPSHOT.jar SearchEngine.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "SearchEngine.jar"]
