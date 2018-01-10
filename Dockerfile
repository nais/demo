FROM navikt/java:8

WORKDIR /app

COPY build/libs/demo-0.0.1.jar /app/app.jar

