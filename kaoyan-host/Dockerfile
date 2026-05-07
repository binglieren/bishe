FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY target/kaoyan-host-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]
