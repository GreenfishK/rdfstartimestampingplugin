package com.ontotext.trree.plugin.rdfstartimestamping;

import com.ontotext.trree.sdk.*;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;

public class RDFStarTimestampingPlugin extends PluginBase implements StatementListener, PluginTransactionListener{

	private static final String PREFIX = "http://example.com/";

	private static final String TIME_PREDICATE = PREFIX + "time";
	private static final String GO_FUTURE_PREDICATE = PREFIX + "goInFuture";
	private static final String GO_PAST_PREDICATE = PREFIX + "goInPast";

	private final Repository repo = new SPARQLRepository("http://ThinkPad-T14s-FK:7200/repositories/test_timestamping/statements");
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
	}

	@Override
	public boolean statementAdded(long subject, long predicate, long object, long context, boolean isAddition, PluginConnection pluginConnection) {
		String s = pluginConnection.getEntities().get(subject).stringValue();
		String o = pluginConnection.getEntities().get(predicate).stringValue();
		String p = pluginConnection.getEntities().get(object).stringValue();
		getLogger().info("Statement added:" + s + " " + p + " " + o + " within context:" + context);

		if (!triplesTimestamped) {
			updateString = String.format("insert { << %s %s %s >> " +
							"<http://example.com/metadata/versioning#valid_from> ?timestamp } " +
							"where {BIND(<http://www.w3.org/2001/XMLSchema#dateTime>(NOW()) AS ?timestamp) }",
					entityToString(pluginConnection.getEntities().get(subject)),
					entityToString(pluginConnection.getEntities().get(predicate)),
					entityToString(pluginConnection.getEntities().get(object)));
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
		if (value.isIRI())
			return "<" + value + ">";
		if (value.isLiteral())
			return value.toString();
		if (value.isBNode())
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
			try (RepositoryConnection connection = repo.getConnection()) {
				triplesTimestamped = true;
				repo.init();
				connection.begin();
				connection.prepareUpdate(updateString).execute();
				connection.commit();
				getLogger().info("Triple timestamped");
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
