package cn.muxicloud.mop.semantic.shacl;

import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.validation.ReportEntry;
import org.apache.jena.shacl.validation.Severity;
import org.apache.jena.sparql.path.Path;

/**
 * 用 Jena SHACL 校验一份 Turtle 数据，并抽出可落库的违规条目。
 */
public final class ShaclEngine {
    public Result check(String shapesTurtle, String dataTurtle) {
        Graph shapes = read(shapesTurtle, "形状不是合法的 Turtle").getGraph();
        Graph data = read(dataTurtle, "待校验数据不是合法的 Turtle").getGraph();
        ValidationReport report = ShaclValidator.get().validate(shapes, data);
        List<Finding> findings = new ArrayList<>();
        for (ReportEntry entry : report.getEntries()) {
            findings.add(new Finding(severity(entry.severity()), node(entry.focusNode()), path(entry.resultPath()), entry.message()));
        }
        return new Result(report.conforms(), List.copyOf(findings));
    }

    private static Model read(String turtle, String message) {
        Model model = ModelFactory.createDefaultModel();
        try {
            byte[] bytes = (turtle == null ? "" : turtle).getBytes(StandardCharsets.UTF_8);
            RDFDataMgr.read(model, new ByteArrayInputStream(bytes), Lang.TURTLE);
            return model;
        } catch (RiotException ex) {
            throw new MopException(ErrorCode.VALIDATION, message, null);
        }
    }

    private static String severity(Severity severity) {
        if (severity == null || severity.level() == null || !severity.level().isURI()) {
            return "Violation";
        }
        String local = severity.level().getLocalName();
        return local == null || local.isBlank() ? "Violation" : local;
    }

    private static String node(Node node) {
        if (node == null) {
            return null;
        }
        if (node.isURI()) {
            return node.getURI();
        }
        return node.toString();
    }

    private static String path(Path path) {
        if (path == null) {
            return null;
        }
        String text = path.toString();
        if (text.startsWith("<") && text.endsWith(">") && text.length() > 2) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    public record Finding(String severity, String focusNode, String path, String message) {}

    public record Result(boolean conforms, List<Finding> findings) {}
}
