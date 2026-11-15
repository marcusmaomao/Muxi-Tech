package cn.muxicloud.mop.semantic.export;

import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import cn.muxicloud.mop.model.api.BranchCatalog.CatalogElement;
import cn.muxicloud.mop.model.api.BranchCatalog.CatalogSnapshot;
import cn.muxicloud.mop.semantic.iri.StableIris;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

public class OwlExporter {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OwlDocument export(CatalogSnapshot snapshot, String format) {
        String key = format == null ? "ttl" : format.trim().toLowerCase(Locale.ROOT);
        OWLDocumentFormat documentFormat = switch (key) {
            case "ttl", "turtle" -> new TurtleDocumentFormat();
            case "rdfxml", "rdf" -> new RDFXMLDocumentFormat();
            case "owl", "owlxml" -> new OWLXMLDocumentFormat();
            default -> throw new MopException(ErrorCode.VALIDATION, "导出格式只支持 ttl、rdfxml、owl", null);
        };
        try {
            OWLOntology ontology = ontology(snapshot);
            if (documentFormat.isPrefixOWLDocumentFormat()) {
                documentFormat.asPrefixOWLDocumentFormat().setDefaultPrefix(snapshot.namespace());
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ontology.getOWLOntologyManager().saveOntology(ontology, documentFormat, out);
            return new OwlDocument(key, mediaType(key), extension(key), out.toByteArray());
        } catch (MopException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MopException(ErrorCode.INTERNAL, "本体导出失败", null);
        }
    }

    public OWLOntology ontology(CatalogSnapshot snapshot) {
        try {
            return build(snapshot);
        } catch (MopException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MopException(ErrorCode.INTERNAL, "本体导出失败", null);
        }
    }

