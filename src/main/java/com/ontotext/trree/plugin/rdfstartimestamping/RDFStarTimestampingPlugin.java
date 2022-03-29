package com.ontotext.trree.plugin.rdfstartimestamping;

import com.ontotext.trree.sdk.*;
import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.impl.*;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;

import javax.management.StringValueExp;
import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

public class RDFStarTimestampingPlugin extends PluginBase implements StatementListener, PluginTransactionListener, ContextUpdateHandler {

	private static final String PREFIX = "http://example.com/";
	private String getEndpoint;
	private String postEndpoint;
	private Repository repo;
	private ArrayList<String> updateStrings;
	private boolean triplesTimestamped;
	private HashSet<Triple> deleteRequestTriples;
	private boolean anyDeleteRequestMatch;
	public static final Object globalLock = new Object();


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
		updateStrings = new ArrayList<>();
		deleteRequestTriples = new HashSet<>();
		triplesTimestamped = false;
		anyDeleteRequestMatch = false;

	}

	@Override
	public Resource[] getUpdateContexts() {
		getLogger().info("getUpdateContexts");
		Resource[] res = new Resource[3];
		//TODO: add other contexts from db (apart from the default context/no context).
		res[0] = () -> "";
		res[1] = () -> "<http://example.com/testGraph>";
		res[2] = () -> "http://example.com/testGraph";

		return res;
	}

	@Override
	public void handleContextUpdate(Resource subject, IRI predicate, Value object, Resource context, boolean b, PluginConnection pluginConnection) {
		getLogger().info("Handling update");
		if (b)
			getLogger().info("Adding and timestamping triple");
		else {
			if (!triplesTimestamped) {
				String cont = "default";
				if (context != null)
					cont = context.stringValue();
				getLogger().info("Requesting delete of triple: " + subject.stringValue()
						+ " " + predicate.stringValue() + " " + object.stringValue()
						+ " within context: " + cont);
				deleteRequestTriples.add(new Triple(subject, predicate, object, context));
			}
		}
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
			String template = "";
			String cont = "default";
			if (Objects.equals(c, null))
				template = "timestampedInsertTemplate";
			 else {
				template = "timestampedInsertWithContextTemplate";
				cont = entityToString(c);
			}
			updateStrings.add(MessageFormat.format(readAllBytes(template),
					cont, entityToString(s), entityToString(p), entityToString(o)));
		}
		return false;
	}

	@Override
	public boolean statementRemoved(long subject, long predicate, long object, long context, boolean isAddition, PluginConnection pluginConnection) {
		Value s = pluginConnection.getEntities().get(subject);
		Value p = pluginConnection.getEntities().get(predicate);
		Value o = pluginConnection.getEntities().get(object);
		Value c = pluginConnection.getEntities().get(context);
		getLogger().info("Statement removed:" + s + " " + p + " " + o + " within context:" + c);

		anyDeleteRequestMatch = true;
		return false;
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

	@Override
	public void transactionStarted(PluginConnection pluginConnection) {
		getLogger().info("transactionStarted");
	}

	@Override
	public void transactionCommit(PluginConnection pluginConnection) {
		getLogger().info("transactionCommit");

		if(!anyDeleteRequestMatch && !deleteRequestTriples.isEmpty() && !triplesTimestamped) {
			getLogger().info("Preparing triples to outdate");
			for (Triple t: deleteRequestTriples) {
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
					getLogger().info(s.stringValue() + " " + p.stringValue() + " " + o.stringValue());
				}
				else {
					template = "timestampedDeleteWithContextTemplate";
					context = entityToString(c);
					getLogger().info(s.stringValue() + " " + p.stringValue() + " " + o.stringValue() + " " + c.stringValue());
				}
				updateStrings.add(MessageFormat.format(readAllBytes(template),
						context, entityToString(s), entityToString(p), entityToString(o)));
			}
		}

		synchronized (globalLock) {

			if (!updateStrings.isEmpty() && !triplesTimestamped) {
				Thread newThread = new Thread(() -> {
					getLogger().info("Timestamping previously added triple");
					repo = new SPARQLRepository(postEndpoint);
					try (RepositoryConnection connection = repo.getConnection()) {
						triplesTimestamped = true;
						//repo.init();
						connection.begin();
						for (String updateString : updateStrings) {
							getLogger().info(updateString);
							connection.prepareUpdate(updateString).execute();
						}
						connection.commit();
						getLogger().info("Triple timestamped");
					} finally {
						updateStrings.clear();
						triplesTimestamped = false;
					}
				});
				newThread.start();
			}
		}
	}

	@Override
	public void transactionCompleted(PluginConnection pluginConnection) {
		getLogger().info("transactionCompleted");
		anyDeleteRequestMatch = false;

	}

	@Override
	public void transactionAborted(PluginConnection pluginConnection) {
		getLogger().info("transactionAborted");

	}


}
