package com.ontotext.trree.plugin.rdfstartimestamping;

import com.ontotext.graphdb.ConfigException;
import com.ontotext.trree.sdk.ServerErrorException;
import org.apache.commons.io.FileUtils;
import org.eclipse.rdf4j.query.*;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.config.RepositoryConfigException;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.junit.*;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;


import static org.junit.Assert.*;

/**
 * Tests the example plugin.
 */
public class TestRDFStarTimestampingPlugin {
    private static SPARQLRepository repo;
    private static RepositoryConnection sparqlRepoConnection;
    private static String repoId;

    private static String logFilePath;
    private static int lastLineNumber;
    private static boolean defaultGraph;

    @BeforeClass
    public static void init() {
        repoId = "testTimestamping";
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        logFilePath = String.format("target/graphdb-data/logs/main-%s.log", dtf.format(LocalDateTime.now()));
        lastLineNumber = getLastLineNumber(logFilePath);

        String queryEndpoint = String.format("http://localhost:7200/repositories/%s", repoId);
        String updateEndpoint = String.format("http://localhost:7200/repositories/%s/statements", repoId);
        try {
            //Start GraphDB server and create or re-create testTimestamping repository with docker-compose.
            runDocker(startContainer());
            System.out.println("\nPort not available yet available...");
            Thread.sleep(20000);

            //Establish connection to SPARQL endpoint
            repo = new SPARQLRepository(queryEndpoint, updateEndpoint);
            sparqlRepoConnection = repo.getConnection();

            //Test queries against SPARQL endpoint
            try {
                TupleQuery query = sparqlRepoConnection.prepareTupleQuery("select * { ?s ?p ?o }");
                try (TupleQueryResult result = query.evaluate()) {
                    assertTrue("Triples must be preloaded from the /import directory.", result.hasNext());
                    long cntTriples = result.stream().count();
                    System.out.println(cntTriples);
                    assertTrue("Number of triples must be over 70 (there are always 70 inferred triples)",
                            cntTriples > 70);
                }
            } catch (QueryEvaluationException e) {
                System.err.println(e.getClass() + ":" + e.getMessage());
                throw new ServerErrorException("Your GraphDB server might not be running.");
            }

            // Test update statements against SPARQL endpoint
            try (RepositoryConnection connection = sparqlRepoConnection) {
                connection.begin();
                String updateString = "delete data {graph <http://example.com/testGraph> " +
                        "{<http://example.com/s/testConnection> <http://example.com/p/testConnection> <http://example.com/o/testConnection>}}";
                connection.prepareUpdate(updateString).execute();
                connection.commit();
                System.out.println("Write statements are executable against the embedded repository");
            } catch (UpdateExecutionException e) {
                System.err.println(e.getClass() + ":" + e.getMessage());
                throw new RepositoryException(e.getMessage());
            }


        } catch (RDFHandlerException | RDFParseException | RepositoryConfigException | ServerErrorException | RepositoryException | IOException | InterruptedException e) {
            System.err.println(e.getClass() + ":" + e.getMessage());
            e.printStackTrace();
            throw new ConfigException("Tests cannot start. " +
                    "Check whether the server is running and your repository is setup correctly.");
        }

    }

    @Test
    public void insertSingleTripleWithContextTest() throws InterruptedException {
        defaultGraph = false;
        String triple = "<http://example.com/testGraph/s/insertThis1> <http://example.com/testGraph/p/insertThis1> <http://example.com/testGraph/o/insertThis1>";
        String updateString = String.format("insert data { graph <http://example.com/testGraph> {%s}}", triple);
        sparqlRepoConnection.begin();
        sparqlRepoConnection.prepareUpdate(updateString).execute();
        sparqlRepoConnection.commit();

        //Wait for plugin to insert triples. This is managed by the server.
        Thread.sleep(5000);

        TupleQuery query = sparqlRepoConnection.prepareTupleQuery(String.format("select * from <http://example.com/testGraph> { <<<<%s>> ?x ?y >> ?a ?b }",triple));
        try (TupleQueryResult result = query.evaluate()) {
            assertTrue("Must have one double-nested triple in the result", result.hasNext());
            assertEquals(1, result.stream().count());
        }
    }

