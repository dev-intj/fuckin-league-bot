FROM openjdk:16

COPY .  .

RUN sh gradlew shadowJar;

#Entrypoint runs when container actually starts!!!
ENTRYPOINT ["java", "-jar", "/build/libs/Freddy-1.0-SNAPSHOT-all.jar"]
