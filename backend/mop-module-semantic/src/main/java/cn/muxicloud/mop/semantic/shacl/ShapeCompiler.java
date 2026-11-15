package cn.muxicloud.mop.semantic.shacl;

import cn.muxicloud.mop.model.api.BranchCatalog.CatalogElement;
import cn.muxicloud.mop.model.api.BranchCatalog.CatalogSnapshot;
import cn.muxicloud.mop.semantic.iri.StableIris;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;

/**
 * 把属性、关系和数据规则编译成 SHACL NodeShape。自定义形状只在 Turtle 能解析时并入。
 */
public final class ShapeCompiler {
    private static final String SH = "http://www.w3.org/ns/shacl#";
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Compiled compile(CatalogSnapshot snapshot) {
        Model model = ModelFactory.createDefaultModel();
        model.setNsPrefix("sh", SH);
        model.setNsPrefix("xsd", XSD.getURI());
        model.setNsPrefix("rdfs", RDFS.getURI());
        model.setNsPrefix("", snapshot.namespace());
        Map<String, CatalogElement> byUid = new HashMap<>();
        Map<String, Resource> shapes = new HashMap<>();
        List<String> errors = new ArrayList<>();
        for (CatalogElement element : snapshot.elements()) {
            byUid.put(element.uid(), element);
        }
        for (CatalogElement element : snapshot.elements()) {
            if ("OBJECT_TYPE".equals(element.type()) && element.apiName() != null && !element.apiName().isBlank()) {
                Resource shape = model.createResource(StableIris.join(snapshot.namespace(), element.apiName() + "Shape"));
                shape.addProperty(RDF.type, resource(model, "NodeShape"));
                shape.addProperty(property(model, "targetClass"), model.createResource(StableIris.join(snapshot.namespace(), element.apiName())));
                label(shape, read(element.payload()));
                shapes.put(element.uid(), shape);
            }
        }
        for (CatalogElement element : snapshot.elements()) {
            JsonNode payload = read(element.payload());
            if ("PROPERTY".equals(element.type())) {
                addProperty(model, snapshot.namespace(), shapes, byUid, element, payload);
            } else if ("LINK_TYPE".equals(element.type())) {
                addLink(model, snapshot.namespace(), shapes, byUid, element, payload);
            } else if ("DATA_RULE".equals(element.type())) {
                addRule(model, snapshot.namespace(), shapes, byUid, payload, errors);
            } else if ("SHAPE".equals(element.type())) {
                mergeCustom(model, payload, element.apiName(), errors);
            }
        }
        StringWriter writer = new StringWriter();
        RDFDataMgr.write(writer, model, Lang.TURTLE);
        return new Compiled(writer.toString(), List.copyOf(errors));
    }

    private void addProperty(Model model, String namespace, Map<String, Resource> shapes, Map<String, CatalogElement> byUid, CatalogElement element, JsonNode payload) {
        Resource owner = shapes.get(element.ownerUid() == null ? text(payload, "ownerUid") : element.ownerUid());
        if (owner == null || element.apiName() == null) {
            return;
        }
        Resource constraint = model.createResource();
        constraint.addProperty(property(model, "path"), model.createResource(StableIris.join(namespace, element.apiName())));
        JsonNode valueType = payload.get("valueType");
        String basic = basicType(valueType, byUid);
        if (valueType != null && "ENUM".equals(text(valueType, "kind"))) {
            List<RDFNode> values = new ArrayList<>();
            String enumUid = text(valueType, "enumUid");
            for (CatalogElement candidate : byUid.values()) {
                if ("ENUM_VALUE".equals(candidate.type()) && enumUid != null && enumUid.equals(candidate.ownerUid()) && candidate.apiName() != null) {
                    values.add(model.createLiteral(candidate.apiName()));
                }
            }
            if (!values.isEmpty()) {
                constraint.addProperty(property(model, "in"), model.createList(values.toArray(RDFNode[]::new)));
            }
        } else {
            constraint.addProperty(property(model, "datatype"), model.createResource(datatype(basic)));
        }
        int min = payload.path("minCount").asInt(0);
        if (payload.path("required").asBoolean(false)) {
            min = Math.max(min, 1);
        }
        if (min > 0) {
            constraint.addProperty(property(model, "minCount"), integer(model, min));
        }
        if (payload.hasNonNull("maxCount")) {
            constraint.addProperty(property(model, "maxCount"), integer(model, payload.get("maxCount").asInt()));
        }
        addRange(model, constraint, payload, basic);
        if (payload.hasNonNull("pattern")) {
            constraint.addProperty(property(model, "pattern"), payload.get("pattern").asText());
        }
        if (payload.hasNonNull("minLength")) {
            constraint.addProperty(property(model, "minLength"), integer(model, payload.get("minLength").asInt()));
        }
        if (payload.hasNonNull("maxLength")) {
            constraint.addProperty(property(model, "maxLength"), integer(model, payload.get("maxLength").asInt()));
        }
        label(constraint, payload);
        owner.addProperty(property(model, "property"), constraint);
    }

