package com.ontotext.trree.plugin.rdfstartimestamping;

import com.ontotext.test.functional.base.SingleRepositoryFunctionalTest;
import com.ontotext.test.utils.StandardUtils;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.BooleanQuery;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.config.RepositoryConfig;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests the example plugin.
 */
public class TestRDFStarTimestampingPlugin extends SingleRepositoryFunctionalTest {
    private static RepositoryConnection localConnectionGet;
    private static RepositoryConnection localConnectionPost;

    @Override
    protected RepositoryConfig createRepositoryConfiguration() {
        // Creates a repository configuration with the rdfsplus-optimized ruleset
        return StandardUtils.createOwlimSe("rdfsplus-optimized");
    }

    @BeforeClass
    public static void init() {
        localConnectionGet = new SPARQLRepository("http://localhost:7200/repositories/test_timestamping").getConnection();
        localConnectionPost = new SPARQLRepository("http://localhost:7200/repositories/test_timestamping/statements").getConnection();
    }

    @Test
    public void dummyTest() {
        assertTrue("dummy test", true == true);
    }

    @Test
    public void testInsertSingleTripleVersioning() {
        String updateString;
        try (RepositoryConnection connection = localConnectionPost) {

            connection.begin();
            updateString = "insert data { graph <http://example.com/testGraph> {<http://example.com/s/v1> <http://example.com/p/v2> <http://example.com/o/v3> }}";
            connection.prepareUpdate(updateString).execute();
            connection.commit();
        }

        try (RepositoryConnection connection = localConnectionGet) {
            TupleQuery query = connection.prepareTupleQuery("select * from <http://example.com/testGraph> { ?s ?p ?o }");
            try (TupleQueryResult result = query.evaluate()) {
                assertTrue("Must have three triples - one data and two nested triples in the result", result.hasNext());
                while (result.hasNext()) {
                    BindingSet bindings = result.next();
                    for (String bindingName : result.getBindingNames()) {
                        System.out.println(bindingName + ": " + bindings.getValue(bindingName));
                    }
                }
            }
        }
    }

    @After
    public void resetRepository() {
        try (RepositoryConnection connection = localConnectionPost) {

            connection.begin();
            String updateString = "clear graph <http://example.com/testGraph>";
            connection.prepareUpdate(updateString).execute();
            connection.commit();
        }

        try (RepositoryConnection connection = localConnectionGet) {
            BooleanQuery query = connection.prepareBooleanQuery("ask from <http://example.com/testGraph> { ?s ?p ?o }");
            boolean hasResults = query.evaluate();
            assertFalse("No triples should be in the graph anymore", hasResults);
        }
    }

}
