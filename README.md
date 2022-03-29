# Include files
Get a free graphdb-free standalone copy (zip file) and copy it into src/test/resources/free-edition along with 
the LICENSE.txt file.

# Build Plugin
Go to the root directory and execute `mvn clean package -Dmaven.test.skip=true` or `mvn clean package -DSkipTests` 
(depending on your maven version).  
This builds the .jar package into target/. From there it is picked up by docker-compose to place it into the docker image. 
You don't need to execute docker-compose manually, this execution is part of the unit tests.

# Execute tests
Run the test file com.ontotext.trree.plugin.rdfstartimestamping.TestRDFStarTimestampingPlugin with Java 8 - corretto 1.8.
This runs two docker-compose files, one to create the test repository and one to start the server. It then 
runs the test via the pre-configured SPARQL endpoints and shuts down the running docker service.