    private void addLink(Model model, String namespace, Map<String, Resource> shapes, Map<String, CatalogElement> byUid, CatalogElement element, JsonNode payload) {
        Resource source = shapes.get(text(payload, "sourceUid"));
        CatalogElement target = byUid.get(text(payload, "targetUid"));
        if (source == null || target == null || target.apiName() == null || element.apiName() == null) {
            return;
        }
        Resource constraint = model.createResource();
        constraint.addProperty(property(model, "path"), model.createResource(StableIris.join(namespace, element.apiName())));
        constraint.addProperty(property(model, "class"), model.createResource(StableIris.join(namespace, target.apiName())));
        String cardinality = text(payload, "cardinality");
        if ("ONE_TO_ONE".equals(cardinality)) {
            constraint.addProperty(property(model, "minCount"), integer(model, 1));
            constraint.addProperty(property(model, "maxCount"), integer(model, 1));
        } else if ("MANY_TO_ONE".equals(cardinality)) {
            constraint.addProperty(property(model, "maxCount"), integer(model, 1));
        } else if ("ONE_TO_MANY".equals(cardinality)) {
            constraint.addProperty(property(model, "minCount"), integer(model, 1));
        }
        label(constraint, payload);
        source.addProperty(property(model, "property"), constraint);
    }

    private void addRule(Model model, String namespace, Map<String, Resource> shapes, Map<String, CatalogElement> byUid, JsonNode payload, List<String> errors) {
        String kind = text(payload, "kind");
        Resource owner = shapes.get(text(payload, "targetObjectUid"));
        if (owner == null) {
            errors.add("数据规则缺少目标对象");
            return;
        }
        if ("AND".equals(kind) || "OR".equals(kind)) {
            addGroup(model, namespace, byUid, owner, payload, kind, errors);
            return;
        }
        if ("SPARQL".equals(kind)) {
            String select = text(payload, "select");
            if (select == null) {
                errors.add("SPARQL 约束缺少 select");
                return;
            }
            Resource constraint = model.createResource();
            constraint.addProperty(RDF.type, resource(model, "SPARQLConstraint"));
            constraint.addProperty(property(model, "select"), select);
            message(model, constraint, payload);
            owner.addProperty(property(model, "sparql"), constraint);
            return;
        }
        Resource constraint = constraintFor(model, namespace, byUid, payload, errors);
        if (constraint == null) {
            return;
        }
        owner.addProperty(property(model, "property"), constraint);
    }

    private void addGroup(Model model, String namespace, Map<String, CatalogElement> byUid, Resource owner, JsonNode payload, String kind, List<String> errors) {
        JsonNode conditions = payload.get("conditions");
        if (conditions == null || !conditions.isArray() || conditions.isEmpty()) {
            errors.add("组合规则至少要有一个条件");
            return;
        }
        List<RDFNode> parts = new ArrayList<>();
        for (JsonNode condition : conditions) {
            Resource constraint = constraintFor(model, namespace, byUid, condition, errors);
            if (constraint == null) {
                continue;
            }
            Resource part = model.createResource();
            part.addProperty(RDF.type, resource(model, "NodeShape"));
            part.addProperty(property(model, "property"), constraint);
            parts.add(part);
        }
        if (parts.isEmpty()) {
            return;
        }
        RDFList list = model.createList(parts.toArray(RDFNode[]::new));
        owner.addProperty(property(model, "AND".equals(kind) ? "and" : "or"), list);
    }

