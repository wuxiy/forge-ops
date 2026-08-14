# 构建上下文 = 仓库根目录（需要 registry/ 一并带入）
# docker build -t forgeops-gateway:dev -f gateway/forgeops-gateway/Dockerfile .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY gateway/forgeops-gateway/pom.xml ./gateway/forgeops-gateway/pom.xml
RUN cd gateway/forgeops-gateway && mvn -q -DskipTests dependency:go-offline
COPY gateway/forgeops-gateway ./gateway/forgeops-gateway
RUN cd gateway/forgeops-gateway && mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/gateway/forgeops-gateway/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
