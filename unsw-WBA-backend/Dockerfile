# The first stage: Maven compilation and packaging
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /app
# Copy pom.xml and source code
COPY pom.xml .
# Copy all submodules and their parent pom.xml files from the current root directory directly into the container
COPY . .
# Because multiple modules are used, if it's a parent-child project, please ensure that the pom and src files of all modules are copied
# Skip tests when packaging
RUN mvn clean package -DskipTests

# The second stage: Operating Environment
FROM eclipse-temurin:25-jre
WORKDIR /app

# In multi-module projects, the final generated Spring Boot executable JAR file is usually located in the module containing the startup class
# The module responsible for providing the API interface is unsw-WBA-api, so the JAR file will be located in its target directory
COPY --from=builder /app/unsw-WBA-api/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]