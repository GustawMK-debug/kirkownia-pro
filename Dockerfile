FROM eclipse-temurin:17-jdk-alpine
COPY . .
RUN ./mvnw clean install -DskipTests
EXPOSE 8080
CMD ["java", "-jar", "target/kirk3-0.0.1-SNAPSHOT.jar"]