package com.ontotext.trree.plugin.rdfstartimestamping;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;

public class start {


    public static void main(String[] args) {
        String sparqlEndpoint = "http://ThinkPad-T14s-FK:7200/repositories/test_timestamping/statements";
        Repository repo = new SPARQLRepository(sparqlEndpoint);
        String updateString;

        try (RepositoryConnection connection = repo.getConnection()) {
            repo.init();
            connection.begin();
            updateString = "insert data {<http://example.com/s/s1111> <http://example.com/p/p1111> <http://example.com/o/o1111> }";
            connection.prepareUpdate(updateString).execute();
            connection.commit();
        }

    }
}
