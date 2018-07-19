# NAIS 101

Disse oppgavene tar deg gjennom de grunnleggende stegene for å deploye en applikasjon på NAIS.

For å gjennomføre disse oppgavene kreves det at du har:

- Installert i utviklerimaget:
  - Docker
  - Kubectl og tilgang til NAVs interne clustere
- Tilgang til Fasit

Følg skrittene her [https://confluence.adeo.no/pages/viewpage.action?pageId=259110874](https://confluence.adeo.no/pages/viewpage.action?pageId=259110874) om du ikke har dette installert på utvikler imaget ditt allerde.

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

### Bygg image

Under [src/](./src) ligger det en liten applikasjon som vi vil bygge inn i et Docker image. Ta en titt på [Dockerfile](./Dockerfile) for å se hvordan det bygges.

Bygg appen:

```
./gradlew build
```

Hvis du er på Windows image, kjør `gradlew.bat build` i stedet.

Bygg Docker imaget, pass på å bytte ut `$UNIQUENAME` med noe du vil kalle appen og `$VERSION` med feks `1.0`.

```
docker build . -t repo.adeo.no:5443/$UNIQUENAME:$VERSION
```

Du skal nå ha et docker image tagget med Docker registrien vi skal laste det opp til. 

List opp images for å se at det er lagd:

```
docker images
```

### Test imaget
Test imaget ditt:

```
docker run -d -p 8080 repo.adeo.no:5443/$UNIQUENAME:$VERSION
```

Se at imaget ditt kjører ved:

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

Stopp containeren din:

`docker stop CONTAINER_ID` eller `docker stop CONTAINER_NAME`

## Push til internt NAV docker repo

```
docker push repo.adeo.no:5443/$UNIQUENAME:$VERSION
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
curl -s -S --user uploader:<super_secret_pwd> --upload-file nais.yaml https://repo.adeo.no/repository/raw/nais/$UNIQUENAME/$VERSION/nais.yaml
```

### Deploy

Nå som vi har pushet manifestet nais.yaml og docker imaget til appen vår, er vi klare til å deploye. Dette gjør vi ved en POST request til naisd:

```
curl -s -S -k -d '{"application": "$UNIQUENAME","version": "$VERSION", "fasitEnvironment": "t6", "zone": "sbs", "fasitUsername": "brukernavn", "fasitPassword": "passord", "skipFasit": "true"}' https://daemon.nais.oera-q.local/deploy
```

You might get a error here. Which brings us to FASIT part 1. 

 -  Your application needs to be registered in Fasit. So head over to fasit.adeo.no 
    and create an application with the same name as $UNIQUENAME. 

 -  Rerun your curl to the daemon. 

    You should get a response about kubernetes resources being created. (deployment, secret, ingress, autoscaler)

 - Check the status of your deployment

        curl -k https://daemon.nais.oera-q.local/deploystatus/demo/$UNIQUENAME

   Hmmm. 

 - Lets debug the status of your application.
 
    Switch to the preprod-sbs cluster:
        
        kubectl config use-context preprod-sbs  
    
    Set namespace demo as the current namesspace: 
    
        kubectl config set-context preprod-sbs --namespace=demo  
        
    Get all pods in the current context(cluster) and namespace demo: 
    
        kubectl get pod 
    
    You should see your pods but they are not in a Running state. Thats bad.
    You can get list of events for your pod. And an indication of why the pod is failing:
    
        kubectl describe pod "your-pod-name"  

    Note that kubernetes is killing your pod because the endpoint /isAlive is responding with 404. 

    At this point I should probably say something about liveness, readyness and nais.yaml. 

    tldr; You application needs to respond with 200 at the default endpoints /isAlive and /isReady.

  - Open your favorite editor and implment a /isAlive and a /isReady endpoint which responds with a 200 OK.

  - Build the application and docker container. Push the new docker container and nais.yaml to their respective
    repositories using curl. Remember to increment the version. 

  - Deploy the new version to NAIS.

  - Check the status of your pods. They should be now in a running state.
    A few kubectl commands to check your pods.
    
        kubectl logs YOUR-POD-NAME
    
        kubectl top pod
        
        kubectl get all -l app=$UNIQUENAME  

 - But... where is my app running
 
        kubectl get ingress $UNIQUENAME 
   should give you a hint. Notice that even if the ingress is exposing port 80, the app is behind a BigIP load balancer and can only be reached with HTTPS.

   Congratulations your app is now running in NAIS.


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

