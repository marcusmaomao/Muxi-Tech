package cn.muxicloud.mop.model.form;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ElementRulesTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void objectsAndLinksRequireACompetencyQuestion() throws Exception {
        assertThat(ElementRules.requiresCq("OBJECT_TYPE")).isTrue();
        assertThat(ElementRules.requiresCq("LINK_TYPE")).isTrue();
        assertThat(ElementRules.requiresCq("TERM")).isFalse();
        assertThat(ElementRules.hasCq(mapper.readTree("{\"cqUids\":[]}"))).isFalse();
        assertThat(ElementRules.hasCq(mapper.readTree("{\"cqUids\":[\"01ARZ3NDEKTSV4RRFFQ69G5FAV\"]}"))).isTrue();
    }

    @Test
    void apiNameStaysFixedAndCardinalityIsOrdered() throws Exception {
        assertThat(ElementRules.apiNameChanged("Transformer", "Breaker")).isTrue();
        assertThat(ElementRules.apiNameChanged("Transformer", "Transformer")).isFalse();
        assertThat(ElementRules.cardinalityProblem(mapper.readTree("{\"minCount\":1,\"maxCount\":1}"))).isNull();
        assertThat(ElementRules.cardinalityProblem(mapper.readTree("{\"required\":true,\"minCount\":0,\"maxCount\":1}"))).contains("必填");
        assertThat(ElementRules.inverseProblem(mapper.readTree("{\"invertible\":true}"))).contains("反向");
        assertThat(ElementRules.inverseProblem(mapper.readTree("{\"invertible\":true,\"inverse\":{\"displayName\":\"安装设备\",\"apiName\":\"hasEquipment\"}}"))).isNull();
    }
}
