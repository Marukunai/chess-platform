# Etapa de build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# Etapa de ejecución
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
# Stockfish para los bots — cambiado de Alpine a esta base (Ubuntu Jammy) a propósito:
# en Alpine, "stockfish" no existe como paquete estable (solo en su repositorio
# "testing", con un bug de compatibilidad con musl documentado desde hace años) —
# Debian/Ubuntu lo empaquetan de forma fiable desde hace años, sin ese problema.
#
# "dpkg -L stockfish" sin filtrar nada, a propósito — ya se me han pasado por alto dos
# suposiciones seguidas sobre el nombre/ruta exacta del binario ("which stockfish" fallaba
# por el $PATH, y luego "grep bin/stockfish" tampoco encontró nada), así que en vez de
# adivinar una tercera vez, esto imprime la lista COMPLETA de archivos que el paquete
# instaló de verdad — la ruta real del binario está ahí, visible en el log del build,
# sin que ningún filtro mío pueda esconderla o hacer fallar el build por equivocarme.
RUN apt-get update && \
    apt-get install -y --no-install-recommends stockfish && \
    rm -rf /var/lib/apt/lists/* && \
    dpkg -L stockfish
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]