# syntax=docker/dockerfile:1

# ====================================================================
# Etapa 1: build — compila el jar con Maven (no necesitas Maven local)
# ====================================================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cachea dependencias: copia solo el pom y descargalas primero.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B dependency:go-offline

# Copia el codigo y empaqueta (sin tests para acelerar la imagen).
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B clean package -DskipTests

# ====================================================================
# Etapa 2: extrae las capas del jar de Spring Boot
# Permite que Docker cachee dependencias por separado del codigo propio,
# acelerando los rebuilds y reduciendo lo que se reenvia en cada deploy.
# ====================================================================
FROM eclipse-temurin:21-jre-jammy AS layers
WORKDIR /app
COPY --from=build /app/target/dmn-sidecar-0.0.1-SNAPSHOT.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# ====================================================================
# Etapa 3: runtime — imagen final ligera, solo JRE, usuario no-root
# ====================================================================
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# curl para el healthcheck.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Usuario no-root por seguridad.
RUN groupadd --system spring && useradd --system --gid spring spring
USER spring:spring

# Copia las capas en orden de menor a mayor frecuencia de cambio.
COPY --from=layers /app/dependencies/ ./
COPY --from=layers /app/spring-boot-loader/ ./
COPY --from=layers /app/snapshot-dependencies/ ./
COPY --from=layers /app/application/ ./

EXPOSE 8080

# Tuning de JVM para contenedores:
#  - MaxRAMPercentage: usa hasta 75% de la RAM asignada al contenedor.
#  - +UseG1GC: recolector adecuado para servicios con baja latencia.
#  - +ExitOnOutOfMemoryError: si se queda sin memoria, muere y el orquestador lo reinicia.
# Sobreescribible con la variable JAVA_OPTS en compose / k8s.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"

# Healthcheck: consulta el endpoint de liveness de Actuator.
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
