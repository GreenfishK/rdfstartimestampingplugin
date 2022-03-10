package com.ontotext.trree.plugin.rdfstartimestamping;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.util.Models;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.BooleanQuery;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.config.RepositoryConfig;
import org.eclipse.rdf4j.repository.config.RepositoryConfigException;
import org.eclipse.rdf4j.repository.config.RepositoryConfigSchema;
import org.eclipse.rdf4j.repository.manager.LocalRepositoryManager;
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

import static org.junit.Assert.*;

/**
 * Tests the example plugin.
 */
public class TestRDFStarTimestampingPlugin  {
    private static LocalRepositoryManager repositoryManager;
    private static RepositoryConnection embeddedRepoCon;

    @BeforeClass
    public static void init() {
        try {
            File baseDir = new File("target","GraphDB");
            if (!baseDir.exists())
                baseDir.mkdirs();

            //Create local repo
            repositoryManager = new LocalRepositoryManager(baseDir);
            repositoryManager.init();
            if (repositoryManager.hasRepositoryConfig("testTimestamping"))
                throw new RuntimeException("Repository evalGraphDB already exists.");
            InputStream config = TestRDFStarTimestampingPlugin.class.getResourceAsStream("/repo-defaults.ttl");
            Model repo_config_graph = Rio.parse(config, "", RDFFormat.TURTLE);
            Resource repositoryNode = Models.subject(repo_config_graph.filter(null, RDF.TYPE, RepositoryConfigSchema.REPOSITORY)).orElse(null);
            repo_config_graph.add(repositoryNode, RepositoryConfigSchema.REPOSITORYID,
                    SimpleValueFactory.getInstance().createLiteral("testTimestamping"));
            RepositoryConfig repositoryConfig = RepositoryConfig.create(repo_config_graph, repositoryNode);
            repositoryManager.addRepositoryConfig(repositoryConfig);

            //Initialize repo
            Repository repo = repositoryManager.getRepository("testTimestamping");
            repo.init();

            //Establish connection to repo
            embeddedRepoCon = repo.getConnection();

        } catch (RDFHandlerException | RDFParseException | IOException | RepositoryConfigException | RepositoryException  e) {
            System.err.println("The GraphDB repository will not be created.");
            System.err.println(e.getMessage());
        }

    }

    @Test
    public void dummyTest() {
        assertTrue("dummy test", true == true);
        System.out.println("Dummy test passed");

    }

    @Test
    public void repoConnectionTest() {
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
    public void insertSingleTripleVersioningTest() {
        String updateString;
        embeddedRepoCon.begin();
        updateString = "insert data { graph <http://example.com/testGraph> {<http://example.com/s/v1> <http://example.com/p/v2> <http://example.com/o/v3> }}";
        embeddedRepoCon.prepareUpdate(updateString).execute();
        embeddedRepoCon.commit();

        TupleQuery query = embeddedRepoCon.prepareTupleQuery("select * from <http://example.com/testGraph> { ?s ?p ?o }");
        try (TupleQueryResult result = query.evaluate()) {
            assertTrue("Must have three triples - one data and two nested triples in the result", result.hasNext());
            System.out.println(result.stream().count());
            while (result.hasNext()) {
                BindingSet bindings = result.next();
                for (String bindingName : result.getBindingNames()) {
                    System.out.println(bindingName + ": " + bindings.getValue(bindingName));
                }
            }
        }
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
        } catch (NullPointerException e) {
            System.out.println("Connection is not open and can therefore be not closed.");
        }
        finally {
            repositoryManager.getRepository("testTimestamping").shutDown();
            repositoryManager.removeRepository("testTimestamping");
            repositoryManager.shutDown();

            System.out.println("Connection shutdown and repository 'testTimestamping' removed");
        }
    }

}
