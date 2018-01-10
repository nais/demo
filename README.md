#Clone this repo: 

git clone https://github.com/nais/demo.git

You might need to turn off SSL verification: 

git config --global http.sslVerify false



# Build the app. As we are using a common docker daemon
# give your docker image a unique name.

./gradlew build && docker build . -t $UNIQUENAME (or gradlew.bat for windows shells)

# List your newly create docker image.

docker images


#Run you docker image locally/remotely

docker run -d -p 8080 $UNIQUENAME

#You should see your docker image running using 

docker ps 

#Note the portmapping 0.0.0.0:12345(random port) -> 8080 for your image
#You should be able to browse to 

http://e34apvl00253.devillo.no:12345/hello 

#Or localhost:12345 if running a local docker daemon.
# You can stop your docker container using 

docker stop CONTAINER_ID or NAMES (from docker ps output)



# Tag and push to internal NAV docker repo. 
#You need a different $VERSION for each new version of the app.

docker tag  $UNIQUENAME docker.adeo.no:5000/$UNIQUENAME:$VERSION  && docker push docker.adeo.no:5000/$UNIQUENAME:$VERSION 


# Deploying to a NAIS cluster.

Open nais.yaml and replace the image with your image

# Push nais.yaml to a repository.
curl --user uploader:upl04d3r --upload-file nais.yaml https://repo.adeo.no/repository/nais/$UNIQUENAME/$VERSION/nais.yaml

# Deploy to preprod-fss 

curl -k -d '{"application": "$UNIQUENAME","version": "$VERSION", "environment": "t6", "zone": "fss", "namespace": "demo", "username": "brukernavn", "password": "passord"}' https://daemon.nais.preprod.local/deploy

# You might get some error here. Which brings us to FASIT part 1. 

Your application needs to be registred in fasit. So head over to fasit.adeo.no 
and create a application with the same name as $UNIQUENAME. 

# Rerun your curl to the daemon. 

You should get a respnse about kubernets resources being created. (deployment, secret, ingress, autoscaler)

# Now for some kubectl commands. 

kubectl context preprod-fss #Switch to the preprod-fss cluster. 
kubectl config set-context preprod-fss --namespace=demo #Set namespace demo as the current namesspace
kubectl get pod #Get all pods in the current context(cluster) and namespace dem.


# You should see your pods but they are not in a Running state. Thats bad.
kubectl describe pod "your-pod-name" #Gives you a list of events for your pod. And an indication of why the pod is failing.

Note that kubernetes is killing your pod because the endpoint /isAlive is responding with 404. 

At this point I should probably say something about liveness and readyness.
tldr; You application needs to respond with 200 at the default endpoints /isalive and /isready.








