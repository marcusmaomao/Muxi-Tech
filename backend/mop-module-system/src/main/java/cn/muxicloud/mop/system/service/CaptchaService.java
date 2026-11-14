package cn.muxicloud.mop.system.service;

import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CaptchaService {
    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final boolean expose;

    public CaptchaService(@Value("${mop.captcha.expose:false}") boolean expose) {
        this.expose = expose;
    }

    public CaptchaChallenge create() {
        String code = randomCode();
        String id = UUID.randomUUID().toString();
        store.put(id, new Entry(code, Instant.now().plusSeconds(120)));
        return new CaptchaChallenge(id, svg(code), expose ? code : null);
    }

    public void verify(String id, String code) {
        Entry entry = id == null ? null : store.remove(id);
        if (entry == null || entry.expires.isBefore(Instant.now()) || code == null || !entry.code.equalsIgnoreCase(code.trim())) {
            throw new MopException(ErrorCode.CAPTCHA_INVALID);
        }
    }

    private static String randomCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder builder = new StringBuilder(4);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 4; i++) {
            builder.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return builder.toString();
    }

    private static String svg(String code) {
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"120\" height=\"40\">"
                + "<rect width=\"120\" height=\"40\" fill=\"#f3efe6\"/>"
                + "<path d=\"M5 28 C 30 8, 70 36, 115 12\" stroke=\"#c56a32\" fill=\"none\"/>"
                + "<text x=\"14\" y=\"27\" font-size=\"20\" font-family=\"sans-serif\" fill=\"#14342c\">"
                + code + "</text></svg>";
    }

    public record CaptchaChallenge(String captchaId, String imageSvg, String debugCode) {
    }

    private record Entry(String code, Instant expires) {
    }
}
