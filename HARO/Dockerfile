FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src/ src/

RUN mvn -q -DskipTests clean package && \
    WAR_PATH="$(ls target/*.war | grep -v '\\.original$' | head -n 1)" && \
    cp "$WAR_PATH" /app/app.war

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

ENV JAVA_OPTS=""

COPY --from=build /app/app.war /app/app.war

EXPOSE 8080

CMD ["sh", "-c", "if [ -z \"$DB_URL\" ] && [ -n \"$DATABASE_URL\" ]; then case \"$DATABASE_URL\" in jdbc:*) export DB_URL=\"$DATABASE_URL\" ;; *) export DB_URL=\"jdbc:$DATABASE_URL\" ;; esac; fi; java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar /app/app.war"]
