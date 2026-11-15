package cn.muxicloud.mop.model.schema;

import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PayloadSchemaValidator {
    private static final Map<String, String> FILES = Map.ofEntries(
            Map.entry("OBJECT_TYPE", "/schema/OBJECT_TYPE.schema.json"),
            Map.entry("PROPERTY", "/schema/PROPERTY.schema.json"),
            Map.entry("LINK_TYPE", "/schema/LINK_TYPE.schema.json"),
            Map.entry("TERM", "/schema/TERM.schema.json"),
            Map.entry("DATA_TYPE", "/schema/DATA_TYPE.schema.json"),
            Map.entry("ENUM_TYPE", "/schema/ENUM_TYPE.schema.json"),
            Map.entry("ENUM_VALUE", "/schema/ENUM_VALUE.schema.json"),
            Map.entry("CQ", "/schema/CQ.schema.json"),
            Map.entry("AXIOM", "/schema/AXIOM.schema.json"),
            Map.entry("DATA_RULE", "/schema/DATA_RULE.schema.json"),
            Map.entry("CQ_TEST", "/schema/CQ_TEST.schema.json"),
            Map.entry("FIXTURE", "/schema/FIXTURE.schema.json"));

    private final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

    public void validate(String elementType, JsonNode payload) {
        String path = FILES.get(elementType);
        if (path == null) {
            throw new MopException(ErrorCode.SCHEMA_INVALID, "不支持的元素类型", Map.of("type", elementType));
        }
        try (InputStream input = PayloadSchemaValidator.class.getResourceAsStream(path)) {
            JsonSchema schema = factory.getSchema(input);
            Set<ValidationMessage> messages = schema.validate(payload);
            if (!messages.isEmpty()) {
                throw new MopException(ErrorCode.SCHEMA_INVALID, ErrorCode.SCHEMA_INVALID.defaultMessage(),
                        Map.of("errors", messages.stream().map(ValidationMessage::getMessage).collect(Collectors.toList())));
            }
        } catch (MopException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MopException(ErrorCode.SCHEMA_INVALID, ex.getMessage(), null);
        }
    }
}
