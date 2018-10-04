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


## Kubernetes

Vi skal nå se litt mer på Kubernetes. Mens Docker er containerteknologien vi bruker for å pakke appen vår inn i er Kubernetes teknologien som holder styr på containerene. Kubernetes vet hvilke containere som er kjørende, healthy og hvor mange instanser som kjører. 

Kubernetesressursen som holder på containeren kaller vi en Pod. En Pod inneholder en, eller flere, containere og informasjon om hvilket docker image som skal kjøres og environment variablene som trengs. Ofte kjører vi opp containeren for applikasjonen vår i flere pods, slik at det er flere instanser av samme applikasjon kjørende samtidig.

Ressursen som kontrollerer Pods heter Deployment. En Deployment inneholder en spesifikasjon på hvordan vi vil ha våre pods, altså både Docker-imaget, environment variabler og antallet pods vi vil ha kjørende. Kubernetes vil da lage enda en type ressurs, som kalles ReplicaSet. Deploymenten holder styr på ReplicaSetet, som igjen styrer Podene. ReplicaSetet får informasjon fra Deploymenten om antall Pods som er ønsket og oppretter dette.

Hvis vi endrer Deploymenten, for eksempel ved å endre versjon av Docker imaget som kjøres, opprettes det et nytt ReplicaSet med den oppdaterte informasjonen. Det gamle ReplicaSetet får beskjed om å skalere ned sine Pods, mens det nye får beskjed om å skalere opp. Kubernetes gjør dette på en måte som kontrollerer at det til en hver tid, så langt det lar seg gjøre, er minst en kjørende og healthy Pod. Slik får vi nedefri deploytid.


### Debugging

Applikasjonen din er deployet til sitt eget namespace, som har samme navn som applikasjonen, i dette tilfellet $UNIQUENAME. Du kan tenke på namespacet som et eget miljø for din app. Les mer om dette under [service discovery](https://nais.io/doc/#/dev-guide/service_discovery) i dokumentasjonen. For å debugge må du derfor spesifisere at du skal bruke dette namespacet:

```
kubectl config set-context preprod-sbs --namespace=$UNIQUENAME
```

Sjekk deploymenten:

```
kubectl get deployment
```

Denne kommandoen lister opp alle deployments i dette namespacet. I dette tilfellet er det kun en, din app. Den er navngitt `app` og videre bortover lister den opp `desired`, `current`, `up-to-date` og `available` pods. Under `available` står det 0. La oss se videre på disse.

```
kubectl get pods
```

Her ser du at statusen på podene ikke er `Running`. Beskriv en av dem for å liste opp eventene for poden og få en indikasjon på hva som feiler, husk å erstatte PODNAVN med et av navnene listet opp av kommandoen over:

```
kubectl describe pod PODNAVN
```

Her kan du se at endepunktet `/isAlive` svarer med `404`, som resulterer i at Kubernetes dreper poden. Applikasjonen din må svare med en statuskode under 400 på endepunktene `/isAlive` og `/isReady`. Disse endepunktene bruker Kubernetes til å sjekke statusen til podene. Når /isAlive svarer med en feilkode anser Kubernetes containeren i poden som unhealthy og dreper den. Endepunktet /isReady bruker Kubernetes til å sjekke om containeren er klar til å ta imot trafikk.

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


## Monitorering og logging 

### Monitorering med Prometheus

Hvis appen din støtter Prometheus-metrics vil plattformen samle metrikkene og du kan visualisere dem og sette opp alerts i Grafana. Du får også et dashboard for applikasjonen din.

Legg til disse avhengighetene i demo-applikasjonen. I fila `build.gradle`:
 

  ``` 
       compile("io.prometheus:simpleclient_spring_boot:0.0.26")

       compile("io.prometheus:simpleclient_hotspot:0.0.26")
  ```

Annoter main-classen for å samle metrics:
 
 - Autoconfigure, enable a metrics endpoint and collect some metrics using by annotating the main class
   with the following annotations.
 
   ```
        @EnablePrometheusEndpoint

        @EnableSpringBootMetricsCollector
   ```
 
Kjør opp demo-appen for å verifisere at JVM-metrics blir hentet:

   
   ```
        localhost:8080/prometheus
   ```


### Logging

Det som logges til `stdout` blir logget og visualisert i Kibana. 
Hvis du logger i et JSON-format vil vi i tillegg indeksere loggene og tilby et kraftigere søk.

 - Legg til en logstasj JSON encoder i applikasjonen. I `build.gradle`:

```
runtime("net.logstash.logback:logstash-logback-encoder:4.10")
```

- Lag fila `src/main/resources/logback.xml` med dette innholdet: 
 
```
        <configuration>
            <appender name="stdout_json" class="ch.qos.logback.core.ConsoleAppender">
                <encoder class="net.logstash.logback.encoder.LogstashEncoder" />
            </appender>
            <root level="info">
                <appender-ref ref="stdout_json" />
            </root>
        </configuration>
```

- Kjør applikasjonen på nytt og verifiser at du får logg-meldinger til stdout i JSON-format.


### Deploy med logging og metrikker

   - Endre `nais.yaml` for å skru på prometheus-scraping: 

  ```
         image: docker.adeo.no:5000/$UNIQUENAME
         prometheus: 
           enabled: true
           path: /prometheus
  ```
  
   - Logging er allerede aktivert (det er standardvalget)

   - Bygg appen, Docker image og push imaget og nais.yaml på nytt. Husk å øke versjonsnummeret

   - Kjør nytt deploy-kall med den nye versjonen

   - Verifiser at den nye versjonen kjører i clusteret

   - Sjekk ut [https://grafana.adeo.no/dashboard/db/nais-app-dashboard](https://grafana.adeo.no/dashboard/db/nais-app-dashboard) og verifiser at metrikkene dine blir scraped

   - Sjekk ut [https://logs.adeo.no](https://logs.adeo.no) for å verifisere at loggene blir indeksert


## Fasit

Her får du statuskode 400 tilbake. Dette er på grunn av FASIT.

- Applikasjonen din må være registrert i FASIT. Ta turen innom fasit.adeo.no og lag en applikasjon med samme navn som $UNIQUENAME.

- Kjør curl til naisd en gang til for å deploye.


### Bruke Fasit-ressurser 

Du kan spesifisere hvilke Fasit-ressurser applikasjonen din bruker. NAIS-plattformen vil hente ressurene og tilgjengeliggjøre dem for applikasjonen som miljøvariabler i Podene.
Du kan også eksponere ressurser.

- Legg til en ressurs for applikasjonen i Fasit

- Endre `nais.yaml` for å konsumere/eksponere ressurser. Se [https://github.com/nais/naisd/blob/master/nais_example.yaml](https://github.com/nais/naisd/blob/master/nais_example.yaml)

- Bygg og deploy applikasjonen din med de nye Fasit-ressursene

- Kjør en curl mot endepunktet `/env` for å se environment-variablene tilgjengelig

- Sjekk ut Fasit for å verifisere at din eksponerte ressurs har blitt opprettet


## Opprydding

For å slette applikasjonen din, send en DELETE-request til naisd:

```
curl -k -S -X "DELETE" https://daemon.nais.oera-q.local/app/t1/$UNIQUENAME
```

## Videre

Dokumentasjon på NAIS finner du her [https://nais.io/doc](https://nais.io/doc). 



