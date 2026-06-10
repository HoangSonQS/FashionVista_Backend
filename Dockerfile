FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml first for better caching
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
COPY mvnw.cmd .

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B || true

# Copy source code
COPY src ./src

# Build application
RUN mvn clean package -DskipTests

# Verify WAR file was created
RUN ls -la /app/target/*.war || (echo "WAR file not found!" && ls -la /app/target/ && exit 1)

# Run stage
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy WAR file from build stage
COPY --from=build /app/target/FashionVista_Backend-0.0.1-SNAPSHOT.war Sixthsoul.war

RUN mkdir -p /app/uploads
VOLUME ["/app/uploads"]

EXPOSE 8085

ENTRYPOINT ["java","-jar","Sixthsoul.war"]
