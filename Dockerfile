# Use OpenJDK 11 as the base image
# FROM eclipse-temurin:17-jdk-jammy
#
# Set the working directory inside the container
# WORKDIR /app
#
# ## Copy the Spring Boot application JAR file into the container
# COPY target/fashions-0.0.1-SNAPSHOT.jar /app/fashions.jar
#
# Expose the port that the Spring Boot application uses
# #EXPOSE 8080
#
# Command to run the Spring Boot application when the container starts
# CMD ["java", "-jar", "fashions.jar"]

#FROM eclipse-temurin:17-jdk-jammy
#
#WORKDIR /app
#COPY .mvn/ .mvn
#COPY mvnw pom.xml ./
#
#RUN chmod +x ./mvnw
#
#RUN apt-get update && apt-get install -y dos2unix
#
#RUN dos2unix ./mvnw
#
#RUN ./mvnw dependency:resolve
#
#COPY src ./src
#
#CMD ["./mvnw", "spring-boot:run"]

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
# RUN ./mvnw package -DskipTests
RUN ./mvnw clean install -DskipTests
# Use a smaller image to run the app
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app
COPY --from=builder /app/target/fashions-0.0.1-SNAPSHOT.jar /app/fashions.jar

CMD ["java", "-jar", "fashions.jar"]
