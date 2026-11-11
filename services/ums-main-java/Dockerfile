FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# 实际产物名（pom artifactId=ums-main）：ums-main-0.1.0-SNAPSHOT.jar
COPY target/ums-main-*.jar app.jar
EXPOSE 8080 9999
ENTRYPOINT ["java", "-jar", "app.jar"]