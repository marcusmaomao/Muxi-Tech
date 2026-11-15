package cn.muxicloud.mop.semantic.cq;

import cn.muxicloud.mop.model.api.BranchCatalog.CatalogElement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 覆盖率只来自本次通过的用例。能力问题上的 acceptStatus 不参与计算。
 */
public final class CqCoverage {
    private CqCoverage() {
    }

    public static Report calculate(String namespace, List<CatalogElement> elements, List<CaseResult> results) {
        List<CatalogElement> cqs = elements.stream().filter(element -> "CQ".equals(element.type())).toList();
        List<CatalogElement> model = elements.stream()
                .filter(element -> "OBJECT_TYPE".equals(element.type()) || "PROPERTY".equals(element.type()) || "LINK_TYPE".equals(element.type()))
                .toList();
        int cqPassed = 0;
        for (CatalogElement cq : cqs) {
            List<CaseResult> mine = results.stream().filter(result -> cq.uid().equals(result.cqUid())).toList();
            if (!mine.isEmpty() && mine.stream().allMatch(CaseResult::passed)) {
                cqPassed++;
            }
        }
        Set<String> covered = new HashSet<>();
        for (CaseResult result : results) {
            if (!result.passed()) {
                continue;
            }
            covered.addAll(result.covers());
            for (CatalogElement element : model) {
                if (element.apiName() != null && mentions(result.query(), namespace, element.apiName())) {
                    covered.add(element.uid());
                }
            }
        }
        int elementPassed = (int) model.stream().filter(element -> covered.contains(element.uid())).count();
        return new Report(cqs.size(), cqPassed, rate(cqPassed, cqs.size()), model.size(), elementPassed, rate(elementPassed, model.size()));
    }

    static boolean mentions(String query, String namespace, String apiName) {
        if (query == null || apiName == null || apiName.isBlank()) {
            return false;
        }
        if (namespace != null && query.contains(namespace + apiName)) {
            return true;
        }
        return Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(apiName) + "(?![A-Za-z0-9_])").matcher(query).find();
    }

    private static double rate(int passed, int total) {
        if (total == 0) {
            return 1;
        }
        return Math.round(passed * 1000.0 / total) / 1000.0;
    }

    public record CaseResult(String cqUid, boolean passed, String query, Set<String> covers) {}

    public record Report(int cqTotal, int cqPassed, double cqRate, int elementTotal, int elementPassed, double elementRate) {
        public boolean gateOpen(double threshold) {
            return cqTotal == 0 || cqRate + 1e-9 >= threshold;
        }
    }
}
