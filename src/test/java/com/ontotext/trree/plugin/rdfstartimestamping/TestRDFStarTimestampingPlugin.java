package com.ontotext.trree.plugin.rdfstartimestamping;

import org.eclipse.rdf4j.http.client.SPARQLProtocolSession;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.util.Models;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.BooleanQuery;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.config.*;
import org.eclipse.rdf4j.repository.manager.LocalRepositoryManager;
import org.eclipse.rdf4j.repository.manager.RemoteRepositoryManager;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.Rio;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

import static org.junit.Assert.*;

/**
 * Tests the example plugin.
 */
public class TestRDFStarTimestampingPlugin  {
    private static LocalRepositoryManager repositoryManager;
    //private static RemoteRepositoryManager remoteRepoManager;
    private static RepositoryConnection embeddedRepoCon;
    //private static RepositoryConnection remoteRepoCon;



    @BeforeClass
    public static void init() {
        try {
            //Repo directory management
            File baseDir = new File("target","GraphDB");
            if (!baseDir.exists())
                baseDir.mkdirs();
            File repoDirectory = new File("target/GraphDB/repositories/testTimestamping");
            if(repoDirectory.exists()) {
                Files.walk(repoDirectory.toPath())
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                System.out.println("Repository removed.");
            }

            //Create local repository
            repositoryManager = new LocalRepositoryManager(baseDir);
            repositoryManager.init();

            //create remote repo
            //remoteRepoManager = new RemoteRepositoryManager("http://localhost:7200/repositories/testTimestamping");
            //remoteRepoManager.init();

            //Add repository config to local repository manager
            InputStream config = TestRDFStarTimestampingPlugin.class.getResourceAsStream("/repo-defaults.ttl");
            Model repo_config_graph = Rio.parse(config, "", RDFFormat.TURTLE);
            Resource repositoryNode = Models.subject(repo_config_graph.filter(null, RDF.TYPE, RepositoryConfigSchema.REPOSITORY)).orElse(null);
            RepositoryConfig repositoryConfig = RepositoryConfig.create(repo_config_graph, repositoryNode);
            repositoryManager.addRepositoryConfig(repositoryConfig);

            //Add repository config ro remote repository manager
            //remoteRepoManager.addRepositoryConfig(repositoryConfig);

            //Initialize local repository
            SailRepository repo = (SailRepository) repositoryManager.getRepository("testTimestamping");
            repo.init();

            //Initialize remote repository
            //SPARQLRepository rrepo = (SPARQLRepository) remoteRepoManager.getRepository("testTimestamping");
            //rrepo.init();

            //Copy static config file to the target folder. This is because the config file does not get parsed correctly.
            Path source = Paths.get("src/test/resources/config.ttl");
            Path destination = Paths.get("target/GraphDB/repositories/testTimestamping/config.ttl");
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);

            //Establish connection to local repository
            embeddedRepoCon = repo.getConnection();

            //Establish connection to remote repository
            //remoteRepoCon = rrepo.getConnection();

        } catch (RDFHandlerException | RDFParseException | IOException | RepositoryConfigException | RepositoryException  e) {
            System.err.println("The GraphDB repository will not be created.");
            System.err.println(e.getMessage());
        }

    }

    @Test
    public void repoSailConnectionTest() {
        //Test queries
        BooleanQuery query = embeddedRepoCon.prepareBooleanQuery("ask from <http://example.com/testGraph> { ?s ?p ?o }");
        boolean hasResults = query.evaluate();
        assertFalse("No triples should be in the graph yet.", hasResults);
        System.out.println("Result from ask query: " + hasResults);
        System.out.println("Read queries are executable against the embedded repository");

        // Test update statements
        String updateString;
        updateString = "clear graph <http://example.com/testGraph>";
        embeddedRepoCon.prepareUpdate(updateString).execute();
        embeddedRepoCon.commit();

        updateString = "delete data {graph <http://example.com/testGraph> " +
                "{<http://example.com/s/v1> <http://example.com/p/v2> <http://example.com/o/v3>}}";
        embeddedRepoCon.prepareUpdate(updateString).execute();
        embeddedRepoCon.commit();
        System.out.println("Write statements are executable against the embedded repository");

    }

    @Test
    public void repoSPARQLConnectionTest() {
        //Test queries
        SPARQLRepository repo = new SPARQLRepository("http://localhost:7200/repositories/testTimestamping");
        repo.init();
        try (RepositoryConnection connection = repo.getConnection()) {
            BooleanQuery query = connection.prepareBooleanQuery("ask from <http://example.com/testGraph> { ?s ?p ?o }");
            boolean hasResults = query.evaluate();
            assertFalse("No triples should be in the graph yet.", hasResults);
            System.out.println("Result from ask query: " + hasResults);
            System.out.println("Read queries are executable against the embedded repository");
        }
        repo.shutDown();

        // Test update statements
        repo = new SPARQLRepository("http://localhost:7200/repositories/testTimestamping/statements");
        repo.init();
        try (RepositoryConnection connection = repo.getConnection()) {
            String updateString;
            updateString = "clear graph <http://example.com/testGraph>";
            connection.begin();
            connection.prepareUpdate(updateString).execute();
            connection.commit();

            updateString = "delete data {graph <http://example.com/testGraph> " +
                    "{<http://example.com/s/v1> <http://example.com/p/v2> <http://example.com/o/v3>}}";
            connection.prepareUpdate(updateString).execute();
            connection.commit();
            System.out.println("Write statements are executable against the embedded repository");
        }
    }

    @Test
    public void insertSingleTripleVersioningTest() {
        String updateString;
        embeddedRepoCon.begin();
        updateString = "insert data { graph <http://example.com/testGraph> {<http://example.com/s/v1> <http://example.com/p/v2> <http://example.com/o/v3> }}";
        embeddedRepoCon.prepareUpdate(updateString).execute();
        embeddedRepoCon.commit();

        TupleQuery query = embeddedRepoCon.prepareTupleQuery("select * from <http://example.com/testGraph> { ?s ?p ?o }");
        try (TupleQueryResult result = query.evaluate()) {
            assertTrue("Must have three triples - one data and two nested triples in the result", result.hasNext());
            assertEquals(3, result.stream().count());
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
        embeddedRepoCon.prepareUpdate(updateString).execute();
        embeddedRepoCon.commit();

        BooleanQuery query = embeddedRepoCon.prepareBooleanQuery("ask from <http://example.com/testGraph> { ?s ?p ?o }");
        boolean hasResults = query.evaluate();
        assertFalse("No triples should be in the graph anymore", hasResults);
        System.out.println("<http://example.com/testGraph> has been cleared.");

    }

    @AfterClass
    public static void shutdown() {
        //Close connection, shutdown repository and delete repository directory
        try {
            embeddedRepoCon.close();
            repositoryManager.getRepository("testTimestamping").shutDown();
            repositoryManager.shutDown();
            System.out.println("Connection shutdown and repository 'testTimestamping' removed");
        } catch (NullPointerException e) {
            System.out.println("Connection is not open and can therefore be not closed.");
        }

    }

}
