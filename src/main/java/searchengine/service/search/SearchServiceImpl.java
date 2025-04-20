package searchengine.service.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.morphology.LuceneMorphology;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tartarus.snowball.ext.EnglishStemmer;
import org.tartarus.snowball.ext.RussianStemmer;
import searchengine.dto.search.Match;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.search.SearchResult;
import searchengine.exceptions.IndexNotReadyException;
import searchengine.model.LemmaModel;
import searchengine.model.PageModel;
import searchengine.model.SiteModel;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.utils.FinderLemma;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


@Slf4j
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Service
public class SearchServiceImpl implements SearchService<SearchResponse> {
    private final FinderLemma finderLemmaService;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LuceneMorphology luceneMorphologyRu;
    private final LuceneMorphology luceneMorphologyEng;
    private final RussianStemmer russianStemmer;
    private final EnglishStemmer englishStemmer;
    private static final double FREQUENCY_THRESHOLD = 0.8;

    @Transactional
    @Override
    public SearchResponse search(String query, String site, int offset, int limit) throws IndexNotReadyException {
        List<SiteModel> sitesList = siteRepository.findAll();
        List<SearchResult> allResults = new ArrayList<>(searchResults(query, site));

        if (query.isBlank()) {
            throw new IllegalArgumentException("Задан пустой поисковый запрос");
        }

        if (sitesList.isEmpty()) {
            throw new IndexNotReadyException("Сайты для поиска отсутствуют");
        }

        if (site == null) {
            for (SiteModel siteModel : sitesList) {
                List<SearchResult> siteResults = searchResults(query, siteModel.getUrl());
                allResults.addAll(siteResults);
            }
        }

        List<SearchResult> paginatedResults = allResults
                .stream()
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());
        return new SearchResponse(true, allResults.size(), paginatedResults);
    }

    @Transactional
    public List<SearchResult> searchResults(String query, String site) {
        Set<String> lemmasSet = extractAndProcessLemmas(query);

        long totalPages = pageRepository.count();
        SiteModel siteModel = siteRepository.findByUrl(site);

        if (siteModel == null) {
            return Collections.emptyList();
        }
        Map<String, Float> filteredLemmasByFrequency = getFilteredLemmas(lemmasSet, totalPages, siteModel);
        Set<String> sortedLemmasByFrequency = sortLemmasByFrequency(filteredLemmasByFrequency);

        List<PageModel> pages = findPagesByLemmas(sortedLemmasByFrequency);

        if (pages.isEmpty()) {
            return Collections.emptyList();
        }

        List<SearchResult> searchResults = new ArrayList<>(processLemmas(pages, query, sortedLemmasByFrequency));

        if (site.isEmpty()) {
            return searchResults;
        }

        return searchResults.stream()
                .filter(searchResult -> searchResult.getSite().equals(site))
                .toList();
    }

    private Set<String> extractAndProcessLemmas(String query) {
        List<String> targetWordsRu = finderLemmaService.extractWordsFromContent(query, FinderLemma.REGEX_RU);
        List<String> targetWordsEng = finderLemmaService.extractWordsFromContent(query, FinderLemma.REGEX_ENG);
        Set<String> lemmasSet = new ConcurrentSkipListSet<>(finderLemmaService.mapLemmaAndCounts(targetWordsRu, luceneMorphologyRu, () -> russianStemmer).keySet());
        lemmasSet.addAll(new ConcurrentSkipListSet<>(finderLemmaService.mapLemmaAndCounts(targetWordsEng, luceneMorphologyEng, () -> englishStemmer).keySet()));
        return lemmasSet;
    }

    @Transactional
    public Map<String, Float> getFilteredLemmas(Set<String> lemmasSet, long totalPages, SiteModel siteModel) {
        Map<String, Float> filteredLemmasByFrequency = new ConcurrentHashMap<>();
        lemmasSet.forEach(lemmaSet -> {
            LemmaModel lemmaModel = lemmaRepository.findByLemmaAndSite(lemmaSet, siteModel);
            if (lemmaModel != null) {
                long lemmaFrequency = lemmaRepository.countByLemma(lemmaSet);
                float frequency = (float) lemmaFrequency / totalPages;
                if (frequency < FREQUENCY_THRESHOLD) {
                    filteredLemmasByFrequency.put(lemmaModel.getLemma(), frequency);
                }
            }
        });
        return filteredLemmasByFrequency.isEmpty() ? Collections.emptyMap() : filteredLemmasByFrequency;
    }

    private Set<String> sortLemmasByFrequency(Map<String, Float> lemmaByFrequency) {
        return lemmaByFrequency.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Transactional
    public List<PageModel> findPagesByLemmas(Set<String> sortedLemmasByFrequency) {
        if (sortedLemmasByFrequency.isEmpty()) {
            return Collections.emptyList();
        }
        String firstLemma = sortedLemmasByFrequency.iterator().next();
        return indexRepository.findPagesByLemma(firstLemma);
    }

    @Transactional
    public List<SearchResult> processLemmas(List<PageModel> initialPages, String query, Set<String> sortedLemmasByFrequency) {
        Map<PageModel, Float> relevanceMap = calculateRelevance(initialPages);
        return initialPages.stream()
                .map(page ->
                        createSearchResult(page, relevanceMap.getOrDefault(page, 0.0f), query, sortedLemmasByFrequency))
                .filter(result -> !result.getSnippet().isEmpty() && !result.getTitle().isEmpty())
                .sorted(Comparator.comparingDouble(SearchResult::getRelevance).reversed())
                .collect(Collectors.toList());
    }

    private SearchResult createSearchResult(PageModel page, float relevance, String query, Set<String> sortedLemmasByFrequency) {
        String snippet = generateSnippet(page.getContent(), query, sortedLemmasByFrequency);
        String title = extractTitleFromHtml(page.getContent());
        return new SearchResult(page.getSite().getUrl(), page.getSite().getName(), page.getPath(),
                title, snippet, relevance);
    }

    @Transactional(readOnly = true)
    public Map<PageModel, Float> calculateRelevance(List<PageModel> pages) {
        if (pages.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Integer> pageIds = pages.stream()
                .map(PageModel::getId)
                .toList();
        Map<Integer, Float> ranksByPageId = indexRepository.sumRankByPageIds(pageIds).stream()
                .collect(Collectors.toMap(
                        tuple -> ((Number) ((Object[]) tuple)[0]).intValue(),
                        tuple -> ((Number) ((Object[]) tuple)[1]).floatValue(),
                        (existing, replacement) -> existing, HashMap::new
                ));
        return pages.stream()
                .collect(
                        Collectors.collectingAndThen(
                                Collectors.toMap(
                                        Function.identity(),
                                        page -> ranksByPageId.getOrDefault(page.getId(), 0.0F)
                                ),
                                map -> {
                                    float max = map.values().stream()
                                            .max(Float::compare)
                                            .orElse(1.0F);

                                    if (max > 0) {
                                        map.replaceAll((k, v) -> v / max);
                                    }
                                    return map;
                                }
                        )
                );
    }

    private static String extractTitleFromHtml(String htmlContent) {
        if (htmlContent == null || htmlContent.isEmpty()) {
            return "";
        }

        Pattern pattern = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(htmlContent);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    public String generateSnippet(String content, String query, Set<String> sortLemmas) {
        String plainText = prepareText(content);
        return buildSnippet(plainText, query, sortLemmas);
    }

    private String buildSnippet(String text, String query, Set<String> sortLemmas) {

        if (StringUtils.isBlank(query) || StringUtils.isBlank(text)) {
            return "";
        }

        List<String> pageWords = Arrays.asList(text.split("[\\s,.!?;:]"));
        List<String> queryWords = Arrays.asList(query.split("[\\s,.!?;:]"));
        List<String> matchingWords = findAllMatchingWords(sortLemmas, pageWords, queryWords);
        String matchingWord = findMatchingWord(matchingWords, pageWords);
        log.info("Найденное слова: {}", matchingWord);

        if (matchingWord.isEmpty()) {
            return "";
        }

        Pattern pattern = Pattern.compile("\\b" + matchingWord + "\\b");
        Matcher matcher = pattern.matcher(text);

        if (!matcher.find()) {
            return "";
        }

        int queryStart = matcher.start();
        int queryEnd = matcher.end();

        int snippetLength = 150;
        int snippetStart = Math.max(0, queryStart - snippetLength);
        int snippetEnd = Math.min(text.length(), queryEnd + snippetLength);

        while (snippetStart > 0 && !Character.isWhitespace(text.charAt(snippetStart - 1))) {
            snippetStart--;
        }

        while (snippetEnd < text.length() && !Character.isWhitespace(text.charAt(snippetEnd))) {
            snippetEnd++;
        }

        String snippetRaw = text.substring(snippetStart, snippetEnd);
        log.info("Найденный отрывок: {}", snippetRaw);

        StringBuilder snippet = new StringBuilder();

        if (snippetStart > 0) {
            snippet.append("...");
        }

        List<Match> allMatches = new ArrayList<>();
        Set<String> allMatchingWords = new HashSet<>(matchingWords);
        allMatchingWords.addAll(queryWords);

        allMatchingWords.forEach(word -> {
            Matcher wordMatcher = Pattern.compile("\\b" + word + "\\b")
                    .matcher(snippetRaw);
            while (wordMatcher.find()) {
                allMatches.add(new Match(wordMatcher.start(), wordMatcher.end()));
            }
        });

        allMatches.sort(Comparator.comparingInt(Match::start));
        snippet = highlightingSnippet(allMatches, snippetRaw, snippet);

        if (snippetEnd < text.length()) {
            snippet.append("...");
        }

        return snippet.toString();

    }

    private StringBuilder highlightingSnippet(List<Match> allMatches, String snippetRaw, StringBuilder snippet) {
        AtomicInteger lastPos = new AtomicInteger(0);
        allMatches.forEach(match -> {
            snippet.append(snippetRaw, lastPos.get(), match.start());
            snippet.append("<b>")
                    .append(snippetRaw, match.start(), match.end())
                    .append("</b>");
            lastPos.set(match.end());
        });

        return snippet.append(snippetRaw.substring(lastPos.get()));
    }

    private List<String> findAllMatchingWords(Set<String> sortedLemmas, List<String> pageWords, List<String> queryWords) {
        queryWords.forEach(word -> {
            if (!extractAndProcessLemmas(word).isEmpty() && word.length() > 3) {
                sortedLemmas.add(word);
            }
        });

        return pageWords.stream()
                .filter(word -> sortedLemmas.stream()
                        .anyMatch(lemma -> word.equalsIgnoreCase(lemma) || word.toLowerCase().contains(lemma.toLowerCase())))
                .distinct()
                .collect(Collectors.toList());

    }

    private String findMatchingWord(List<String> matchingWords, List<String> pageWords) {
        return pageWords.stream()
                .filter(pageWord -> matchingWords.stream()
                        .anyMatch(matchingWord -> pageWord.toLowerCase().startsWith(matchingWord.toLowerCase())))
                .findFirst()
                .orElse("");
    }


    private String prepareText(String content) {
        if (StringUtils.isBlank(content)) {
            return "";
        }

        return content.replaceAll("<[^>]+>", " ")
                .replaceAll("\\{.*?\\}", " ")
                .replaceAll("\\w+\\(.*?\\)", " ")
                .replaceAll("[^А-Яа-яЁёA-Za-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}



