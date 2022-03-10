package com.ontotext.trree.plugin.rdfstartimestamping;
import org.eclipse.rdf4j.query.BooleanQuery;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;

public class start {


    public static void main(String[] args) {
        String sparqlEndpoint = "http://localhost:7200/repositories/test_timestamping";
        Repository repo = new SPARQLRepository(sparqlEndpoint);
        RepositoryConnection localConnection = repo.getConnection();
        String updateString;

        /*try (RepositoryConnection connection = repo.getConnection()) {
            repo.init();
            connection.begin();
            updateString = "insert data { graph <http://example.com/testGraph> {<http://example.com/s/v1> <http://example.com/p/v2> <http://example.com/o/v3> }}";
            connection.prepareUpdate(updateString).execute();
            connection.commit();
        } */

        try (RepositoryConnection connection = localConnection) {

            /*connection.begin();
            updateString = "clear graph <http://example.com/testGraph>";
            connection.prepareUpdate(updateString).execute();
            connection.commit();*/

            BooleanQuery query = connection.prepareBooleanQuery("ask from <http://example.com/testGraph> { ?s ?p ?o }");
            boolean hasResults = query.evaluate();
            System.out.println(hasResults);

        }

    }
}
