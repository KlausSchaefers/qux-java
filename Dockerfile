FROM openjdk:19-jdk-bullseye

RUN mkdir -p /usr/src/qux-java

RUN rm /bin/sh && ln -s /bin/bash /bin/sh

ENV TZ=America/Chicago

RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

WORKDIR /usr/src/qux-java

## Set ENV vars here
ENV QUX_HTTP_HOST=quant.example.com \
    QUX_HTTP_PORT=8080 \
    QUX_MONGO_DB_NAME=quantux \
    QUX_MONGO_TABLE_PREFIX=quantux \
    QUX_MONGO_CONNECTION_STRING=mongodb://mongo:27017 \
    QUX_MAIL_USER=mail_admin@example.com \
    QUX_MAIL_PASSWORD=some-strong-password0! \
    QUX_MAIL_HOST=mail.example.com \
    QUX_JWT_PASSWORD=some-log-string-of-randomized-numbers-characters \
    QUX_IMAGE_FOLDER_USER=/qux-images \
    QUX_IMAGE_FOLDER_APPS=/qux-image-apps \
    QUX_AUTH_SERVICE=qux \
    QUX_KEYCLOAK_SERVER=https://keycloak.example.com \
    QUX_KEYCLOAK_REALM=qux \
    QUX_KEY_CLOAK_CLAIM_ROLE=role \
    QUX_KEY_CLOAK_ISSUER=qux \
    QUX_KEY_CLOAK_CLAIM_ID=id \
    QUX_KEY_CLOAK_CLAIM_EMAIL=email \
    QUX_KEY_CLOAK_CLAIM_NAME=name \
    QUX_USER_ALLOW_SIGNUP=true \
    QUX_USER_ALLOWED_DOMAINS=* \
    QUX_KEY_CLOAK_CLAIM_LASTNAME=lastname

## Clone Quant-ux backend repo with pre-built java backend
RUN git clone https://github.com/KlausSchaefers/qux-java.git

## Run the java backend with this
CMD [ "java", "-jar",  "qux-java/release/qux-server.jar", "-Xmx2g", "-conf", "qux-java/matc.conf", "-instances 1" ]