This keeps the infrastructure that builds docker images for [GraphDB](http://graphdb.ontotext.com/)

Check [Docker Hub Images](https://hub.docker.com/r/ontotext/graphdb/) for information on how to use the images.

Note that to use GraphDB EE or SE docker images, you must get a license from us first.

Currently there are no public images for GraphDB Free and you will have to build those if needed from the zip distribution that you get after registering on our website.

# Preload and start GraphDB with docker-compose

The `docker-compose.yml` files to deploy GraphDB use the `free-edition` build by default. It requires to have downloaded and placed the standalone GraphDB distribution `.zip` file in the `free-edition/` folder. 

## Preload a repository

Go to the `preload` folder to run the bulk load data when GraphDB is stopped.

```bash
cd preload
```

By default it will:

* Create and override the repository testTimestamping as defined in the `graphdb-repo-config.ttl` file.
* Upload a test ntriple file from the `preload/import` subfolder.

> See the [GraphDB preload documentation](http://graphdb.ontotext.com/documentation/free/loading-data-using-preload.html) for more details.

When running the preload docker-compose various parameters can be provided in the `preload/.env` file:

```bash
GRAPHDB_VERSION=9.10.3
GRAPHDB_HEAP_SIZE=2g
GRAPHDB_HOME=../graphdb-data
REPOSITORY_CONFIG_FILE=./graphdb-repo-config.ttl
```

Build and run:

```bash
docker-compose build
docker-compose up -d
```

> GraphDB data will go to `/data/graphdb`

Go back to the root of the git repository to start GraphDB:

```bash
cd ..
```

### Start GraphDB

To start GraphDB run the following **from the root of the git repository**:

```bash
docker-compose up -d
```

> It will use the repo created by the preload in `graphdb-data/`

> Feel free to add a `.env` file similar to the preload repository to define variables.


# Original docu
https://github.com/Ontotext-AD/graphdb-docker