    @Test
    public void insertMultipleTriplesWithContextTest() throws InterruptedException {
        defaultGraph = false;
        String triple1 = "<http://example.com/testGraph/s/insertThis2> <http://example.com/testGraph/p/insertThis2> <http://example.com/testGraph/o/insertThis2>";
        String triple2 = "<http://example.com/testGraph/s/insertThis3> <http://example.com/testGraph/p/insertThis3> <http://example.com/testGraph/o/insertThis3>";

        String updateString = String.format("insert data { graph <http://example.com/testGraph> {%s . %s .}}",
                triple1, triple2);
        sparqlRepoConnection.begin();
        sparqlRepoConnection.prepareUpdate(updateString).execute();
        sparqlRepoConnection.commit();

        //Wait for plugin to insert triples. This is managed by the server.
        Thread.sleep(5000);

        TupleQuery query = sparqlRepoConnection.prepareTupleQuery(
                String.format("select * from <http://example.com/testGraph> { {<<<<%s>> ?x ?y>> ?a ?b} union {<<<<%s>> ?x ?y>> ?a ?b} }",
                        triple1, triple2));
        try (TupleQueryResult result = query.evaluate()) {
            assertTrue("Must two double-nested triples in the result.", result.hasNext());
            assertEquals(2, result.stream().count());

        }
    }

    @Test
    public void deleteSingleTripleWithContextTest() throws InterruptedException {
        defaultGraph = false;

        //Delete
        String triple = "<http://example.com/testGraph/s/deleteThis2> <http://example.com/testGraph/p/deleteThis2> <http://example.com/testGraph/o/deleteThis2>";
        String updateString = String.format("delete data { graph <http://example.com/testGraph> { %s }}", triple);
        sparqlRepoConnection.begin();
        sparqlRepoConnection.prepareUpdate(updateString).execute();
        sparqlRepoConnection.commit();

        Thread.sleep(5000);

        TupleQuery query = sparqlRepoConnection.prepareTupleQuery(String.format("select * { <<<<%s>> ?x ?y>> ?a ?b }", triple));
        try (TupleQueryResult result = query.evaluate()) {
            assertTrue("Number of triples should not change in the default graph", result.hasNext());
        }

        query = sparqlRepoConnection.prepareTupleQuery(String.format("select * from <http://example.com/testGraph> { <<<<%s>> ?x ?y>> ?a ?b }", triple));

        try (TupleQueryResult result = query.evaluate()) {
            assertTrue("Number of triples should not change in graph <http://example.com/testGraph>.", result.hasNext());
            int c = 0;
            while (result.hasNext()) {
                BindingSet bs = result.next();
                //String s = bs.getValue("s").stringValue();
                String p = bs.getValue("a").stringValue();
                String o = bs.getValue("b").stringValue();
                c++;
                System.out.println(p + " " + o);
                assertNotEquals("9999-12-31T00:00:00.000+00:00", o);

            }
            assertEquals(1, c);
        }
    }

    @Test
    public void deleteMultipleTripleWithContextTest() {
        defaultGraph = false;
        fail("not yet implemented");
    }

    @Test
    public void deleteNonExistingWithContextTest() {
        defaultGraph = true;
        fail("not yet implemented");
    }

    @Test
    public void deleteAllTriplesWithContextTest() {
        defaultGraph = false;
        fail("not yet implemented");
    }

    @Test
    public void queryLiveWithContextDataTest() {
        defaultGraph = false;
        fail("not yet implemented");
    }

    @Test
    public void queryHistoryWithContextDataTest() {
        defaultGraph = false;
        fail("not yet implemented");
    }

    @Test
    public void insertDeleteReInsertWithContextTest() {
        defaultGraph = false;
        fail("not yet implemented");

    }

    // %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% Without context tests starting here %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

    @Test
    public void insertSingleTripleTest() throws InterruptedException {
        defaultGraph = true;
        String triple = "<http://example.com/s/insertThis1> <http://example.com/p/insertThis1> <http://example.com/o/insertThis1>";
        String updateString = String.format("insert data {%s}", triple);
        sparqlRepoConnection.begin();
        sparqlRepoConnection.prepareUpdate(updateString).execute();
        sparqlRepoConnection.commit();

        //Wait for plugin to insert triples. This is managed by the server.
        Thread.sleep(5000);

        TupleQuery query = sparqlRepoConnection.prepareTupleQuery(String.format("select * { <<<<%s>> ?x ?y>> ?a ?b }",triple));
        try (TupleQueryResult result = query.evaluate()) {
            assertTrue("Must have one double-nested triples in the result", result.hasNext());
            assertEquals(1, result.stream().count());
        }
    }

