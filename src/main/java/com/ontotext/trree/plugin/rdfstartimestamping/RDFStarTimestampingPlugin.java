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
import org.eclipse.rdf4j.util.iterators.SingletonIterator;

import java.util.Calendar;
import java.util.Iterator;

public class RDFStarTimestampingPlugin extends PluginBase implements UpdateInterpreter, Preprocessor, Postprocessor {

	private static final String PREFIX = "http://example.com/";

	private static final String TIME_PREDICATE = PREFIX + "time";
	private static final String GO_FUTURE_PREDICATE = PREFIX + "goInFuture";
	private static final String GO_PAST_PREDICATE = PREFIX + "goInPast";

	private int timeOffsetHrs = 0;

	private IRI timeIri;

	// IDs of the entities in the entity pool
	private long timeID;
	private long goFutureID;
	private long goPastID;


	// Service interface methods
	@Override
	public String getName() {
		return "example";
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

		getLogger().info("Example plugin initialized!");
	}

	// UpdateInterpreter interface methods
	@Override
	public long[] getPredicatesToListenFor() {
		// We can filter the tuples we are interested in by their predicate. We are interested only
		// in tuples with have the predicate we are listening for.
		return new long[] {goFutureID, goPastID};
	}

	@Override
	public boolean interpretUpdate(long subject, long predicate, long object, long context, boolean isAddition,
								   boolean isExplicit, PluginConnection pluginConnection) {
		// Make sure that the subject is the time entity
		if (subject == timeID) {
			final String intString = pluginConnection.getEntities().get(object).stringValue();
			int step;
			try {
				step = Integer.parseInt(intString);
			} catch (NumberFormatException e) {
				// Invalid input, propagate the error to the caller
				throw new ClientErrorException("Invalid integer value: " + intString);
			}

			if (predicate == goFutureID) {
				timeOffsetHrs += step;
			} else if (predicate == goPastID) {
				timeOffsetHrs -= step;
			}

			// We handled the statement.
			// Return true so the statement will not be interpreted by other plugins or inserted in the DB
			return true;
		}

		// Tell the PluginManager that we can not interpret the tuple so further processing can continue.
		return false;
	}

	// Preprocessor interface methods
	@Override
	public RequestContext preprocess(Request request) {
		// We are interested only in QueryRequests
		if (request instanceof QueryRequest) {
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
		// Postprocess only if we have created RequestContext in the Preprocess phase. Here the requestContext object
		// is the same one that we created in the preprocess(...) method.
		return requestContext != null;
	}

	@Override
	public BindingSet postprocess(BindingSet bindingSet, RequestContext requestContext) {
		// Filter all results. Returning null will remove the binding set from the returned query result.
		// We will add the result we want in the flush() phase.
		return null;
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
}
