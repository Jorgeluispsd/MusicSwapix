package com.jorge.playlistconverter.matcher;

import org.apache.commons.text.StringEscapeUtils;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class TrackVersionMatcher {

    private static final List<Pattern> VERSION_PATTERNS = List.of(
            Pattern.compile("\\b(?:ao\\s+vivo|live"
                    + "|olympics?\\s+(?:opening|closing)\\s+ceremony)\\b"),
            Pattern.compile("\\bremix\\b"),
            Pattern.compile("\\bcover\\b"),
            Pattern.compile("\\binstrumental\\b"));

    public boolean isCompatible(String sourceTitle, String candidateTitle){
        String source = normalize(sourceTitle);
        String candidate = normalize(candidateTitle);

        return VERSION_PATTERNS.stream().allMatch(pattern ->
                pattern.matcher(source).find()
                        == pattern.matcher(candidate).find());

    }

    public String removeVersionLabels(String title){
        String result = normalize(title);

        for (Pattern pattern : VERSION_PATTERNS){
            result = pattern.matcher(result).replaceAll(" ");
        }

        return result.replaceAll("\\s+", " ").strip();
    }

    private String normalize(String title){
        return Normalizer.normalize(
                StringEscapeUtils.unescapeHtml4(title),
                Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
