package org.chainlink.infrastructure.exceptionmapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.StreamSupport;

import ch.dvbern.dvbstarter.infrastructure.errorhandling.json.ViolationJson;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.Path.Node;
import org.apache.commons.lang3.NotImplementedException;

class ConstraintViolationParser {

    public ViolationJson parse(ConstraintViolation<?> violation) {
        return ViolationJson.of(
            buildPath(violation),
            violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
            violation.getMessage()
        );
    }

    public List<ViolationJson> parse(Collection<ConstraintViolation<?>> violation) {
        return violation.stream()
            .map(this::parse)
            .toList();
    }

    private static List<Node> asList(Path propertyPath) {
        var result = StreamSupport.stream(propertyPath.spliterator(), false)
            .toList();

        return result;
    }

    // TODO: B3000-388: REST Error Response verbessern
    // die Methode muss raus aus Violation, da sie nicht nur fuer AppValidation gebrauchtg wird
    // sondern auch fuer AppFailures. Failures werden hier aber nur mit einer RuntimeException behandelt
    // und werden damit recht unschoen ans GUI weitergeleitet.
    // In der Endausbaustufe sollte Methode wohl andhand der Teile im Path entscheiden,
    // ob eine ValidationErrorResponse oder AppFailureResponse gebaut wird.
    private String buildPath(ConstraintViolation<?> constraintViolation) {
        Path propertyPath = constraintViolation.getPropertyPath();
        Iterator<Node> iterator = propertyPath.iterator();

        if (!iterator.hasNext()) {
            throw new NotImplementedException(
                String.format("Not yet implemented: violations without path %s, %s",
                    propertyPath, constraintViolation
                ));
        }

        Node first = iterator.next();
        switch (first.getKind()) {
        case BEAN:
            if (!iterator.hasNext()) {
                return constraintViolation.getLeafBean().getClass().getSimpleName();
            }
            throw new NotImplementedException("BEAN");
        case PROPERTY:
            return buildNodePath(asList(propertyPath));
        case PARAMETER, METHOD:
            // case CONSTRUCTOR:
            // case CROSS_PARAMETER:
            // case RETURN_VALUE:
            // case CONTAINER_ELEMENT:
            List<Node> nodes = asList(propertyPath);
            List<Node> justParam = nodes.subList(2, nodes.size());

            return buildNodePath(justParam);
        default:
            throw new NotImplementedException(String.format(
                "Not yet implemented validation exception for type %s, %s",
                first.getKind(), constraintViolation
            ));
        }
    }

    private String buildNodePath(List<Node> path) {
        // example:
        // parent.child[1].property
        // => entries:
        //    parent (isInIterable=false,index=null)
        //    child  (isInIterable=false,index=null)
        //    property (isInIterable=true,index=1);

        var pathEntries = new ArrayList<String>();
        for (int i = 0; i < path.size(); i++) {
            Node next = path.get(i);

            if (next.getKey() != null) {
                throw new NotImplementedException("Path with map keys is not implemented: " + path);
            }

            pathEntries.add(i, next.getName());

            if (next.isInIterable()) {
                var prevIdx = i - 1;
                String previous = pathEntries.get(prevIdx);
                String prevIndexed = previous + '[' + next.getIndex() + ']';
                pathEntries.set(prevIdx, prevIndexed);
            }
        }

        var result = String.join(".", pathEntries);

        return result;
    }

}