    @Test
    public void insertMultipleTriplesTest() throws InterruptedException {
        defaultGraph = true;
        String triple1 = "<http://example.com/s/insertThis2> <http://example.com/p/insertThis2> <http://example.com/o/insertThis2>";
        String triple2 = "<http://example.com/s/insertThis3> <http://example.com/p/insertThis3> <http://example.com/o/insertThis3>";
        String updateString = String.format("insert data {%s. %s .}", triple1, triple2);
        sparqlRepoConnection.begin();
        sparqlRepoConnection.prepareUpdate(updateString).execute();
        sparqlRepoConnection.commit();

        //Wait for plugin to insert triples. This is managed by the server.
        Thread.sleep(5000);

        TupleQuery query = sparqlRepoConnection.prepareTupleQuery(
                String.format("select * { {<<<<%s>> ?x ?y>> ?a ?b} union {<<<<%s>> ?x ?y>> ?a ?b} }", triple1, triple2));
        try (TupleQueryResult result = query.evaluate()) {
            assertTrue("Must have two double-nested triples in the result.", result.hasNext());
            assertEquals(2, result.stream().count());
        }
    }

    @Test
    public void deleteSingleTripleTest() throws InterruptedException {
        defaultGraph = true;
        String updateString;
        String triple = "<http://example.com/s/deleteThis1> <http://example.com/p/deleteThis1> <http://example.com/o/deleteThis1>";

        updateString = String.format("delete data {%s}", triple);
        sparqlRepoConnection.begin();
        sparqlRepoConnection.prepareUpdate(updateString).execute();
        sparqlRepoConnection.commit();

        Thread.sleep(5000);

        TupleQuery query = sparqlRepoConnection.prepareTupleQuery(String.format("select * { <<<<%s>> ?x ?y>> ?a ?b }", triple));

        try (TupleQueryResult result = query.evaluate()) {
            assertTrue("Number of triples should not change in the default graph.", result.hasNext());
            int c = 0;
            while (result.hasNext()) {
                BindingSet bs = result.next();
                //String s = bs.getValue("s").stringValue();
                String p = bs.getValue("a").stringValue();
                String o = bs.getValue("b").stringValue();
                c++;
                System.out.println(p + " " + o);
                assertNotEquals("9999-12-31T00:00:00.000+00:00", o);

            }
            assertEquals(1, c);
        }
    }

    @Test
    public void deleteMultipleTripleTest() throws InterruptedException {
        defaultGraph = true;
        String updateString;
        String triple1 = "<http://example.com/s/deleteThis2> <http://example.com/p/deleteThis2> <http://example.com/o/deleteThis2>";
        String triple2 = "<http://example.com/s/deleteThis3> <http://example.com/p/deleteThis3> <http://example.com/o/deleteThis3>";


        updateString = String.format("delete data {%s . %s .}", triple1, triple2);
        sparqlRepoConnection.begin();
        sparqlRepoConnection.prepareUpdate(updateString).execute();
        sparqlRepoConnection.commit();

        Thread.sleep(5000);

        TupleQuery query = sparqlRepoConnection.prepareTupleQuery(
                String.format("select * { {<<<<%s>> ?x ?y>> ?a ?b} union {<<<<%s>> ?x ?y>> ?a ?b} }", triple1, triple2));

        try (TupleQueryResult result = query.evaluate()) {
            assertTrue("Number of triples should not change in the default graph.", result.hasNext());
            int c = 0;
            while (result.hasNext()) {
                BindingSet bs = result.next();
                //String s = bs.getValue("s").stringValue();
                String p = bs.getValue("a").stringValue();
                String o = bs.getValue("b").stringValue();
                c++;
                System.out.println(p + " " + o);
                assertNotEquals("9999-12-31T00:00:00.000+00:00", o);

            }
            assertEquals(2, c);
        }
    }

    @Test
    public void deleteNonExistingTest() {
        defaultGraph = true;
        fail("not yet implemented");
    }

    @Test
    public void deleteAllTriplesTest() {
        defaultGraph = true;
        fail("not yet implemented");
    }

    @Test
    public void queryLiveDataTest() {
        defaultGraph = true;
        fail("not yet implemented");
    }

    @Test
    public void queryHistoryDataTest() {
        defaultGraph = true;
        fail("not yet implemented");
    }

