
# # Use OpenJDK 17 as the base image
# FROM eclipse-temurin:17-jdk-jammy AS builder
#
# WORKDIR /app
#
# # Copy Maven wrapper and pom.xml
# COPY .mvn/ .mvn
# COPY mvnw pom.xml ./
# RUN chmod +x mvnw
#
# # Resolve dependencies
# RUN ./mvnw dependency:go-offline
#
# # Copy source and build the jar
# COPY src ./src
# # RUN ./mvnw package -DskipTests
# RUN ./mvnw clean install -DskipTests
# # Use a smaller image to run the app
# FROM eclipse-temurin:17-jdk-jammy
# WORKDIR /app
# COPY --from=builder /app/target/fashions-0.0.1-SNAPSHOT.jar /app/fashions.jar
#
# CMD ["java", "-jar", "fashions.jar"]

# Use OpenJDK 17 as the base image
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Resolve dependencies
RUN ./mvnw dependency:go-offline

# Copy source and build the jar
COPY src ./src
RUN ./mvnw clean install -DskipTests

# Use a smaller image to run the app
FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

COPY --from=builder /app/target/fashions-0.0.1-SNAPSHOT.jar /app/fashions.jar

# Let profile be set externally using ENV variable SPRING_PROFILES_ACTIVE
ENV SPRING_PROFILES_ACTIVE=production

CMD ["java", "-jar", "fashions.jar", "--spring.profiles.active=${SPRING_PROFILES_ACTIVE}"]

