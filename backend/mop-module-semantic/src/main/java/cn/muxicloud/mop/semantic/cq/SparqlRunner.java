package cn.muxicloud.mop.semantic.cq;

import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;

public final class SparqlRunner {
    public Result run(String sparql, String tboxTurtle, String fixtureTurtle) {
        rejectUpdate(sparql);
        Model model = ModelFactory.createDefaultModel();
        read(model, tboxTurtle, "本体不是合法的 Turtle");
        read(model, fixtureTurtle, "夹具不是合法的 Turtle");
        Dataset dataset = DatasetFactory.create(model);
        Query query;
        try {
            query = QueryFactory.create(sparql);
        } catch (QueryParseException ex) {
            throw new MopException(ErrorCode.VALIDATION, "SPARQL 无法解析", null);
        }
        if (!query.isSelectType() && !query.isAskType() && !query.isConstructType()) {
            throw new MopException(ErrorCode.VALIDATION, "SPARQL 只允许 SELECT、ASK 或 CONSTRUCT", null);
        }
        try (QueryExecution execution = QueryExecution.create()
                .dataset(dataset)
                .query(query)
                .timeout(10, TimeUnit.SECONDS)
                .build()) {
            if (query.isAskType()) {
                return new Result(true, execution.execAsk(), List.of());
            }
            if (query.isConstructType()) {
                Model constructed = execution.execConstruct();
                List<Map<String, String>> rows = new ArrayList<>();
                constructed.listStatements().forEachRemaining(statement -> {
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("s", statement.getSubject().toString());
                    row.put("p", statement.getPredicate().toString());
                    row.put("o", statement.getObject().toString());
                    rows.add(row);
                });
                return new Result(false, null, rows);
            }
            ResultSet selected = execution.execSelect();
            List<String> names = selected.getResultVars();
            List<Map<String, String>> rows = new ArrayList<>();
            while (selected.hasNext()) {
                var solution = selected.next();
                Map<String, String> row = new LinkedHashMap<>();
                for (String name : names) {
                    if (solution.contains(name)) {
                        row.put(name, solution.get(name).toString());
                    }
                }
                rows.add(row);
            }
            return new Result(false, null, rows);
        }
    }

    private static void rejectUpdate(String sparql) {
        String upper = sparql == null ? "" : sparql.toUpperCase(Locale.ROOT);
        for (String word : List.of("INSERT", "DELETE", "LOAD", "CLEAR", "DROP", "CREATE", "MOVE", "COPY")) {
            if (upper.contains(word)) {
                throw new MopException(ErrorCode.VALIDATION, "SPARQL 只允许读查询", null);
            }
        }
    }

    private static void read(Model model, String turtle, String message) {
        if (turtle == null || turtle.isBlank()) {
            return;
        }
        try {
            RDFDataMgr.read(model, new ByteArrayInputStream(turtle.getBytes(StandardCharsets.UTF_8)), Lang.TURTLE);
        } catch (RiotException ex) {
            throw new MopException(ErrorCode.VALIDATION, message, null);
        }
    }

    public record Result(boolean ask, Boolean askValue, List<Map<String, String>> rows) {}
}
