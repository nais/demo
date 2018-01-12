# NAIS  101


## Prerequisites

 - Docker
 - Kubectl and access to internal NAV clusters. 
 - Access to Fasit. 

## Setup 

``
git clone https://github.com/nais/demo.git
``

You might need to turn off SSL verification:
 
`` git config --global http.sslVerify false ``

## Building and running your application in Docker.

 - Build the app. As we are using a common docker daemon give your docker image a unique name.
   You need a different $VERSION for each new version of the app.

    ``
    ./gradlew build && docker build . -t docker.adeo.no:5000/$UNIQUENAME:$VERSION
    ``
    (or gradlew.bat for windows shells)
 
 - List your newly create docker image:
 
    `` docker images ``

 - Run you docker image locally/remotely

    `` docker run -d -p 8080 docker.adeo.no:5000/$UNIQUENAME:$VERSION ``

    You should see your docker container running using 

    `` docker ps `` 

    Note the portmapping  ``0.0.0.0:12345(random port) -> 8080 ``  for your container.
    You should be able to browse to 

    `` http://e34apvl00253.devillo.no:12345/hello `` 

      Or `` localhost:12345 `` if running a local docker daemon.
    
 -  Stop your docker container 

    `` docker stop CONTAINER_ID `` or 
    `` docker stop CONTAINER_NAME``

 - Push to internal NAV docker repo. 

    `` docker push docker.adeo.no:5000/$UNIQUENAME:$VERSION `` 


# Deploying to a NAIS cluster.

 - Open nais.yaml and replace the image with your image
      
       image: docker.adeo.no:5000/$UNIQUENAME

 - Push nais.yaml to a repository.
 
   `` curl -s -S --user uploader:upl04d3r --upload-file nais.yaml https://repo.adeo.no/repository/raw/nais/$UNIQUENAME/$VERSION/nais.yaml ``

 - Deploy to preprod-fss
 
    todo: Get a srv_user/pwd  

    `` curl -s -S -k -d '{"application": "$UNIQUENAME","version": "$VERSION", "environment": "t6", "zone": "fss", "namespace": "demo", "username": "brukernavn", "password": "passord"}' https://daemon.nais.preprod.local/deploy ``
    
     You might get a error here. Which brings us to FASIT part 1. 

 -  Your application needs to be registered in Fasit. So head over to fasit.adeo.no 
    and create an application with the same name as $UNIQUENAME. 

 -  Rerun your curl to the daemon. 

    You should get a response about kubernetes resources being created. (deployment, secret, ingress, autoscaler)

 - Check the status of your deployment

        curl -k https://daemon.nais.preprod.local/deploystatus/demo/$UNIQUENAME

   Hmmm. 

 - Lets debug the status of your application.
 
    Switch to the preprod-fss cluster:
        
        kubectl config use-context preprod-fss  
    
    Set namespace demo as the current namesspace: 
    
        kubectl config set-context preprod-fss --namespace=demo  
        
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
    
        kubectl top YOUR-POD_NAME
        
        kubectl get all -l app=$UNIQUENAME  

 - But... where is my app running
 
        kubectl get ingress $UNIQUENAME 
   should give you a hint. 

   Congratulations your app is now running in NAIS.

   At this point I should ask if there are any questions and perhaps talk about k8s resources.

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
   

## QA

????

I should probably say something about the configuration options in nais.yaml at some
point.

Service discovery internal/external.

And maybe something about our plans/dreams.  Istio(J), persistence storage, databases(SQL/NOSQL) as a service, 
Redis As A Service. XYZ as a service. 


  
             
        
 
        


     
 
    

  



 

