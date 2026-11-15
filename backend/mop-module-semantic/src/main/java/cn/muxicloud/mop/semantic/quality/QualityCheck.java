package cn.muxicloud.mop.semantic.quality;

import java.util.List;

public interface QualityCheck {
    String code();

    String dimension();

    List<Finding> run(QualityContext context);

    record Finding(String code, String dimension, String severity, String elementUid, String message) {}
}
