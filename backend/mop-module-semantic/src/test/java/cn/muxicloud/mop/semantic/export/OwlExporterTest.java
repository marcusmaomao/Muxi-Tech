package cn.muxicloud.mop.semantic.export;

import static org.assertj.core.api.Assertions.assertThat;

import cn.muxicloud.mop.model.api.BranchCatalog.CatalogElement;
import cn.muxicloud.mop.model.api.BranchCatalog.CatalogSnapshot;
import cn.muxicloud.mop.semantic.iri.StableIris;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;

class OwlExporterTest {
    private static final String NAMESPACE = "https://muxi.example/ont/grid#";
    private final OwlExporter exporter = new OwlExporter();

    @Test
    void irisUseApiNamesAndStayTheSameAcrossExports() throws Exception {
        CatalogSnapshot snapshot = sample();
        String classIri = StableIris.join(NAMESPACE, "Transformer");
        assertThat(classIri).isEqualTo(NAMESPACE + "Transformer");
        assertThat(classIri).doesNotContain("01M7").doesNotContain("212175");

        byte[] first = exporter.export(snapshot, "ttl").body();
        byte[] second = exporter.export(snapshot, "ttl").body();
        assertThat(classIris(first)).contains(classIri);
        assertThat(classIris(second)).isEqualTo(classIris(first));

        OWLOntology reloaded = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(new ByteArrayInputStream(first));
        assertThat(reloaded.subClassAxiomsForSubClass(classNamed(reloaded, classIri))).isNotEmpty();
        assertThat(reloaded.objectPropertiesInSignature().map(property -> property.getIRI().toString()))
                .contains(NAMESPACE + "installedAt");
        assertThat(reloaded.individualsInSignature().map(individual -> individual.getIRI().toString()))
                .contains(NAMESPACE + "RunState_Running");
        assertThat(reloaded.axioms().anyMatch(axiom -> axiom.toString().contains("Cardinality"))).isTrue();

        Model jena = ModelFactory.createDefaultModel();
        RDFDataMgr.read(jena, new ByteArrayInputStream(first), Lang.TURTLE);
        assertThat(jena.containsResource(jena.createResource(classIri))).isTrue();

        byte[] rdf = exporter.export(snapshot, "rdfxml").body();
        Model rdfModel = ModelFactory.createDefaultModel();
        RDFDataMgr.read(rdfModel, new ByteArrayInputStream(rdf), Lang.RDFXML);
        assertThat(rdfModel.containsResource(rdfModel.createResource(classIri))).isTrue();

        byte[] owl = exporter.export(snapshot, "owl").body();
        OWLOntology owlReloaded = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(new ByteArrayInputStream(owl));
        assertThat(classIris(owlReloaded)).contains(classIri);
        assertThat(new String(owl, StandardCharsets.UTF_8)).contains("Transformer");
    }

    private static CatalogSnapshot sample() {
        return new CatalogSnapshot(NAMESPACE, List.of(
                element("cq", "CQ", "whichAssets", null, "{\"displayName\":\"有哪些设备\",\"apiName\":\"whichAssets\"}"),
                element("tx", "OBJECT_TYPE", "Transformer", null, "{\"displayName\":\"变压器\",\"apiName\":\"Transformer\",\"parentUids\":[\"st\"]}"),
                element("st", "OBJECT_TYPE", "Substation", null, "{\"displayName\":\"变电站\",\"apiName\":\"Substation\"}"),
                element("pr", "PROPERTY", "ratedVoltage", "tx", "{\"displayName\":\"额定电压\",\"apiName\":\"ratedVoltage\",\"ownerUid\":\"tx\",\"minCount\":1,\"maxCount\":1,\"valueType\":{\"kind\":\"BASIC\",\"basic\":\"DECIMAL\"}}"),
                element("lk", "LINK_TYPE", "installedAt", null, "{\"displayName\":\"安装于\",\"apiName\":\"installedAt\",\"sourceUid\":\"tx\",\"targetUid\":\"st\",\"cardinality\":\"MANY_TO_ONE\",\"invertible\":true,\"inverse\":{\"displayName\":\"安装设备\",\"apiName\":\"hasEquipment\"}}"),
                element("en", "ENUM_TYPE", "RunState", null, "{\"displayName\":\"运行状态\",\"apiName\":\"RunState\"}"),
                element("ev", "ENUM_VALUE", "Running", "en", "{\"displayName\":\"运行\",\"apiName\":\"Running\",\"ownerUid\":\"en\"}"),
                element("ax", "AXIOM", null, null, "{\"kind\":\"SUBCLASS\",\"subjectUid\":\"tx\",\"objectUid\":\"st\"}")
        ));
    }

    private static CatalogElement element(String uid, String type, String apiName, String ownerUid, String payload) {
        return new CatalogElement(uid, type, apiName, ownerUid, payload);
    }

    private static Set<String> classIris(byte[] turtle) throws Exception {
        return classIris(OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(new ByteArrayInputStream(turtle)));
    }

    private static Set<String> classIris(OWLOntology ontology) {
        return ontology.classesInSignature().map(type -> type.getIRI().toString()).collect(Collectors.toSet());
    }

    private static OWLClass classNamed(OWLOntology ontology, String iri) {
        return ontology.classesInSignature().filter(type -> type.getIRI().toString().equals(iri)).findFirst().orElseThrow();
    }
}