    private OWLOntology build(CatalogSnapshot snapshot) throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLDataFactory factory = manager.getOWLDataFactory();
        OWLOntology ontology = manager.createOntology(IRI.create(snapshot.namespace()));
        Map<String, CatalogElement> byUid = new HashMap<>();
        Map<String, OWLClass> classes = new HashMap<>();
        Map<String, OWLEntity> properties = new HashMap<>();
        for (CatalogElement element : snapshot.elements()) {
            byUid.put(element.uid(), element);
        }
        for (CatalogElement element : snapshot.elements()) {
            if (!"OBJECT_TYPE".equals(element.type()) && !"ENUM_TYPE".equals(element.type())) {
                continue;
            }
            if (element.apiName() == null || element.apiName().isBlank()) {
                continue;
            }
            OWLClass type = factory.getOWLClass(IRI.create(StableIris.join(snapshot.namespace(), element.apiName())));
            classes.put(element.uid(), type);
            manager.addAxiom(ontology, factory.getOWLDeclarationAxiom(type));
            label(manager, ontology, factory, type.getIRI(), element);
        }
        for (CatalogElement element : snapshot.elements()) {
            if (!"ENUM_VALUE".equals(element.type()) || element.apiName() == null) {
                continue;
            }
            CatalogElement owner = byUid.get(element.ownerUid());
            OWLClass enumClass = classes.get(element.ownerUid());
            if (owner == null || enumClass == null || owner.apiName() == null) {
                continue;
            }
            String local = owner.apiName() + "_" + element.apiName();
            OWLNamedIndividual individual = factory.getOWLNamedIndividual(IRI.create(StableIris.join(snapshot.namespace(), local)));
            manager.addAxiom(ontology, factory.getOWLDeclarationAxiom(individual));
            manager.addAxiom(ontology, factory.getOWLClassAssertionAxiom(enumClass, individual));
            label(manager, ontology, factory, individual.getIRI(), element);
        }
        for (CatalogElement element : snapshot.elements()) {
            if (!"ENUM_TYPE".equals(element.type())) {
                continue;
            }
            List<OWLNamedIndividual> members = new ArrayList<>();
            for (CatalogElement value : snapshot.elements()) {
                if ("ENUM_VALUE".equals(value.type()) && element.uid().equals(value.ownerUid()) && value.apiName() != null && element.apiName() != null) {
                    String local = element.apiName() + "_" + value.apiName();
                    members.add(factory.getOWLNamedIndividual(IRI.create(StableIris.join(snapshot.namespace(), local))));
                }
            }
            OWLClass enumClass = classes.get(element.uid());
            if (enumClass != null && !members.isEmpty()) {
                manager.addAxiom(ontology, factory.getOWLEquivalentClassesAxiom(enumClass, factory.getOWLObjectOneOf(members)));
            }
        }
        for (CatalogElement element : snapshot.elements()) {
            JsonNode payload = read(element.payload());
            if ("PROPERTY".equals(element.type()) && element.apiName() != null) {
                JsonNode valueType = payload.get("valueType");
                boolean enumeration = valueType != null && "ENUM".equals(text(valueType, "kind"));
                IRI iri = IRI.create(StableIris.join(snapshot.namespace(), element.apiName()));
                OWLClass owner = classes.get(element.ownerUid());
                if (enumeration) {
                    OWLObjectProperty property = factory.getOWLObjectProperty(iri);
                    properties.put(element.uid(), property);
                    manager.addAxiom(ontology, factory.getOWLDeclarationAxiom(property));
                    OWLClass range = classes.get(text(valueType, "enumUid"));
                    if (range != null) {
                        manager.addAxiom(ontology, factory.getOWLObjectPropertyRangeAxiom(property, range));
                    }
                    if (owner != null) {
                        manager.addAxiom(ontology, factory.getOWLObjectPropertyDomainAxiom(property, owner));
                        addObjectCardinality(manager, ontology, factory, owner, property, payload);
                    }
                } else {
                    OWLDataProperty property = factory.getOWLDataProperty(iri);
                    properties.put(element.uid(), property);
                    manager.addAxiom(ontology, factory.getOWLDeclarationAxiom(property));
                    manager.addAxiom(ontology, factory.getOWLDataPropertyRangeAxiom(property, factory.getOWLDatatype(datatype(valueType, byUid))));
                    if (owner != null) {
                        manager.addAxiom(ontology, factory.getOWLDataPropertyDomainAxiom(property, owner));
                        addDataCardinality(manager, ontology, factory, owner, property, payload);
                    }
                }
                label(manager, ontology, factory, iri, element);
            }
            if ("LINK_TYPE".equals(element.type()) && element.apiName() != null) {
                OWLObjectProperty property = factory.getOWLObjectProperty(IRI.create(StableIris.join(snapshot.namespace(), element.apiName())));
                properties.put(element.uid(), property);
                manager.addAxiom(ontology, factory.getOWLDeclarationAxiom(property));
                OWLClass source = classes.get(text(payload, "sourceUid"));
                OWLClass target = classes.get(text(payload, "targetUid"));
                if (source != null) {
                    manager.addAxiom(ontology, factory.getOWLObjectPropertyDomainAxiom(property, source));
                    addLinkCardinality(manager, ontology, factory, source, property, text(payload, "cardinality"));
                }
                if (target != null) {
                    manager.addAxiom(ontology, factory.getOWLObjectPropertyRangeAxiom(property, target));
                }
                JsonNode inverse = payload.get("inverse");
                if (payload.path("invertible").asBoolean(false) && inverse != null && !inverse.path("apiName").asText("").isBlank()) {
                    OWLObjectProperty back = factory.getOWLObjectProperty(IRI.create(StableIris.join(snapshot.namespace(), inverse.path("apiName").asText())));
                    manager.addAxiom(ontology, factory.getOWLDeclarationAxiom(back));
                    manager.addAxiom(ontology, factory.getOWLInverseObjectPropertiesAxiom(property, back));
                    manager.addAxiom(ontology, factory.getOWLAnnotationAssertionAxiom(back.getIRI(), factory.getRDFSLabel(inverse.path("displayName").asText(inverse.path("apiName").asText()))));
                }
                label(manager, ontology, factory, property.getIRI(), element);
            }
        }
        for (CatalogElement element : snapshot.elements()) {
            JsonNode payload = read(element.payload());
            if ("OBJECT_TYPE".equals(element.type())) {
                OWLClass type = classes.get(element.uid());
                if (type == null || payload.get("parentUids") == null) {
                    continue;
                }
                for (JsonNode parent : payload.get("parentUids")) {
                    OWLClass superClass = classes.get(parent.asText());
                    if (superClass != null) {
                        manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(type, superClass));
                    }
                }
            }
            if (!"AXIOM".equals(element.type())) {
                continue;
            }
            String kind = text(payload, "kind");
            OWLClass subject = classes.get(text(payload, "subjectUid"));
            OWLClass object = classes.get(text(payload, "objectUid"));
            if ("SUBCLASS".equals(kind) && subject != null && object != null) {
                manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(subject, object));
            }
            if ("EQUIVALENT".equals(kind) && subject != null && object != null) {
                manager.addAxiom(ontology, factory.getOWLEquivalentClassesAxiom(subject, object));
            }
            if ("DISJOINT".equals(kind)) {
                List<OWLClass> disjoint = new ArrayList<>();
                if (subject != null) {
                    disjoint.add(subject);
                }
                if (object != null) {
                    disjoint.add(object);
                }
                if (payload.get("classUids") != null) {
                    for (JsonNode uid : payload.get("classUids")) {
                        OWLClass type = classes.get(uid.asText());
                        if (type != null) {
                            disjoint.add(type);
                        }
                    }
                }
                if (disjoint.size() >= 2) {
                    manager.addAxiom(ontology, factory.getOWLDisjointClassesAxiom(disjoint));
                }
            }
            if ("DOMAIN".equals(kind) || "RANGE".equals(kind)) {
                OWLEntity property = properties.get(text(payload, "subjectUid"));
                if (property instanceof OWLObjectProperty objectProperty && object != null) {
                    manager.addAxiom(ontology, "DOMAIN".equals(kind)
                            ? factory.getOWLObjectPropertyDomainAxiom(objectProperty, object)
                            : factory.getOWLObjectPropertyRangeAxiom(objectProperty, object));
                }
                if (property instanceof OWLDataProperty dataProperty && object != null) {
                    manager.addAxiom(ontology, "DOMAIN".equals(kind)
                            ? factory.getOWLDataPropertyDomainAxiom(dataProperty, object)
                            : factory.getOWLDataPropertyRangeAxiom(dataProperty, factory.getOWLDatatype(IRI.create("http://www.w3.org/2001/XMLSchema#string"))));
                }
            }
        }
        return ontology;
    }

    private void addDataCardinality(OWLOntologyManager manager, OWLOntology ontology, OWLDataFactory factory, OWLClass owner, OWLDataProperty property, JsonNode payload) {
        Integer min = integer(payload, "minCount");
        Integer max = integer(payload, "maxCount");
        if (min != null && max != null && min.equals(max)) {
            manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(owner, factory.getOWLDataExactCardinality(min, property)));
            return;
        }
        if (min != null && min > 0) {
            manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(owner, factory.getOWLDataMinCardinality(min, property)));
        }
        if (max != null) {
            manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(owner, factory.getOWLDataMaxCardinality(max, property)));
        }
    }

    private void addObjectCardinality(OWLOntologyManager manager, OWLOntology ontology, OWLDataFactory factory, OWLClass owner, OWLObjectProperty property, JsonNode payload) {
        Integer min = integer(payload, "minCount");
        Integer max = integer(payload, "maxCount");
        if (min != null && max != null && min.equals(max)) {
            manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(owner, factory.getOWLObjectExactCardinality(min, property)));
            return;
        }
        if (min != null && min > 0) {
            manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(owner, factory.getOWLObjectMinCardinality(min, property)));
        }
        if (max != null) {
            manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(owner, factory.getOWLObjectMaxCardinality(max, property)));
        }
    }

    private void addLinkCardinality(OWLOntologyManager manager, OWLOntology ontology, OWLDataFactory factory, OWLClass source, OWLObjectProperty property, String cardinality) {
        if ("ONE_TO_ONE".equals(cardinality)) {
            manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(source, factory.getOWLObjectExactCardinality(1, property)));
        } else if ("MANY_TO_ONE".equals(cardinality)) {
            manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(source, factory.getOWLObjectMaxCardinality(1, property)));
        } else if ("ONE_TO_MANY".equals(cardinality)) {
            manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(source, factory.getOWLObjectMinCardinality(1, property)));
        }
    }

    private void label(OWLOntologyManager manager, OWLOntology ontology, OWLDataFactory factory, IRI iri, CatalogElement element) {
        String name = text(read(element.payload()), "displayName");
        if (name != null) {
            manager.addAxiom(ontology, factory.getOWLAnnotationAssertionAxiom(iri, factory.getRDFSLabel(name)));
        }
    }

    private IRI datatype(JsonNode valueType, Map<String, CatalogElement> byUid) {
        String basic = valueType == null ? "STRING" : text(valueType, "basic");
        if (valueType != null && "DATA_TYPE".equals(text(valueType, "kind"))) {
            CatalogElement defined = byUid.get(text(valueType, "dataTypeUid"));
            basic = defined == null ? basic : text(read(defined.payload()), "baseType");
        }
        String local = switch (basic == null ? "" : basic) {
            case "INTEGER" -> "integer";
            case "DECIMAL" -> "decimal";
            case "BOOLEAN" -> "boolean";
            case "DATE" -> "date";
            case "DATETIME" -> "dateTime";
            default -> "string";
        };
        return IRI.create("http://www.w3.org/2001/XMLSchema#" + local);
    }

    private JsonNode read(String payload) {
        try {
            return payload == null || payload.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(payload);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText();
        return value.isBlank() ? null : value;
    }

    private static Integer integer(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asInt();
    }

    private static String mediaType(String format) {
        return switch (format) {
            case "rdfxml", "rdf" -> "application/rdf+xml";
            case "owl", "owlxml" -> "application/owl+xml";
            default -> "text/turtle";
        };
    }

    private static String extension(String format) {
        return switch (format) {
            case "rdfxml", "rdf" -> "rdf";
            case "owl", "owlxml" -> "owl";
            default -> "ttl";
        };
    }
}
