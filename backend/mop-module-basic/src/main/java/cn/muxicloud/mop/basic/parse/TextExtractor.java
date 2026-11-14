package cn.muxicloud.mop.basic.parse;

import java.io.ByteArrayInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;

@Component
public class TextExtractor {
    private static final int MAX_CHARS = 1_000_000;

    public Extracted extract(byte[] body, String filename) {
        try {
            BodyContentHandler handler = new BodyContentHandler(MAX_CHARS);
            Metadata metadata = new Metadata();
            if (filename != null) {
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
            }
            new AutoDetectParser().parse(new ByteArrayInputStream(body), handler, metadata, new ParseContext());
            String text = handler.toString().trim();
            if (text.isEmpty()) {
                return new Extracted("EMPTY", "");
            }
            return new Extracted("OK", text);
        } catch (Exception ex) {
            return new Extracted("FAILED", "");
        }
    }

    public record Extracted(String status, String text) {
    }
}
