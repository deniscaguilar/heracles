# Mesma imagem para os perfis api, worker e all: o perfil vem de SPRING_PROFILES_ACTIVE.
FROM eclipse-temurin:25-jdk@sha256:884a2f8692cc1e2c7e21f95914adf0ae0eafee9a7eb663a12169cc44468256ec AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package \
    && java -Djarmode=tools -jar target/heracles-backend-*.jar extract --layers --destination /build/layers

FROM eclipse-temurin:25-jre@sha256:945247f9d092703e0c6be92243ef3c51640229e23ab34d353c8dcdc73e6cd4e6
RUN useradd --system --uid 10001 --no-create-home --shell /usr/sbin/nologin heracles
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/layers/dependencies/ ./
COPY --from=build --chown=10001:10001 /build/layers/spring-boot-loader/ ./
COPY --from=build --chown=10001:10001 /build/layers/snapshot-dependencies/ ./
COPY --from=build --chown=10001:10001 /build/layers/application/ ./
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/heracles-backend-0.0.1-SNAPSHOT.jar"]
