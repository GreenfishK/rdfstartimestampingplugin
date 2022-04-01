package com.ontotext.trree.plugin.rdfstartimestamping;

import com.ontotext.trree.sdk.*;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.SimpleBNode;
import org.eclipse.rdf4j.model.impl.SimpleIRI;
import org.eclipse.rdf4j.model.impl.SimpleLiteral;
import org.eclipse.rdf4j.model.impl.SimpleTriple;
import org.eclipse.rdf4j.repository.Repository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.stream.Collectors;

public class PluginInterfacesPlayground extends PluginBase implements StatementListener, PluginTransactionListener, ContextUpdateHandler {

	private static final String PREFIX = "http://example.com/";
	private String getEndpoint;
	private String postEndpoint;
	private Repository repo;


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


	}

	@Override
	public Resource[] getUpdateContexts() {
		getLogger().info("getUpdateContexts");
		Resource[] res = new Resource[1];

		res[0] = () -> "";

		return res;
	}

	@Override
	public void handleContextUpdate(Resource subject, IRI predicate, Value object, Resource context, boolean isAdded, PluginConnection pluginConnection) {
		if (isAdded)
			getLogger().info("Handle insert request");
		else {
			getLogger().info("Handle delete request");

		}
	}

	@Override
	public boolean statementAdded(long subject, long predicate, long object, long context, boolean isAddition, PluginConnection pluginConnection) {
		Value s = pluginConnection.getEntities().get(subject);
		Value p = pluginConnection.getEntities().get(predicate);
		Value o = pluginConnection.getEntities().get(object);
		Value c = pluginConnection.getEntities().get(context);
		getLogger().info("Add statement:" + s + " " + p + " " + o + " within context:" + c);

		return false;
	}

	@Override
	public boolean statementRemoved(long subject, long predicate, long object, long context, boolean isAddition, PluginConnection pluginConnection) {
		Value s = pluginConnection.getEntities().get(subject);
		Value p = pluginConnection.getEntities().get(predicate);
		Value o = pluginConnection.getEntities().get(object);
		Value c = pluginConnection.getEntities().get(context);
		getLogger().info("Remove statement:" + s + " " + p + " " + o + " within context:" + c);

		return false;
	}

	@Override
	public void transactionStarted(PluginConnection pluginConnection) {
		getLogger().info("Start transaction");

	}

	@Override
	public void transactionCommit(PluginConnection pluginConnection) {
		getLogger().info("Commit transaction");
	}

	@Override
	public void transactionCompleted(PluginConnection pluginConnection) {
		getLogger().info("Complete transaction");
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
		try (InputStream in = PluginInterfacesPlayground.class.getResourceAsStream("/" +resourceName)) {
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
