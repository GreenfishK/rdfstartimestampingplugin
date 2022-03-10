package com.ontotext.trree.plugin.rdfstartimestamping;
import org.eclipse.rdf4j.query.BooleanQuery;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;

public class start {


    public static void main(String[] args) {
        String sparqlEndpointPost = "http://localhost:7200/repositories/test_timestamping/statements";
        Repository repo = new SPARQLRepository(sparqlEndpointPost);
        RepositoryConnection localConnection = repo.getConnection();
        String updateString;

         try (RepositoryConnection connection = repo.getConnection()) {
            repo.init();
            connection.begin();
            updateString = "insert data { graph <http://example.com/testGraph> " +
                    "{<http://example.com/s/v11> <http://example.com/p/v21> <http://example.com/o/v31> }}";
            connection.prepareUpdate(updateString).execute();
            connection.commit();
        }
        /*URL res = start.class.getClassLoader().getResource("timestampedInsertTemplate");
        assert res != null;
        String template= readAllBytes(res);
        System.out.println(MessageFormat.format(template,"<http://example.com/testGraph>",
                "<http://example.com/s/v1>","<http://example.com/p/v2>","<http://example.com/o/v3>"));*/
    }

    private static String readAllBytes(URL url)
    {
        String content = "";
        try {
            content = new String (Files.readAllBytes(Paths.get(url.toURI())));
        }
        catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }
        return content;
    }
}
