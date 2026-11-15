package cn.muxicloud.mop.semantic.quality;

import cn.muxicloud.mop.semantic.quality.QualityCheck.Finding;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 分数由这次运行的问题扣分得到，不使用写死的总分。 */
public final class QualityScore {
    private static final List<String> DIMENSIONS = List.of("STRUCTURE", "SEMANTIC", "CONSTRAINT", "GOAL");

    private QualityScore() {
    }

    public static Scored score(List<Finding> findings) {
        Map<String, BigDecimal> dimensions = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal weight = BigDecimal.ONE.divide(BigDecimal.valueOf(DIMENSIONS.size()), 4, RoundingMode.HALF_UP);
        for (String dimension : DIMENSIONS) {
            int penalty = findings.stream()
                    .filter(finding -> dimension.equals(finding.dimension()))
                    .mapToInt(finding -> penalty(finding.severity()))
                    .sum();
            BigDecimal points = BigDecimal.valueOf(Math.max(0, 100 - Math.min(100, penalty)));
            dimensions.put(dimension, points);
            total = total.add(points.multiply(weight));
        }
        return new Scored(total.setScale(2, RoundingMode.HALF_UP), dimensions);
    }

    public static boolean blocks(List<Finding> findings) {
        return findings.stream().anyMatch(finding -> "Violation".equals(finding.severity()));
    }

    private static int penalty(String severity) {
        return switch (severity) {
            case "Violation" -> 20;
            case "Warning" -> 5;
            default -> 1;
        };
    }

    public record Scored(BigDecimal total, Map<String, BigDecimal> dimensions) {}
}
