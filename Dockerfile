FROM eclipse-temurin:25-jdk-jammy AS build
WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:25-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 nexo \
    && useradd --uid 10001 --gid nexo --create-home nexo

WORKDIR /app
COPY --from=build --chown=nexo:nexo /build/target/nexo-eventos-1.0.0.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60.0 -XX:+ExitOnOutOfMemoryError -Djava.awt.headless=true -Dpdfbox.fontcache=/tmp"
USER nexo
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
