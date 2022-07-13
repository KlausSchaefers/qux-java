FROM maven:3-openjdk-18 as builder

WORKDIR /app

COPY ["pom.xml", "./"]
COPY ["src/", "./src"]
RUN mvn -B package --file pom.xml



FROM openjdk:19-jdk-alpine as runner

WORKDIR /app

COPY ["emails/", "./emails"]
COPY ["matc.conf", "./"]
COPY --from=builder ["/app/release/qux-server.jar", "./"]

CMD [ "java", "-jar",  "qux-server.jar", "-Xmx2g", "-conf", "matc.conf", "-instances 1" ]
