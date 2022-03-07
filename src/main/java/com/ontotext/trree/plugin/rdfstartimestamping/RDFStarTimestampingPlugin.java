package com.ontotext.trree.plugin.rdfstartimestamping;

import com.ontotext.trree.sdk.*;
import com.ontotext.trree.sdk.impl.RequestContextImpl;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.Dataset;
import org.eclipse.rdf4j.query.impl.MapBindingSet;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;
import org.eclipse.rdf4j.util.iterators.SingletonIterator;

import javax.naming.Context;
import java.util.Calendar;
import java.util.Iterator;

public class RDFStarTimestampingPlugin extends PluginBase implements StatementListener, Preprocessor, Postprocessor {

	private static final String PREFIX = "http://example.com/";

	private static final String TIME_PREDICATE = PREFIX + "time";
	private static final String GO_FUTURE_PREDICATE = PREFIX + "goInFuture";
	private static final String GO_PAST_PREDICATE = PREFIX + "goInPast";

	private int timeOffsetHrs = 0;

	private IRI timeIri;
	private IRI defaultGraphIri;

	// IDs of the entities in the entity pool
	private long timeID;
	private long goFutureID;
	private long goPastID;

	private String sparqlEndpoint = "http://ThinkPad-T14s-FK:7200/repositories/test_timestamping/statements";
	private Repository repo = new SPARQLRepository(this.sparqlEndpoint);
	private String updateString;

	// Service interface methods
	@Override
	public String getName() {
		return "rdf-star-timestamping";
	}

	// Plugin interface methods
	@Override
	public void initialize(InitReason reason, PluginConnection pluginConnection) {
		// Create IRIs to represent the entities
		timeIri = SimpleValueFactory.getInstance().createIRI(TIME_PREDICATE);
		IRI goFutureIRI = SimpleValueFactory.getInstance().createIRI(GO_FUTURE_PREDICATE);
		IRI goPastIRI = SimpleValueFactory.getInstance().createIRI(GO_PAST_PREDICATE);

		// Put the entities in the entity pool using the SYSTEM scope
		timeID = pluginConnection.getEntities().put(timeIri, Entities.Scope.SYSTEM);
		goFutureID = pluginConnection.getEntities().put(goFutureIRI, Entities.Scope.SYSTEM);
		goPastID = pluginConnection.getEntities().put(goPastIRI, Entities.Scope.SYSTEM);

		defaultGraphIri = SimpleValueFactory.getInstance().createIRI("<http://www.ontotext.com/explicit>");

		getLogger().info("rdf-star-timestamping plugin initialized!");
	}

	// Preprocessor interface methods
	@Override
	public RequestContext preprocess(Request request) {
		// We are interested only in QueryRequests

		if (request instanceof QueryRequest) {
			getLogger().info(request.getOptions().toString());
			getLogger().info(((QueryRequest) request).getTupleExpr().toString());
			QueryRequest queryRequest = (QueryRequest) request;
			Dataset dataset = queryRequest.getDataset();

			// Check if the predicate is included in the default graph. This means that we have a "FROM <our_predicate>"
			// clause in the SPARQL query.
			if ((dataset != null && dataset.getDefaultGraphs().contains(timeIri))) {
				// Create a date/time literal
				Value literal = createDateTimeLiteral();

				// Prepare a binding set with all projected variables set to the date/time literal value
				MapBindingSet result = new MapBindingSet();
				for (String bindingName : queryRequest.getTupleExpr().getBindingNames()) {
					result.addBinding(bindingName, literal);
				}

				// Create a Context object which will be available during the other phases of the request processing
				// and set the created result as an attribute.
				RequestContextImpl context = new RequestContextImpl();
				context.setAttribute("bindings", result);

				return context;
			}
		}
		// If we are not interested in the request there is no need to create a Context.
		return null;
	}

	// Postprocessor interface methods
	@Override
	public boolean shouldPostprocess(RequestContext requestContext) {
		getLogger().info("Should postprocess? Yes");
		// Postprocess only if we have created RequestContext in the Preprocess phase. Here the requestContext object
		// is the same one that we created in the preprocess(...) method.

		return requestContext != null;
	}

	@Override
	public BindingSet postprocess(BindingSet bindingSet, RequestContext requestContext) {
		// Filter all results. Returning null will remove the binding set from the returned query result.
		// We will add the result we want in the flush() phase.
		getLogger().info("Postprocessing");

		if (updateString != null) {
			getLogger().info("Timestamping previously added triple");
			try (RepositoryConnection connection = repo.getConnection()) {
				connection.begin();
				connection.prepareUpdate(updateString).execute();
				connection.commit();
				getLogger().info("Triple timestamped");
			}
			updateString = null;
			return null;
		}
		return bindingSet;
	}

	@Override
	public Iterator<BindingSet> flush(RequestContext requestContext) {
		// Get the BindingSet we created in the Preprocess phase and return it.
		// This will be returned as the query result.
		BindingSet result = (BindingSet) ((RequestContextImpl) requestContext).getAttribute("bindings");
		return new SingletonIterator<>(result);
	}

	private Literal createDateTimeLiteral() {
		// Create a literal for the current timestamp.
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.HOUR, timeOffsetHrs);

		return SimpleValueFactory.getInstance().createLiteral(calendar.getTime());
	}


	@Override
	public boolean statementAdded(long subject, long predicate, long object, long context, boolean isAddition, PluginConnection pluginConnection) {
		String s = pluginConnection.getEntities().get(subject).stringValue();
		String o = pluginConnection.getEntities().get(predicate).stringValue();
		String p = pluginConnection.getEntities().get(object).stringValue();
		getLogger().info("Statement added:" + s + " " + p + " " + o + " within context:" + context);

		updateString = String.format("insert { << %s %s %s >> " +
				"<http://example.com/metadata/versioning#valid_from> ?timestamp } " +
				"where {BIND(<http://www.w3.org/2001/XMLSchema#dateTime>(NOW()) AS ?timestamp) }",
				entityToString(pluginConnection.getEntities().get(subject)),
				entityToString(pluginConnection.getEntities().get(predicate)),
				entityToString(pluginConnection.getEntities().get(object)));

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
}
