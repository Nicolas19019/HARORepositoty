FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw mvnw
COPY pom.xml .

RUN chmod +x mvnw
RUN ./mvnw -q -DskipTests dependency:go-offline

COPY src/ src/

RUN ./mvnw -q -DskipTests clean package && \
    WAR_PATH="$(ls target/*.war | grep -v '\\.original$' | head -n 1)" && \
    cp "$WAR_PATH" /app/app.war

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

ENV JAVA_OPTS=""

COPY --from=build /app/app.war /app/app.war

EXPOSE 8080

CMD ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar /app/app.war"]
