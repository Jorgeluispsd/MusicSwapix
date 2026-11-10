package com.jorge.playlistconverter.matcher;

import com.jorge.playlistconverter.model.MatchResult;
import com.jorge.playlistconverter.model.Song;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.apache.commons.text.StringEscapeUtils;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Compara títulos e artistas normalizados usando Jaro-Winkler, com penalidades
 * para versões diferentes e ajustes para os metadados dos candidatos.
 * Retorna o melhor resultado conforme o limiar atual, sem chamadas às APIs nem escrita.
 * A pontuação indica semelhança; revisão e confirmação do usuário pertencem à Fase 8.
 */
@Slf4j
public class TrackMatcher {

    private static final List<String> UNWANTED_WORDS = List.of(
            "8d audio", "type beat");

    private static final Pattern NUMBERED_PART_PATTERN = Pattern.compile(
            "\\b(?:pt\\.?|part|parte)\\s*[-:]?\\s*"
                    + "(\\d{1,3}|viii|vii|iii|vi|iv|ii|ix|v|i|x)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PRESENTATION_LABEL_PATTERN = Pattern.compile(
            "[\\[(]\\s*(?:official\\s+)?"
                    + "(?:audio|music\\s+video|video|lyric\\s+video|lyrics)"
                    + "\\s*[\\])]",
            Pattern.CASE_INSENSITIVE);

    private static final List<Pattern> INCOMPATIBLE_FORMATS = List.of(
            Pattern.compile(
                    "\\b(?:dj\\s+)?set\\s+completo\\b"
                            + "|\\bfull\\s+(?:dj\\s+)?set\\b"
                            + "|\\balbum\\s+completo\\b"
                            + "|\\bfull\\s+album\\b"),
            Pattern.compile(
                    "\\b(?:react|reaction|reage|reacao)\\b"));

    private static final double MIN_TITLE_TOKEN_COVERAGE = 0.6;
    private static final double MIN_CONFIDENCE = 0.6;
    private static final double CONTAINMENT_FLOOR = 0.5;
    private static final int HASHTAG_SPAM_THRESHOLD = 2;
    private static final int MIN_DURATION_TOLERANCE_MS = 15_000;
    private static final double DURATION_TOLERANCE_RATIO = 0.10;
    private static final double DURATION_MISMATCH_FACTOR = 0.85;

    private final JaroWinklerSimilarity jaroWinkler = new JaroWinklerSimilarity();
    private final TrackVersionMatcher versionMatcher = new TrackVersionMatcher();

    public MatchResult findBestMatch(Song sourceSong, List<Song> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return new MatchResult(sourceSong, null, null, 0.0);
        }

        String rawSourceTitle = sourceSong.title().toLowerCase();
        String normalizedSourceTitle = normalizeMatchTitle(sourceSong.title());
        String normalizedSourceArtist = normalizeText(sourceSong.artist());

        Song bestMatch = null;
        double bestScore = 0.0;
        double bestRankingScore = 0.0;
        boolean bestChannelMatchesArtist = false;

        int sourcePartNumber = extractPartNumber(sourceSong.title());

        for (Song candidate : candidates) {

            if (sourcePartNumber != extractPartNumber(candidate.title())){
                log.debug(
                        "Candidato descartado por parte diferente: id={} | título={}",
                        candidate.id(),
                        candidate.title());

                continue;
            }

            if (!versionMatcher.isCompatible(sourceSong.title(), candidate.title())
                    || !hasTitleEvidence(sourceSong.title(), candidate.title())
                    || hasIncompatibleFormat(sourceSong.title(), candidate.title())){

                log.debug("Candidato descartado por título, versão ou formato incompatível: "
                                + "id={} | título={}", candidate.id(), candidate.title());
                continue;
            }

            String rawCandidateTitle = candidate.title().toLowerCase();
            String normalizedCandidateTitle = normalizeMatchTitle(candidate.title());
            String normalizedCandidateArtist = normalizeText(candidate.artist());

            boolean channelMatchesArtist = !normalizedSourceArtist.isBlank()
                    && normalizedSourceArtist.equals(normalizedCandidateArtist);

            Matcher artistCredit = Pattern.compile("^" + Pattern.quote(normalizedSourceArtist) + "\\s*[-–—:]\\s*")
                    .matcher(normalizedCandidateTitle);

            if (!normalizedSourceArtist.isBlank() && artistCredit.find()){
                normalizedCandidateArtist = normalizedSourceArtist;
                normalizedCandidateTitle = normalizedCandidateTitle.substring(artistCredit.end()).strip();
            }

            double titleSimilarity = computeTitleSimilarity(normalizedSourceTitle, normalizedCandidateTitle);
            double artistSimilarity = jaroWinkler.apply(normalizedSourceArtist, normalizedCandidateArtist);

            double score = (titleSimilarity * 0.6) + (artistSimilarity * 0.4);

            score = applyPenalty(score, rawSourceTitle, rawCandidateTitle);
            score = applySpamPenalty(score, rawCandidateTitle);
            score = applyOfficialityBonus(score, rawCandidateTitle);
            score = Math.min(score, 1.0);

            log.debug(
                    "Pontuação: id={} | título={} | artista={} | score={}",
                    candidate.id(),
                    candidate.title(),
                    candidate.artist(),
                    score);

            if (score < MIN_CONFIDENCE){
                continue;
            }

            double rankingScore = scoreForRanking(score, sourceSong, candidate);

            boolean winsOnChannelTie = rankingScore == bestRankingScore && channelMatchesArtist
                    && !bestChannelMatchesArtist;

            if (rankingScore > bestRankingScore || winsOnChannelTie){
                bestRankingScore= rankingScore;
                bestScore = score;
                bestMatch = candidate;
                bestChannelMatchesArtist = channelMatchesArtist;
            }
        }

