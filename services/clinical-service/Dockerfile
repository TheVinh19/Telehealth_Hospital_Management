FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY build/libs/clinical-service.jar app.jar
RUN mkdir -p storage/clinical-reports
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