    private Resource constraintFor(Model model, String namespace, Map<String, CatalogElement> byUid, JsonNode payload, List<String> errors) {
        String kind = text(payload, "kind");
        if (kind == null) {
            kind = "RANGE";
        }
        CatalogElement property = byUid.get(text(payload, "targetPropertyUid"));
        if (property == null || property.apiName() == null) {
            errors.add("数据规则缺少目标属性");
            return null;
        }
        Resource constraint = model.createResource();
        constraint.addProperty(property(model, "path"), model.createResource(StableIris.join(namespace, property.apiName())));
        String basic = basicType(read(property.payload()).get("valueType"), byUid);
        switch (kind) {
            case "RANGE" -> addRange(model, constraint, payload, basic);
            case "PATTERN" -> {
                if (text(payload, "pattern") == null) {
                    errors.add("格式规则缺少 pattern");
                    return null;
                }
                constraint.addProperty(property(model, "pattern"), text(payload, "pattern"));
            }
            case "LENGTH" -> {
                if (payload.hasNonNull("minLength")) {
                    constraint.addProperty(property(model, "minLength"), integer(model, payload.get("minLength").asInt()));
                }
                if (payload.hasNonNull("maxLength")) {
                    constraint.addProperty(property(model, "maxLength"), integer(model, payload.get("maxLength").asInt()));
                }
            }
            case "REQUIRED" -> constraint.addProperty(property(model, "minCount"), integer(model, 1));
            default -> {
                errors.add("不认识的数据规则 " + kind);
                return null;
            }
        }
        message(model, constraint, payload);
        return constraint;
    }

    private void addRange(Model model, Resource constraint, JsonNode payload, String basic) {
        boolean inclusive = !payload.has("inclusive") || payload.get("inclusive").asBoolean();
        JsonNode min = first(payload, "min", "minimum");
        JsonNode max = first(payload, "max", "maximum");
        if (min != null && min.isNumber()) {
            constraint.addProperty(property(model, inclusive ? "minInclusive" : "minExclusive"), number(model, min, basic));
        }
        if (max != null && max.isNumber()) {
            constraint.addProperty(property(model, inclusive ? "maxInclusive" : "maxExclusive"), number(model, max, basic));
        }
    }

    private void mergeCustom(Model model, JsonNode payload, String apiName, List<String> errors) {
        String turtle = text(payload, "turtle");
        if (turtle == null) {
            errors.add((apiName == null ? "自定义形状" : apiName) + " 缺少 Turtle");
            return;
        }
        Model extra = ModelFactory.createDefaultModel();
        try {
            RDFDataMgr.read(extra, new ByteArrayInputStream(turtle.getBytes(StandardCharsets.UTF_8)), Lang.TURTLE);
            model.add(extra);
        } catch (RiotException ex) {
            errors.add((apiName == null ? "自定义形状" : apiName) + " 的 Turtle 无法解析");
        }
    }

    private static void message(Model model, Resource constraint, JsonNode payload) {
        String text = text(payload, "message");
        if (text != null) {
            constraint.addProperty(property(model, "message"), text);
        }
        String severity = text(payload, "severity");
        if (severity != null) {
            constraint.addProperty(property(model, "severity"), resource(model, severity));
        }
    }

    private static void label(Resource resource, JsonNode payload) {
        String name = text(payload, "displayName");
        if (name != null) {
            resource.addProperty(RDFS.label, name);
        }
    }

    private String basicType(JsonNode valueType, Map<String, CatalogElement> byUid) {
        if (valueType != null && "DATA_TYPE".equals(text(valueType, "kind"))) {
            CatalogElement defined = byUid.get(text(valueType, "dataTypeUid"));
            String base = defined == null ? null : text(read(defined.payload()), "baseType");
            return base == null ? "STRING" : base;
        }
        String basic = text(valueType, "basic");
        return basic == null ? "STRING" : basic;
    }

    private static String datatype(String basic) {
        return switch (basic) {
            case "INTEGER" -> XSDDatatype.XSDinteger.getURI();
            case "DECIMAL" -> XSDDatatype.XSDdecimal.getURI();
            case "BOOLEAN" -> XSDDatatype.XSDboolean.getURI();
            case "DATE" -> XSDDatatype.XSDdate.getURI();
            case "DATETIME" -> XSDDatatype.XSDdateTime.getURI();
            default -> XSDDatatype.XSDstring.getURI();
        };
    }

    private static RDFNode integer(Model model, int value) {
        return model.createTypedLiteral(String.valueOf(value), XSDDatatype.XSDinteger);
    }

    private static RDFNode number(Model model, JsonNode value, String basic) {
        if ("INTEGER".equals(basic)) {
            return model.createTypedLiteral(value.longValue());
        }
        return model.createTypedLiteral(value.decimalValue());
    }

    private JsonNode read(String payload) {
        try {
            return payload == null || payload.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(payload);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private static JsonNode first(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }
        for (String name : names) {
            if (node.hasNonNull(name)) {
                return node.get(name);
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText();
        return value.isBlank() ? null : value;
    }

    private static Property property(Model model, String local) {
        return model.createProperty(SH + local);
    }

    private static Resource resource(Model model, String local) {
        return model.createResource(SH + local);
    }

    public record Compiled(String turtle, List<String> errors) {}
}
