FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY target/kaoyan-question-server-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8092
ENTRYPOINT ["java", "-jar", "app.jar"]
