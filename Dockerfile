FROM eclipse-temurin:17
COPY . /usr/src/contacts-birthday
WORKDIR /usr/src/contacts-birthday
RUN ["./gradlew", "build", "-x", "test"]
EXPOSE 8080/tcp
ENTRYPOINT ["java", "-jar", "build/libs/contacts-birthday-0.0.1-SNAPSHOT.jar"]