        if (bestMatch == null) {
            return new MatchResult(sourceSong, null, null, 0.0);
        }

        return new MatchResult(sourceSong, bestMatch.id(), bestMatch.title(), bestScore);
    }

    private double scoreForRanking(double score,  Song source, Song candidate){
        int sourceDuration = source.durationMs();
        int candidateDuration = candidate.durationMs();

        if (sourceDuration <= 0 || candidateDuration <= 0){
            return score;
        }

        double tolerance = Math.max(MIN_DURATION_TOLERANCE_MS, sourceDuration * DURATION_TOLERANCE_RATIO);

        int difference = Math.abs(sourceDuration - candidateDuration);

        return difference > tolerance ? score * DURATION_MISMATCH_FACTOR : score;
    }

    private double computeTitleSimilarity(String normalizedSourceTitle, String normalizedCandidateTitle) {

        double rawSimilarity = jaroWinkler.apply(normalizedSourceTitle, normalizedCandidateTitle);

        if (!normalizedSourceTitle.isBlank() && normalizedCandidateTitle.contains(normalizedSourceTitle))
            return Math.max(rawSimilarity, CONTAINMENT_FLOOR);

        return rawSimilarity;
    }


    private String normalizeText(String text) {
        if (text == null || text.isEmpty()) {return "";}

        String normalized = normalizeCharacters(text)
                .replaceAll("#[\\w]+", "")
                .replaceAll("\\([^)]*\\)", "");

        return normalized.strip().replaceAll("\\s+", " ");
    }

    private double applyPenalty(double score, String sourceTitle, String candidateTitle) {
        for (String keyword : UNWANTED_WORDS) {
            if (candidateTitle.contains(keyword) && !sourceTitle.contains(keyword)) {
                score *= 0.5;
            }
        }
        return score;
    }

    private double applySpamPenalty(double score, String rawCandidateTitle) {

        long hashtagCount = rawCandidateTitle.chars().filter(c -> c == '#' ).count();
        if (hashtagCount > HASHTAG_SPAM_THRESHOLD) {
            score *= 0.4;
        }
        return score;
    }

    private double applyOfficialityBonus(double score, String rawCandidateTitle) {

        return rawCandidateTitle.contains("official") ? score * 1.15 : score;
    }

    private int extractPartNumber(String title) {
        var partMatcher = NUMBERED_PART_PATTERN.matcher(title);

        if (!partMatcher.find()) {
            return 0;
        }

        String number = partMatcher.group(1).toUpperCase(Locale.ROOT);

        return switch (number) {
            case "I" -> 1;
            case "II" -> 2;
            case "III" -> 3;
            case "IV" -> 4;
            case "V" -> 5;
            case "VI" -> 6;
            case "VII" -> 7;
            case "VIII" -> 8;
            case "IX" -> 9;
            case "X" -> 10;
            default -> Integer.parseInt(number);
        };
    }

    public MatchResult findExistingMatch(Song sourceSong, List<Song> existingSongs){
        String sourceArtist = normalizeExistingArtist(sourceSong.artist());
        String sourceTitle = normalizeExistingTitle(sourceSong);

        if (sourceArtist.isBlank() || sourceTitle.isBlank()) {
            return new MatchResult(sourceSong, null, null, 0.0);
        }

        List<Song> compatibleSongs = existingSongs.stream()
                .filter(song -> sourceArtist.equals(normalizeExistingArtist(song.artist())))
                .filter(song -> sourceTitle.equals(normalizeExistingTitle(song)))
                .toList();

        return findBestMatch(sourceSong, compatibleSongs);
    }

    private String normalizeExistingArtist(String artist){
        return normalizeText(artist)
                .replaceFirst("\\s*-\\s*topic$", "")
                .strip();
    }

    private String normalizeExistingTitle(Song song){
        String title = normalizeCharacters(song.title());

        title = PRESENTATION_LABEL_PATTERN.matcher(title).replaceAll(" ");

        String artist = normalizeExistingArtist(song.artist());

        title = title.replaceFirst("^" + Pattern.quote(artist) + "\\s*[-:]\\s*",
                "");

        return title.strip().replaceAll("\\s+", " ");
    }

    private String normalizeCharacters(String text){
        return Normalizer.normalize(StringEscapeUtils.unescapeHtml4(text),
        Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private Set<String> titleTokens(String title){
        String normalized = NUMBERED_PART_PATTERN.matcher(normalizeMatchTitle(title))
                .replaceAll(" ").replaceAll("[^\\p{L}\\p{N}]+", " ");

        return Arrays.stream(normalized.strip().split("\\s+"))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toSet());
    }

    private boolean hasTitleEvidence(String sourceTitle, String candidateTitle){
        Set<String> sourceTokens = titleTokens(sourceTitle);
        Set<String> candidateTokens = titleTokens(candidateTitle);

        long sharedTokens = sourceTokens.stream().filter(candidateTokens::contains)
                .count();

        return !sourceTokens.isEmpty() &&
                (double) sharedTokens / sourceTokens.size()
                >= MIN_TITLE_TOKEN_COVERAGE;
    }

    private boolean hasIncompatibleFormat(String sourceTitle, String candidateTitle){
        String source = normalizeCharacters(sourceTitle);
        String candidate = normalizeCharacters(candidateTitle);

        return INCOMPATIBLE_FORMATS.stream().anyMatch(pattern ->
                pattern.matcher(candidate).find() && !pattern.matcher(source).find());
    }

    private String normalizeMatchTitle(String title){
        return normalizeText(versionMatcher.removeVersionLabels(title))
                .replaceAll("\\s*[-–—:]\\s*$", "").strip();
    }
}
