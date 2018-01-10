# Build the app. 

./gradlew build && docker build . -t demo 

#Run you docker image local

# Tag and push to internal NAV docker repo. 
#You need a different tag for a new version of the app

docker tag demo docker.adeo.no:5000/demo/demo:1 && docker push docker.adeo.no:5000/demo/demo:1 
