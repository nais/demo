FROM busybox

WORKDIR /app

COPY build/libs/demo-0.0.1.jar /app/app.jar

FROM navikt/java:8
COPY --from=0 /app/app.jar .
