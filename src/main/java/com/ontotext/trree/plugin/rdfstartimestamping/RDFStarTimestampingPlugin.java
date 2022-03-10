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

import java.util.Arrays;

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
		Value o = pluginConnection.getEntities().get(predicate);
		Value p = pluginConnection.getEntities().get(object);
		getLogger().info("Statement added:" + s + " " + p + " " + o + " within context:" + context);

		if (!triplesTimestamped) {
			updateString = String.format("insert { << %s %s %s >> " +
							"<http://example.com/metadata/versioning#valid_from> ?timestamp } " +
							"where {BIND(<http://www.w3.org/2001/XMLSchema#dateTime>(NOW()) AS ?timestamp) }",
					entityToString(s),
					entityToString(o),
					entityToString(p));
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

	@Override
	public void transactionStarted(PluginConnection pluginConnection) {
		getLogger().info("transactionStarted");
	}

	@Override
	public void transactionCommit(PluginConnection pluginConnection) {
		getLogger().info("transactionCommit");

	}

	@Override
	public void transactionCompleted(PluginConnection pluginConnection) {
		getLogger().info("transactionCompleted");

			if (updateString != null) {
				getLogger().info("Timestamping previously added triple");
				repo = new SPARQLRepository(postEndpoint);
				try (RepositoryConnection connection = repo.getConnection()) {
					triplesTimestamped = true;
					repo.init();
					Thread newThread = new Thread(() -> {
						connection.begin();
						connection.prepareUpdate(updateString).execute();
						connection.commit();
						getLogger().info("Triple timestamped");
					});
					newThread.start();
				} finally {
					updateString = null;
					triplesTimestamped = false;
				}
			}
	}

	@Override
	public void transactionAborted(PluginConnection pluginConnection) {
		getLogger().info("transactionAborted");

	}
}
