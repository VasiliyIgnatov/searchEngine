FROM maven:3.8.7-amazoncorretto-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests
FROM amazoncorretto:17
COPY --from=build /app/target/SearchEngine-1.0-SNAPSHOT.jar /app/SearchEngine-1.0-SNAPSHOT.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/SearchEngine-1.0-SNAPSHOT.jar"]
