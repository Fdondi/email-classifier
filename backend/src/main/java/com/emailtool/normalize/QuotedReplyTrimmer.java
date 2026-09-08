package com.emailtool.normalize;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class QuotedReplyTrimmer {

    private static final Pattern ON_WROTE = Pattern.compile("(?ms)^On .+wrote:\\s*$");

    public String trim(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        int cut = lines.length;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith(">")) {
                cut = i;
                break;
            }
            if (ON_WROTE.matcher(line).matches()) {
                cut = i;
                break;
            }
            if (line.startsWith("-----Original Message-----")) {
                cut = i;
                break;
            }
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < cut; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(lines[i]);
        }
        return builder.toString().strip();
    }
}
