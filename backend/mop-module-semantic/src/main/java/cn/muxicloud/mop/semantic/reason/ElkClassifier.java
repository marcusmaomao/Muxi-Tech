package cn.muxicloud.mop.semantic.reason;

import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import cn.muxicloud.mop.model.api.BranchCatalog.CatalogElement;
import cn.muxicloud.mop.model.api.BranchCatalog.CatalogSnapshot;
import cn.muxicloud.mop.semantic.export.OwlExporter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDeclarationAxiom;
import org.semanticweb.owlapi.model.OWLDisjointClassesAxiom;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

/**
 * 用 ELK 对类层级做分类。基数等 EL 以外的公理先拿掉，避免推理机拒绝整份本体。
 */
public final class ElkClassifier {
    private final OwlExporter exporter = new OwlExporter();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Classification classify(CatalogSnapshot snapshot) {
        OWLOntology ontology = exporter.ontology(snapshot);
        keepHierarchy(ontology);
        OWLReasoner reasoner = new ElkReasonerFactory().createReasoner(ontology);
        try {
            reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
            Map<String, String> names = names(snapshot);
            List<NamedClass> unsatisfiable = new ArrayList<>();
            for (OWLClass type : reasoner.getUnsatisfiableClasses().entities().filter(item -> !item.isOWLNothing()).toList()) {
                NamedClass named = named(snapshot.namespace(), names, type);
                if (named != null) {
                    unsatisfiable.add(named);
                }
            }
            unsatisfiable.sort(Comparator.comparing(NamedClass::apiName));
            List<ClassNode> types = new ArrayList<>();
            for (OWLClass type : ontology.getClassesInSignature()) {
                NamedClass named = named(snapshot.namespace(), names, type);
                if (named == null) {
                    continue;
                }
                List<String> supers = new ArrayList<>();
                for (OWLClass parent : reasoner.getSuperClasses(type, true).entities().toList()) {
                    String api = apiName(snapshot.namespace(), parent);
                    if (api != null) {
                        supers.add(api);
                    }
                }
                supers.sort(String::compareTo);
                TreeSet<String> descendants = new TreeSet<>();
                descendants.add(named.apiName());
                for (OWLClass child : reasoner.getSubClasses(type, false).entities().toList()) {
                    String api = apiName(snapshot.namespace(), child);
                    if (api != null) {
                        descendants.add(api);
                    }
                }
                types.add(new ClassNode(named.apiName(), named.displayName(), named.iri(), List.copyOf(supers), List.copyOf(descendants)));
            }
            types.sort(Comparator.comparing(ClassNode::apiName));
            return new Classification(reasoner.isConsistent(), List.copyOf(unsatisfiable), List.copyOf(types));
        } catch (RuntimeException ex) {
            throw new MopException(ErrorCode.INTERNAL, "ELK 分类失败", null);
        } finally {
            reasoner.dispose();
        }
    }

    public List<String> expand(Classification classification, String apiName, boolean includeSubtypes) {
        ClassNode node = classification.types().stream().filter(type -> type.apiName().equals(apiName)).findFirst().orElse(null);
        if (node == null) {
            throw new MopException(ErrorCode.VALIDATION, "对象类型不存在", null);
        }
        if (!includeSubtypes) {
            return List.of(apiName);
        }
        return node.descendants();
    }

    private static void keepHierarchy(OWLOntology ontology) {
        List<OWLAxiom> drop = new ArrayList<>();
        for (OWLAxiom axiom : ontology.getAxioms()) {
            if (!hierarchyAxiom(axiom)) {
                drop.add(axiom);
            }
        }
        ontology.getOWLOntologyManager().removeAxioms(ontology, drop);
    }

    private static boolean hierarchyAxiom(OWLAxiom axiom) {
        if (axiom instanceof OWLDeclarationAxiom || axiom instanceof OWLDisjointClassesAxiom) {
            return true;
        }
        if (axiom instanceof OWLSubClassOfAxiom sub) {
            return named(sub.getSubClass()) && named(sub.getSuperClass());
        }
        if (axiom instanceof OWLEquivalentClassesAxiom equivalent) {
            return equivalent.getClassExpressions().stream().allMatch(ElkClassifier::named);
        }
        return false;
    }

    private static boolean named(OWLClassExpression expression) {
        return expression instanceof OWLClass;
    }

    private Map<String, String> names(CatalogSnapshot snapshot) {
        Map<String, String> names = new HashMap<>();
        for (CatalogElement element : snapshot.elements()) {
            if (element.apiName() == null || element.apiName().isBlank()) {
                continue;
            }
            String display = text(read(element.payload()), "displayName");
            names.put(element.apiName(), display == null ? element.apiName() : display);
        }
        return names;
    }

    private NamedClass named(String namespace, Map<String, String> names, OWLClass type) {
        String api = apiName(namespace, type);
        if (api == null) {
            return null;
        }
        return new NamedClass(api, names.getOrDefault(api, api), type.getIRI().toString());
    }

    private static String apiName(String namespace, OWLClass type) {
        String iri = type.getIRI().toString();
        if (namespace == null || !iri.startsWith(namespace) || iri.length() == namespace.length()) {
            return null;
        }
        return iri.substring(namespace.length());
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

    public record NamedClass(String apiName, String displayName, String iri) {}

    public record ClassNode(String apiName, String displayName, String iri, List<String> directSuperClasses, List<String> descendants) {}

    public record Classification(boolean consistent, List<NamedClass> unsatisfiable, List<ClassNode> types) {}
}
