package com.ontotext.trree.plugin.rdfstartimestamping;

import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.SimpleBNode;
import org.eclipse.rdf4j.model.impl.SimpleIRI;
import org.eclipse.rdf4j.model.impl.SimpleLiteral;
import org.eclipse.rdf4j.model.impl.SimpleTriple;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

class PluginUtils {

    public static String entityToString(Value value) {
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
        throw new IllegalArgumentException("The entity's type is not support. It is none of: IRI, literal, BNode, Triple");
    }

    public static String readAllBytes(String resourceName) {
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
