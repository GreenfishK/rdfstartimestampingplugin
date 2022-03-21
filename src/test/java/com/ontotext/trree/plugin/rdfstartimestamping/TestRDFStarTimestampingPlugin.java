package com.ontotext.trree.plugin.rdfstartimestamping;

import com.ontotext.graphdb.ConfigException;
import com.ontotext.trree.sdk.ServerErrorException;
import org.eclipse.rdf4j.query.*;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.config.RepositoryConfigException;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.junit.*;
import java.io.*;
import java.net.Socket;
import java.util.Random;
import static org.junit.Assert.*;

/**
 * Tests the example plugin.
 */
public class TestRDFStarTimestampingPlugin {
    private static SPARQLRepository repo;
    private static RepositoryConnection sparqlRepoConnection;
    private static String repoId;

    private static boolean available(int port) {
        try (Socket ignored = new Socket("localhost", port)) {
            return false;
        } catch (IOException ignored) {
            return true;
        }
    }

    private static void runDocker(File file) throws IOException, InterruptedException {
        Process process;
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", file.toString());
            pb.inheritIO();
            process = pb.start();
            process.waitFor();

        } finally {
            file.delete();
        }
    }

    private static File startContainer() throws IOException {
        File tempScript = File.createTempFile("script", null);

        Writer streamWriter = new OutputStreamWriter(new FileOutputStream(
                tempScript));
        PrintWriter printWriter = new PrintWriter(streamWriter);

        printWriter.println("cd src/test/resources/graphdb-docker-master/preload");
        printWriter.println("docker-compose up -d");
        printWriter.println("cd ..");
        printWriter.println("docker-compose up -d");

        printWriter.close();

        return tempScript;
    }

    private static File shutdownContainer() throws IOException {
        File tempScript = File.createTempFile("script", null);

        Writer streamWriter = new OutputStreamWriter(new FileOutputStream(
                tempScript));
        PrintWriter printWriter = new PrintWriter(streamWriter);

        printWriter.println("cd src/test/resources/graphdb-docker-master");
        printWriter.println("docker-compose down");

        printWriter.close();

        return tempScript;
    }

    @BeforeClass
    public static void init() {
        //Wenn re-starting tests and trying to create a repository with the same ID as before within the same
        //GraphDB session, the repository will not be created due to unknown reasons. That is why we
        //randomize the repository ID so that we can create multiple test repositories within one server session.
        Random ra = new Random(System.currentTimeMillis());
        repoId = String.format("testTimestamping%s", ra.doubles().findFirst().getAsDouble());
        repoId = "testTimestamping";

        String queryEndpoint = String.format("http://localhost:7200/repositories/%s", repoId);
        String updateEndpoint = String.format("http://localhost:7200/repositories/%s/statements", repoId);
        try {
            //Start GraphDB server and create or re-create testTimestamping repository with docker-compose.
            runDocker(startContainer());
            System.out.println("Port not available yet available...");
            Thread.sleep(20000);

            //Establish connection to SPARQL endpoint
            repo = new SPARQLRepository(queryEndpoint, updateEndpoint);
            sparqlRepoConnection = repo.getConnection();

            //Test queries against SPARQL endpoint
            try (RepositoryConnection connection = sparqlRepoConnection) {
            BooleanQuery bQuery = connection.prepareBooleanQuery("ask from <http://example.com/testGraph> { ?s ?p ?o }");
                boolean hasResults = bQuery.evaluate();
                assertFalse("No triples should be in the graph yet.", hasResults);
                System.out.println("Result from ask query: " + hasResults);
                System.out.println("Read queries are executable against the SPARQL endpoint");
            } catch (QueryEvaluationException e) {
                System.err.println(e.getClass() + ":" + e.getMessage());
                throw new ServerErrorException("Your GraphDB server might not be running.");
            }

            // Test update statements against SPARQL endpoint
            try (RepositoryConnection connection = sparqlRepoConnection) {
                connection.begin();
                String updateString = "clear graph <http://example.com/testGraph>";
                connection.prepareUpdate(updateString).execute();
                updateString = "delete data {graph <http://example.com/testGraph> " +
                        "{<http://example.com/s/v1> <http://example.com/p/v2> <http://example.com/o/v3>}}";
                connection.prepareUpdate(updateString).execute();
                connection.commit();
                System.out.println("Write statements are executable against the embedded repository");
            } catch (UpdateExecutionException e) {
                System.err.println(e.getClass() + ":" + e.getMessage());
                throw new RepositoryException(e.getMessage());
            }


        } catch (RDFHandlerException | RDFParseException | RepositoryConfigException | RepositoryException | IOException | InterruptedException e) {
            System.err.println(e.getClass() + ":" + e.getMessage());
            e.printStackTrace();
            throw new ConfigException("Tests cannot start. " +
                    "Check whether the server is running and your repository is setup correctly.");
        }

    }

    @Test
    public void insertSingleTripleVersioningTest() throws InterruptedException {
        String updateString = "insert data { graph <http://example.com/testGraph> {<http://example.com/s/v1> <http://example.com/p/v2> <http://example.com/o/v3> }}";
        sparqlRepoConnection.begin();
        sparqlRepoConnection.prepareUpdate(updateString).execute();
        sparqlRepoConnection.commit();

        Thread.sleep(5000);

        //TODO: wait for plugin to insert triples

        TupleQuery query = sparqlRepoConnection.prepareTupleQuery("select * from <http://example.com/testGraph> { ?s ?p ?o }");
        try (TupleQueryResult result = query.evaluate()) {
            assertTrue("Must have two nested triples in the result", result.hasNext());
            assertEquals(2, result.stream().count());
            while (result.hasNext()) {
                BindingSet bindings = result.next();
                for (String bindingName : result.getBindingNames()) {
                    System.out.println(bindingName + ": " + bindings.getValue(bindingName));
                }
            }
        }
    }

    @Test
    public void insertMultipleTriplesVersioningTest() {
        fail("not yet implemented");
    }

    @Test
    public void deleteSingleTripleVersioningTest() {
        fail("not yet implemented");
    }

    @Test
    public void deleteMultipleTripleVersioningTest() {
        fail("not yet implemented");
    }

    @Test
    public void deleteAllTriplesVersioningTest() {
        fail("not yet implemented");
    }

    @Test
    public void queryLiveDataTest() {
        fail("not yet implemented");
    }

    @Test
    public void queryHistoryDataTest() {
        fail("not yet implemented");
    }


    @After
    public void clearTestGraph() {
        String updateString = "clear graph <http://example.com/testGraph>";
        sparqlRepoConnection.begin();
        sparqlRepoConnection.prepareUpdate(updateString).execute();
        sparqlRepoConnection.commit();

        BooleanQuery query = sparqlRepoConnection.prepareBooleanQuery("ask from <http://example.com/testGraph> { ?s ?p ?o }");
        boolean hasResults = query.evaluate();
        assertFalse("No triples should be in the graph anymore", hasResults);
        System.out.println("<http://example.com/testGraph> has been cleared.");

    }

    @AfterClass
    public static void shutdown() {
        //Close connection, shutdown repository and delete repository directory
        try {
            repo.shutDown();
            sparqlRepoConnection.close();
            //File lock = new File(System.getProperty("user.home") + String.format("/.graphdb/data/repositories/testTimestamping/storage/lock", repoId));
            //if (lock.exists())
            //    lock.delete();
            runDocker(shutdownContainer());

            //TODO: execute docker-compose down
            System.out.println(String.format("Connection shutdown and repository %s removed", repoId));
        } catch (NullPointerException e) {
            System.out.println("Connection is not open and can therefore be not closed.");
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

}
