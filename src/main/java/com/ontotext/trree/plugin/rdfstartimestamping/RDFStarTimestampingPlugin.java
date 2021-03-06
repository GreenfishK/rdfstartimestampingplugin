package com.ontotext.trree.plugin.rdfstartimestamping;

import com.ontotext.trree.sdk.*;
import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.impl.*;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;

import java.io.*;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class RDFStarTimestampingPlugin extends PluginBase implements StatementListener, PluginTransactionListener, ContextUpdateHandler, Preprocessor {

	private static final String PREFIX = "http://example.com/";
	private String getEndpoint;
	private String postEndpoint;
	private Repository repo;
	private HashMap<String, Boolean> updateStrings;
	private boolean pluginUpdateRequestCommitted;
	private HashSet<Triple> deleteRequestTriples;
	private boolean statementRemoved;
	ExecutorService executor;
	SynchronousQueue<Runnable> queue;


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
		this.getEndpoint = "http://localhost:7200/repositories/testTimestamping";
		this.postEndpoint = "http://localhost:7200/repositories/testTimestamping/statements";
		updateStrings = new HashMap<>();
		deleteRequestTriples = new HashSet<>();
		pluginUpdateRequestCommitted = false;
		queue = new SynchronousQueue<Runnable>();
		statementRemoved = false;
		executor = new ThreadPoolExecutor(4, 16, 2000,
				TimeUnit.SECONDS, queue, new ThreadPoolExecutor.CallerRunsPolicy());

	}

	@Override
	public Resource[] getUpdateContexts() {
		getLogger().info("getUpdateContexts");
		Resource[] res = new Resource[2];

		//TODO: Find a workaround for contexts other than default. they can for some reason not be processed.
		res[0] = () -> "";
		res[1] = () -> "http://example.com/testGraph";

		return res;
	}

	@Override
	public void handleContextUpdate(Resource subject, IRI predicate, Value object, Resource context, boolean isAdded, PluginConnection pluginConnection) {
		//getLogger().info("Is added: " + isAdded);
		if (isAdded)
			getLogger().info("Start adding and timestamping triple procedure");
		else {
			if (!pluginUpdateRequestCommitted) {
				//handle user request
				String cont = context == null ? "default" : context.stringValue();
				getLogger().info("Requesting delete of triple: " + subject.stringValue()
						+ " " + predicate.stringValue() + " " + object.stringValue()
						+ " within context: " + cont);
				deleteRequestTriples.add(new Triple(subject, predicate, object, context));
			}
		}
	}

	@Override
	public void transactionStarted(PluginConnection pluginConnection) {
		getLogger().info("Start transaction");

		/* First two conditions: if there were requests for deletion but no triples were actually removed
		   this means that the user posted a normal delete request but no triple was actually deleted
		   as the underlying structure encompasses only nested triples. These are the triples the plugin wants
		   to capture. Simple triples that were actually removed can only appear due to an insert request where
		   the plugin would replace the inserted triple by nested triples, thus remove it. These we do not want to
		   capture.
		   Third condition: Triples must have not been previously timestamped by the plugin.
		*/

		boolean userDeleteRequestCommitted = !statementRemoved && !deleteRequestTriples.isEmpty();
		if (userDeleteRequestCommitted && !pluginUpdateRequestCommitted) {
			//handle user request
			for (Triple t : deleteRequestTriples) {
				Value c = t.getContext();
				Value s = t.getSubject();
				Value p = t.getPredicate();
				Value o = t.getObject();

				URL res = getClass().getClassLoader().getResource("timestampedDeleteTemplate");
				assert res != null;
				String template = "";
				String context = "default";
				if (Objects.equals(c, null)) {
					template = "timestampedDeleteTemplate";
					getLogger().info("Prepare triples to outdate: " + s.stringValue() + " " + p.stringValue() + " " + o.stringValue());
				} else {
					template = "timestampedDeleteWithContextTemplate";
					context = PluginUtils.entityToString(c);
					getLogger().info("Prepare triples to outdate: " + s.stringValue() + " " + p.stringValue() + " " + o.stringValue() + " " + c.stringValue());
				}
				updateStrings.put(MessageFormat.format(PluginUtils.readAllBytes(template),
						context, PluginUtils.entityToString(s), PluginUtils.entityToString(p), PluginUtils.entityToString(o)), false);
			}
			deleteRequestTriples.clear();
		}

	}

	@Override
	public boolean statementAdded(long subject, long predicate, long object, long context, boolean isAddition, PluginConnection pluginConnection) {
		Value s = pluginConnection.getEntities().get(subject);
		Value p = pluginConnection.getEntities().get(predicate);
		Value o = pluginConnection.getEntities().get(object);
		Value c = pluginConnection.getEntities().get(context);

		getLogger().info("Add statement:" + s + " " + p + " " + o + " within context:" + c);

		if (!pluginUpdateRequestCommitted) {
			//handle user request
			URL res = getClass().getClassLoader().getResource("timestampedInsertTemplate");
			assert res != null;
			String template = "";
			String cont = "default";
			if (Objects.equals(c, null))
				template = "timestampedInsertTemplate";
			else {
				template = "timestampedInsertWithContextTemplate";
				cont = PluginUtils.entityToString(c);
			}
			updateStrings.put(MessageFormat.format(PluginUtils.readAllBytes(template),
					cont, PluginUtils.entityToString(s), PluginUtils.entityToString(p), PluginUtils.entityToString(o)), true);
		}
		return false;
	}

	@Override
	public boolean statementRemoved(long subject, long predicate, long object, long context, boolean isAddition, PluginConnection pluginConnection) {
		Value s = pluginConnection.getEntities().get(subject);
		Value p = pluginConnection.getEntities().get(predicate);
		Value o = pluginConnection.getEntities().get(object);
		Value c = pluginConnection.getEntities().get(context);
		getLogger().info("Remove statement:" + s + " " + p + " " + o + " within context:" + c);

		statementRemoved = true;
		return false;
	}

	@Override
	public void transactionCommit(PluginConnection pluginConnection) {
		getLogger().info("Commit transaction");

		if (!updateStrings.isEmpty() && !pluginUpdateRequestCommitted) {
			//handle user request
			executor.execute(() -> {
				 repo = new SPARQLRepository(postEndpoint);
				 try (RepositoryConnection connection = repo.getConnection()) {
					 pluginUpdateRequestCommitted = true;
					 connection.begin();
					 for (Map.Entry<String, Boolean> entry : updateStrings.entrySet()) {
						 if (entry.getValue())
							 getLogger().info("Prepare timestamped insert statement");
						 else
							 getLogger().info("Prepare timestamped delete statement");
						 connection.prepareUpdate(entry.getKey()).execute();
					 }
					 connection.commit();
				 } finally {
					 getLogger().info("Clear list of update strings and reset triplesTimestamped flag.");
					 updateStrings.clear();
					 pluginUpdateRequestCommitted = false;
				 }
			 });
		}
	}

	@Override
	public void transactionCompleted(PluginConnection pluginConnection) {
		getLogger().info("Complete transaction");
		statementRemoved = false;

	}

	@Override
	public void transactionAborted(PluginConnection pluginConnection) {
		getLogger().info("Abort transaction");

	}

	@Override
	public RequestContext preprocess(Request request) {
		if (request instanceof QueryRequest) {
			getLogger().info(String.valueOf(((QueryRequest) request).getTupleExpr().getBindingNames()));
			getLogger().info(String.valueOf(((QueryRequest) request).getBindings().getBindingNames()));
		}
		return null;
	}

}
