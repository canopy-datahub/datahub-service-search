FROM openjdk:22-ea-17-jdk-slim
EXPOSE 8080

ARG CERT
ARG JAR_FILE=target/search-v3-0.0.1-SNAPSHOT.jar

ENV SEARCH_USERNAME=opensearch
ENV SEARCH_PASSWORD=123!


#Instruction to copy files from local source to container target
COPY ${JAR_FILE} app.jar

ENTRYPOINT java -jar app.jar