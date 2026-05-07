FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY target/kaoyan-knowledge-server-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8093
ENTRYPOINT ["java", "-jar", "app.jar"]
