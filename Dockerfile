FROM openjdk:22-ea-17-jdk-slim
EXPOSE 8080

ARG CERT
ARG JAR_FILE=target/datahub-service-search-0.0.1-SNAPSHOT.jar

# Install cURL to perform ECS health check
RUN apt update && apt install -y curl

#Instruction to copy files from local source to container target
COPY ${JAR_FILE} app.jar

ENTRYPOINT java -jar app.jar
