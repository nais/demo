# NAIS 101

Disse oppgavene tar deg gjennom de grunnleggende stegene for å deploye en applikasjon på NAIS.

For å gjennomføre disse oppgavene kreves det at du har:

- Installert i utviklerimaget:
  - Docker
  - Kubectl og tilgang til NAVs interne clustere
- Tilgang til Fasit

Følg skrittene her [https://confluence.adeo.no/pages/viewpage.action?pageId=259110874](https://confluence.adeo.no/pages/viewpage.action?pageId=259110874) om du ikke har dette installert på utvikler imaget ditt allerde.


## Hva er NAIS?

NAIS står for NAV's application infrastructure service og bygger på [Kubernetes](https://kubernetes.io). I denne demoen vil du både lære å deploye en applikasjon på NAIS og noen grunnleggende operasjoner i Kubernetes.


## Tilgang til preprod-clusteret

Sjekk at du bruker `preprod`:

```
kubectl config use-context preprod-sbs
```


List opp pods for å se at du er autentisert:

```
kubectl get pods
```

## Last ned kildekode

Last ned dette repoet på utviklerimaget ditt.

```
git clone https://github.com/nais/demo.git
```

Det kan hende du må skru av SSL verification:

```
git config --global http.sslVerify false
```


## Docker

For å kjøre applikasjoner på NAIS bruker vi [Docker](https://docker.io). Docker er en containerteknologi hvor vi kan pakke applikasjonen vår og dens avhengigheter inn i et image. Når dette imaget startes, lages det en container hvor applikasjonen kjører etter spesifikasjonene vi har definert da vi lagde imaget.

### Bygg image

Under [src/](./src) ligger det en liten applikasjon som vi vil bygge inn i et Docker image. Ta en titt på [Dockerfile](./Dockerfile) for å se hvordan det bygges.

Bygg appen:

```
./gradlew build
```

Hvis du er på Windows image, kjør `gradlew.bat build` i stedet.

Bygg Docker imaget, pass på å bytte ut `$UNIQUENAME` med noe du vil kalle appen og `$VERSION` med feks `1.0`.

```
docker build . -t docker.adeo.no:5000/$UNIQUENAME:$VERSION
```

Du skal nå ha et docker image tagget med Docker registrien vi skal laste det opp til. 

List opp images for å se at det er lagd:

```
docker images
```

### Test imaget
Test imaget ditt:

```
docker run -d -p 8080 docker.adeo.no:5000/$UNIQUENAME:$VERSION
```

Se at containeren kjører ved å liste alle kjørende containere:

```
docker ps
```

Noter deg portmappingen (`0.0.0.0:12345(random port) -> 8080`) for containeren din. Denne må du bruke for å besøke applikasjonen i nettleseren:

```
http://e34apvl00253.devillo.no:PORT/hello
```

Eller hvis du kjører en lokal Docker daemon:

```
localhost:PORT/hello
```

Stopp containeren din: `docker stop CONTAINER_ID` eller `docker stop CONTAINER_NAME`

### Push til internt NAV docker repo

```
docker push docker.adeo.no:5000/$UNIQUENAME:$VERSION
```

## Deploye til NAIS

Nå som vi har bygd appen vår, vil vi kjøre den i et cluster også. For å gjøre dette må vi først endre på manifestet, `nais.yaml`, slik at denne beskriver appen vår.

### nais.yaml

Åpne nais.yaml og legg inn docker imaget ditt:

```
image: repo.adeo.no:5443/$UNIQUENAME
```

Legg inn team-navn (eks brukernavnet ditt):

```
team: TEAMNAVN
```

Fila nais.yaml må ligge et sted hvor naisd kan få tak i den for å deploye applikasjonen din. Push fila til repo.adeo.no:

```
curl -s -S --user <username>:<password> --upload-file nais.yaml https://repo.adeo.no/repository/raw/nais/$UNIQUENAME/$VERSION/nais.yaml
```

### Deploy

Nå som vi har pushet manifestet nais.yaml og docker imaget til appen vår, er vi klare til å deploye. Dette gjør vi ved en POST request til naisd:

```
curl -s -S -k -d '{"application": "$UNIQUENAME","version": "$VERSION", "fasitEnvironment": "t6", "zone": "sbs", "fasitUsername": "brukernavn", "fasitPassword": "passord", "skipFasit": true}' https://daemon.nais.oera-q.local/deploy
```

Responsen lister opp hvilke Kubernetes-ressurser som blir opprettet for applikasjonen din (deployment, secret, ingress, autoscaler).

Sjekk statusen på deploymenten din

```
curl -k https://daemon.nais.oera-q.local/deploystatus/default/$UNIQUENAME

```

Hmmm... La oss debugge statusen til applikasjonen.


### Debugging

Applikasjonen din er deployet til sitt eget namespace, som har samme navn som applikasjonen, i dette tilfellet $UNIQUENAME. Du kan tenke på namespacet som et eget miljø for din app. Les mer om dette under [service discovery](https://nais.io/doc/#/dev-guide/service_discovery) i dokumentasjonen. For å debugge må du derfor spesifisere at du skal bruke dette namespacet:

```
kubectl config set-context preprod-sbs --namespace=$UNIQUENAME
```

Sjekk deploymenten:

```
kubectl get deployment
```

Denne kommandoen lister opp alle deployments i dette namespacet. I dette tilfellet er det kun en, din app. Den er navngitt `app` og videre bortover lister den opp `desired`, `current`, up-to-date` og `available` pods. Under `available` står det 0. La oss se videre på disse.

```
kubectl get pods
```

Her ser du at statusen på podene ikke er `Running`. Beskriv en av dem for å liste opp eventene for poden og få en indikasjon på hva som feiler, husk å erstatte PODNAVN med et av navnene listet opp av kommandoen over:

```
kubectl describe pod PODNAVN
```

Her kan du se at endepunktet /isAlive svarer med 404, som resulterer i at Kubernetes dreper poden. Applikasjonen din må svare med statuskode 200 på endepunktene `/isAlive` og `isReady`. Disse endepunktene bruker Kubernetes til å sjekke statusen til podene. Når /isAlive svarer med en feilkode anser Kubernetes containeren i poden som unhealthy og dreper den. Endepunktet /isReady bruker Kubernetes til å sjekke om containeren er klar til å ta imot trafikk.

Åpne din favoritteditor og implementer en /isAlive og en /isReady som begge svarer med en 200 OK. Når du har gjort det, bygg et nytt docker image med en ny tag, f.eks `2.0`. Push det slik som du gjorde med første versjon.

Deploy den nye versjonen til NAIS, ved å gjenta requesten du gjorde tidligere. Husk å oppdatere versjon i payloaden.

Sjekk statusen på deployment og pods på nytt. Nå burde disse være oppe å kjøre.

Her er noen andre kommandoer du kan teste for å inspisere:

```
# se loggene i en container
kubectl logs PODNAVN
# se ressursene podene bruker
kubectl top pods
# hent alle ressursene i Kubernetes for applikasjonen din, de har labelen app=$UNIQUENAME
kubectl get all -l app=$UNIQUENAME
```

### Ingress

Nå som du har deployet en sunn og fin app er neste skritt å kunne nå den fra f.eks nettleseren. Se på ingress-ressursen til appen:

```
kubectl get ingress
```

Prøv å adressen som står under HOSTS via nettleseren. Gikk det ikke? Selv om ingressen eksponerer port 80 kan appen kun nås med HTTPS. Dette er fordi appen er bak en BigIP load balancer. Prøv å nå samme adressen på HTTPS. Du skal nå kunne se "Hello, World!". 

Gratulerer, appen din kjører nå i NAIS! 🎉


## Monitoring and logging 

### Monitoring with Prometheus

If your app provides Prometheus metrics. The platform will collect the metrics
and you will be able to visualize the metrics and set up alerts in grafana.
We will also provide default dashboards for your application.

Lets add some  metrics to your application.

 - Add the following compile dependencies to the demo application. In build.gradle:
 
       compile("io.prometheus:simpleclient_spring_boot:0.0.26")

       compile("io.prometheus:simpleclient_hotspot:0.0.26")

 
 - Autoconfigure, enable a metrics endpoint and collect some metrics using by annotating the main class
   with the following annotations.
 
        @EnablePrometheusEndpoint

        @EnableSpringBootMetricsCollector

 
 - Run the demo app and verify that jvm metrics are collected:
 
        localhost:8080/prometheus

### Logging

Log to stdout. And we will collect them for you and visualize them in Kibana.
Log to stdout and in a json format and we will collect them, index them and provide even more powerful
search and visualize capabilities in Kibana.

 - Add a logstash json encoder to the application. In build.gradle:
 
        runtime("net.logstash.logback:logstash-logback-encoder:4.10")
 
 - Add the following logback.xml to the resources folder:

        <configuration>
            <appender name="stdout_json" class="ch.qos.logback.core.ConsoleAppender">
                <encoder class="net.logstash.logback.encoder.LogstashEncoder" />
            </appender>
            <root level="info">
                <appender-ref ref="stdout_json" />
            </root>
        </configuration>

  - Run the application and verify that you get some logs messages to stdout in json format.

### Putting it all together:

   - Modify nais.yaml to enable prometheus scraping:

         image: docker.adeo.no:5000/$UNIQUENAME
         prometheus: 
           enabled: true
           path: /prometheus

   - Logging is enabled by default.

   - Build application, docker image and push your image and nais.yaml. Remember to increase version.

   - Deploy the new version.

   - Verify that your new version is up and running.

   - Checkout https://grafana.adeo.no/dashboard/db/nais-app-dashboard to verify that your metrics are being scraped

   - CHeckout https://logs.adeo.no to verify that logs are being indexed.


## Fasit

Her får du statuskode 400 tilbake. Dette er på grunn av FASIT.

- Applikasjonen din må være registrert i FASIT for å kunne deployes 

You might get a error here. Which brings us to FASIT part 1. 

 -  Your application needs to be registered in Fasit. So head over to fasit.adeo.no 
    and create an application with the same name as $UNIQUENAME. 

 -  Rerun your curl to the daemon. 

### Using/Exposing Fasit resources. 

You can specify which Fasit resources your application is using and the platform will fetch the
resources and inject them as environment variables into your pods.
You can also expose resources.


   - In Fasit add a a resource to your application

   - Modify nais.yaml to consume/expose resources. See:

        https://github.com/nais/naisd/blob/master/nais_example.yaml

   - Build and deploy you application.

   - Checkout the  /env endpoint to see environment variables available.

   - Check Fasit to see that your exposed resource has been created.


## Clean up

Send a request to delete your application:

```
curl -k -S -X "DELETE" https://daemon.nais.oera-q.local/app/t1/$UNIQUENAME
```

## Videre

Dokumentasjon på NAIS finner du her [https://nais.io/doc](https://nais.io/doc). 





