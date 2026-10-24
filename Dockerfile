FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

COPY chat-server/pom.xml ./chat-server/pom.xml
COPY chat-server/src ./chat-server/src

WORKDIR /build/chat-server
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache sqlite-libs
COPY --from=builder /build/chat-server/target/chat-server-1.0-SNAPSHOT.jar app.jar

RUN mkdir -p /app/data /app/server_storage

EXPOSE 8888
ENV PORT=8888

CMD ["java", "-jar", "app.jar"]
