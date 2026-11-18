# --- build ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
# Roda os testes no build: se algum falhar, o deploy não acontece.
RUN mvn -B package

# --- run ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/pato-bot.jar app.jar
# Render free tem 512 MB de RAM
ENV JAVA_OPTS="-Xmx350m -XX:+UseSerialGC"
EXPOSE 8080
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
