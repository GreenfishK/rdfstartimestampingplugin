package com.ontotext.trree.plugin.rdfstartimestamping;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

class Triple implements org.eclipse.rdf4j.model.Triple {
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