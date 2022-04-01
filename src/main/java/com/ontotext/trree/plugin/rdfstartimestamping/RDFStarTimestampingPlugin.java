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

public class RDFStarTimestampingPlugin extends PluginBase implements StatementListener, PluginTransactionListener, ContextUpdateHandler {

	private static final String PREFIX = "http://example.com/";
	private String getEndpoint;
	private String postEndpoint;
	private Repository repo;
	private HashMap<String, Boolean> updateStrings;
	private boolean pluginUpdateRequestCommitted;
	private HashSet<Triple> deleteRequestTriples;
	private boolean statementRemoved;
	ExecutorService executor;



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
		statementRemoved = false;
		//executor = Executors.newSingleThreadExecutor();
		executor = new ThreadPoolExecutor(1, 2, 0, TimeUnit.SECONDS,
				new SynchronousQueue<Runnable>(), new ThreadPoolExecutor.CallerRunsPolicy());

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
		if (isAdded)
			getLogger().info("Start adding and timestamping triple procedure");
		else {
			executor.execute(()-> {
				if (!pluginUpdateRequestCommitted) {
					//handle user requests
					String cont = context == null ? "default" : context.stringValue();
					getLogger().info("Requesting delete of triple: " + subject.stringValue()
							+ " " + predicate.stringValue() + " " + object.stringValue()
							+ " within context: " + cont);
					deleteRequestTriples.add(new Triple(subject, predicate, object, context));
				} else {
					pluginUpdateRequestCommitted = false;
				}
			});
		}
	}

	@Override
	public boolean statementAdded(long subject, long predicate, long object, long context, boolean isAddition, PluginConnection pluginConnection) {
		Value s = pluginConnection.getEntities().get(subject);
		Value p = pluginConnection.getEntities().get(predicate);
		Value o = pluginConnection.getEntities().get(object);
		Value c = pluginConnection.getEntities().get(context);
		/*try {
			executor.awaitTermination(1000, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			getLogger().info(e.getMessage());
			getLogger().info(Arrays.toString(e.getStackTrace()));
		}*/
		getLogger().info("Add statement:" + s + " " + p + " " + o + " within context:" + c);

		if (!pluginUpdateRequestCommitted) {
			//handle user requests
			URL res = getClass().getClassLoader().getResource("timestampedInsertTemplate");
			assert res != null;
			String template = "";
			String cont = "default";
			if (Objects.equals(c, null))
				template = "timestampedInsertTemplate";
			 else {
				template = "timestampedInsertWithContextTemplate";
				cont = entityToString(c);
			}
			updateStrings.put(MessageFormat.format(readAllBytes(template),
					cont, entityToString(s), entityToString(p), entityToString(o)), true);
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
			//handle user requests
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
					getLogger().info("Prepare triple to outdate: " + s.stringValue() + " " + p.stringValue() + " " + o.stringValue());
				} else {
					template = "timestampedDeleteWithContextTemplate";
					context = entityToString(c);
					getLogger().info("Prepare triple to outdate: " + s.stringValue() + " " + p.stringValue() + " " + o.stringValue() + " " + c.stringValue());
				}
				updateStrings.put(MessageFormat.format(readAllBytes(template),
						context, entityToString(s), entityToString(p), entityToString(o)), false);
			}
			deleteRequestTriples.clear();
		}

	}

	@Override
	public void transactionCommit(PluginConnection pluginConnection) {
		getLogger().info("Commit transaction");

		if (!updateStrings.isEmpty() && !pluginUpdateRequestCommitted) {
			//handle user requests
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
					 //pluginUpdateRequestCommitted = false;
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







	private class Triple implements org.eclipse.rdf4j.model.Triple {
		private Resource subject;
		private IRI predicate;
		private Value object;
		private Resource context;

		public Triple(Resource subject, IRI predicate, Value object, Resource context) {
			this.subject = subject;
			this.predicate = predicate;
			this.object = object;
			this.context = context;

		}

		public Resource getSubject() {
			return this.subject;
		}

		public IRI getPredicate() {
			return this.predicate;
		}

		public Value getObject() {
			return this.object;
		}

		public Resource getContext() {
			return this.context;
		}

		@Override
		public boolean equals(Object o) {
			if (o == this)
				return true;
			if (!(o instanceof Triple)) {
				return false;
			}
			Triple t = (Triple) o;

			boolean equal = Objects.equals(t.subject.stringValue(), this.subject.stringValue()) &&
					Objects.equals(t.predicate.stringValue(), this.predicate.stringValue()) &&
					Objects.equals(t.object.stringValue(), this.object.stringValue());

			if (t.context == null || this.context == null)
				return equal;
			else
				return equal && Objects.equals(t.context.stringValue(), this.context.stringValue());
		}


		@Override
		public String stringValue() {
			return subject.stringValue() + " " + predicate.toString() + " " + object.toString();
		}

		@Override
		public int hashCode() {
			MessageDigest messageDigest = null;
			String stringHash = "";
			try {
				messageDigest = MessageDigest.getInstance("SHA-256");

				String stringToHash = subject.stringValue() + predicate.stringValue() + object.stringValue();
				if (context != null)
					stringToHash += context.stringValue();
				messageDigest.update(stringToHash.getBytes());
				stringHash = new String(messageDigest.digest());

				return stringHash.hashCode();
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			}
			return stringHash.hashCode();
		}
	}


	private String entityToString(Value value) {
		if (value instanceof SimpleIRI)
			return "<" + value + ">";
		if (value instanceof SimpleLiteral)
			return value.toString();
		if (value instanceof SimpleBNode)
			return value.toString();
		if (value instanceof SimpleTriple) {
			Value s = ((SimpleTriple) value).getSubject();
			Value p = ((SimpleTriple) value).getPredicate();
			Value o = ((SimpleTriple) value).getObject();
			return "<<" + entityToString(s) + " " + entityToString(p) + " " + entityToString(o) + ">>";
		}
		if (value instanceof Resource)
			return "<" + value + ">";
		getLogger().error("The entity's type is not support. It is none of: IRI, literal, BNode, Triple");
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


}
