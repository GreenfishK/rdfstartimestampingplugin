package com.ontotext.trree.plugin.rdfstartimestamping;

import com.ontotext.trree.sdk.*;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.SimpleBNode;
import org.eclipse.rdf4j.model.impl.SimpleIRI;
import org.eclipse.rdf4j.model.impl.SimpleLiteral;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.stream.Collectors;

public class RDFStarTimestampingPlugin extends PluginBase implements StatementListener, PluginTransactionListener{

	private static final String PREFIX = "http://example.com/";
	private String getEndpoint;
	private String postEndpoint;
	private Repository repo;
	private String updateString;
	private boolean triplesTimestamped = false;


	// Service interface methods
	@Override
	public String getName() {
		return "rdf-star-timestamping";
	}

	// Plugin interface methods
	@Override
	public void initialize(InitReason reason, PluginConnection pluginConnection) {
		// Create IRIs to represent the entities
		getLogger().info("rdf-star-timestamping plugin initialized!");
		this.getEndpoint = "http://localhost:7200/repositories/test_timestamping";
		this.postEndpoint = "http://localhost:7200/repositories/test_timestamping/statements";
	}

	@Override
	public boolean statementAdded(long subject, long predicate, long object, long context, boolean isAddition, PluginConnection pluginConnection) {
		Value s = pluginConnection.getEntities().get(subject);
		Value p = pluginConnection.getEntities().get(predicate);
		Value o = pluginConnection.getEntities().get(object);
		Value c = pluginConnection.getEntities().get(context);
		getLogger().info("Statement added:" + s + " " + p + " " + o + " within context:" + c);

		if (!triplesTimestamped) {
			URL res = getClass().getClassLoader().getResource("timestampedInsertTemplate");
			assert res != null;
			getLogger().info(res.getPath());

			updateString = MessageFormat.format(readAllBytes("timestampedInsertTemplate"),
					entityToString(c), entityToString(s), entityToString(p), entityToString(o));
			getLogger().info(updateString);
		}

		return false;
	}

	@Override
	public boolean statementRemoved(long subject, long predicate, long object, long context, boolean isAddition, PluginConnection pluginConnection) {
		String s = pluginConnection.getEntities().get(subject).stringValue();
		String o = pluginConnection.getEntities().get(predicate).stringValue();
		String p = pluginConnection.getEntities().get(object).stringValue();
		getLogger().info("Statement deleted:" + s + " " + p + " " + o + " from context:" + context);
		return false;
	}

	private String entityToString(Value value) {
		if (value instanceof SimpleIRI)
			return "<" + value + ">";
		if (value instanceof SimpleLiteral)
			return value.toString();
		if (value instanceof SimpleBNode)
			return value.toString();
		getLogger().error("The entity's type is not support. It is none of: IRI, literal, BNode");
		return null;
	}

	private static String readAllBytes(String resourceName) {
		String text = "";
		try (InputStream in = RDFStarTimestampingPlugin.class.getResourceAsStream("/" +resourceName)) {
			assert in != null;
			text = new BufferedReader(
					new InputStreamReader(in))
					.lines()
					.collect(Collectors.joining("\n"));
		}  catch (IOException e) {
			e.printStackTrace();
		}
		return text;
	}

	@Override
	public void transactionStarted(PluginConnection pluginConnection) {
		getLogger().info("transactionStarted");
	}

	@Override
	public void transactionCommit(PluginConnection pluginConnection) {
		getLogger().info("transactionCommit");

		if (updateString != null && !triplesTimestamped) {
			Thread newThread = new Thread(() -> {
				getLogger().info("Timestamping previously added triple");
				repo = new SPARQLRepository(postEndpoint);
				try (RepositoryConnection connection = repo.getConnection()) {
					triplesTimestamped = true;
					repo.init();
					connection.begin();
					getLogger().info(updateString);
					connection.prepareUpdate(updateString).execute();
					connection.commit();
					getLogger().info("Triple timestamped");

				} finally {
					updateString = null;
					triplesTimestamped = false;
				}
			});
			newThread.start();

		}
	}

	@Override
	public void transactionCompleted(PluginConnection pluginConnection) {
		getLogger().info("transactionCompleted");

	}

	@Override
	public void transactionAborted(PluginConnection pluginConnection) {
		getLogger().info("transactionAborted");

	}
}
