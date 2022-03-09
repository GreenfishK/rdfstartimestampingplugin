package com.ontotext.trree.plugin.rdfstartimestamping;

import com.ontotext.test.functional.base.SingleRepositoryFunctionalTest;
import com.ontotext.test.utils.StandardUtils;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.config.RepositoryConfig;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests the example plugin.
 */
public class TestRDFStarTimestampingPlugin extends SingleRepositoryFunctionalTest {
    @Override
    protected RepositoryConfig createRepositoryConfiguration() {
        // Creates a repository configuration with the rdfsplus-optimized ruleset
        return StandardUtils.createOwlimSe("rdfsplus-optimized");
    }

    @Test
    public void testInsertSingleTripleVersioning() {
        String updateString;
        try (RepositoryConnection connection = getRepository().getConnection()) {
            connection.begin();
            updateString = "insert data { graph <http://example.com/testGraph> {<http://example.com/s/v1> <http://example.com/p/v2> <http://example.com/o/v3> }}";
            connection.prepareUpdate(updateString).execute();
            connection.commit();

            TupleQuery query = connection.prepareTupleQuery("select * from <http://example.com/testGraph> { ?s ?p ?o }");
            try (TupleQueryResult result = query.evaluate()) {
                assertTrue("Must have at least one row in the result", result.hasNext());
                while (result.hasNext()) {
                    BindingSet bindings = result.next();
                    for (String bindingName : result.getBindingNames()) {
                        System.out.println(bindingName + ": " + bindings.getValue(bindingName));
                    }
                }
            }
        }
    }

}