    @Test
    public void insertDeleteReInsertDeleteTest() throws InterruptedException {
        //TODO: Problem with concurrent updates. Consecutively inserting, deleting and re-inserting
        // leads to ignorance of the delete handling by the plugin.
        defaultGraph = true;
        String triple = "<http://example.com/test#reinsertThisS1> <http://example.com/test#p> <http://example.com/test#reinsertThisO1>";
        String updateString = String.format("insert data {%s}", triple);
        sparqlRepoConnection.begin();
        sparqlRepoConnection.prepareUpdate(updateString).execute();
        sparqlRepoConnection.commit();
        //Thread.sleep(500);

        updateString = String.format("delete data {%s}", triple);
        sparqlRepoConnection.begin();
        sparqlRepoConnection.prepareUpdate(updateString).execute();
        sparqlRepoConnection.commit();

        /*updateString = String.format("insert data {%s}", triple);
        sparqlRepoConnection.begin();
        sparqlRepoConnection.prepareUpdate(updateString).execute();
        sparqlRepoConnection.commit();
        //Thread.sleep(2000);

        updateString = String.format("delete data {%s}", triple);
        sparqlRepoConnection.begin();
        sparqlRepoConnection.prepareUpdate(updateString).execute();
        sparqlRepoConnection.commit();
        //Wait for plugin to insert triples. This is managed by the server.*/
        Thread.sleep(2000);

        TupleQuery query = sparqlRepoConnection.prepareTupleQuery(String.format("select * { <<<<%s>> ?x ?y>> ?a ?b }",triple));
        try (TupleQueryResult result = query.evaluate()) {
            assertTrue("Must have two double-nested triples in the result", result.hasNext());
            assertEquals(1, result.stream().count());
        }
        try (TupleQueryResult result = query.evaluate()) {
            while (result.hasNext()) {
                BindingSet bs = result.next();
                //String s = bs.getValue("s").stringValue();
                String p = bs.getValue("a").stringValue();
                String o = bs.getValue("b").stringValue();
                System.out.println(p + " " + o);
                assertNotEquals("9999-12-31T00:00:00.000+00:00", o);

            }
        }


    }

    @After
    public void clearTestGraph() {
        /*
        String updateString;
        String graph = "";
        if (defaultGraph) {
            updateString = "clear default";
            graph = "<http://example.com/testGraph>";
        } else {
            updateString = "clear graph <http://example.com/testGraph>";
            graph = "default";
        }

        sparqlRepoConnection.begin();
        sparqlRepoConnection.prepareUpdate(updateString).execute();
        sparqlRepoConnection.commit();
        System.out.printf("%s has been cleared.", graph);

        BooleanQuery query;
        if (defaultGraph)
            query = sparqlRepoConnection.prepareBooleanQuery("ask { ?s ?p ?o }");
        else
            query = sparqlRepoConnection.prepareBooleanQuery("ask from <http://example.com/testGraph> { ?s ?p ?o }");
        boolean hasResults = query.evaluate();
        assertFalse("No triples should be in the graph anymore", hasResults);
        */
    }

    @AfterClass
    public static void shutdown() {
        //Close connection, shutdown repository and delete repository directory
        try {
            repo.shutDown();
            sparqlRepoConnection.close();
            runDocker(shutdownContainer());

            System.out.printf("Connection shutdown and repository %s removed%n", repoId);
            System.out.println("==========================GraphDB main logs==========================");
            getLog(logFilePath).forEach(System.out::println);
        } catch (NullPointerException e) {
            System.out.println("Connection is not open and can therefore be not closed.");
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
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
        printWriter.println("echo \"Logs from GraphDB for preload disabled in docker-compose file ... \"");
        printWriter.println("docker-compose up -d");

        printWriter.println("cd ..");
        printWriter.println("docker-compose up -d");

        printWriter.close();

        return tempScript;
    }

    private static int getLastLineNumber(String filePath) {
        int lastLineNumber = -1;
        try {
            ArrayList<String> logs = (ArrayList<String>) FileUtils.readLines(new File(filePath), "UTF-8");
            lastLineNumber = logs.size();
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
        return lastLineNumber;
    }

    private static ArrayList<String> getLog(String filePath) throws IOException {
        ArrayList<String> mainLog = new ArrayList<>();
        try {
            FileReader fileReader = new FileReader(filePath);
            BufferedReader buffer = new BufferedReader(fileReader);
            buffer.lines().skip(lastLineNumber).forEachOrdered(mainLog::add);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return mainLog;
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

}
