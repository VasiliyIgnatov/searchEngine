package searchengine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.morphology.LuceneMorphology;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.concurrent.CopyOnWriteArrayList;
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
    private static final double FREQUENCY_THRESHOLD = 0.8;

    @Transactional
    @Override
    public SearchResponse search(String query, String site, int offset, int limit) throws IndexNotReadyException {
        List<SiteModel> sitesList = siteRepository.findAll();
        List<SearchResult> allResults = new CopyOnWriteArrayList<>(searchResults(query, site));

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


        List<String> targetWordsRu = finderLemmaService.extractWordsFromContent(query, FinderLemma.REGEX_RU);
        List<String> targetWordsEng = finderLemmaService.extractWordsFromContent(query, FinderLemma.REGEX_ENG);
        Set<String> lemmasSet = new ConcurrentSkipListSet<>(finderLemmaService.mapLemmaAndCounts(targetWordsRu, luceneMorphologyRu).keySet());
        lemmasSet.addAll(new ConcurrentSkipListSet<>(finderLemmaService.mapLemmaAndCounts(targetWordsEng, luceneMorphologyEng).keySet()));

        long totalPages = pageRepository.count();
        SiteModel siteModel = siteRepository.findByUrl(site);

        if (siteModel == null) {
            return Collections.emptyList();
        }
        Map<String, Float> filteredLemmasByFrequency = getFilteredLemmas(lemmasSet, totalPages, siteModel);
        Set<String> sortedLemmasByFrequency = sortLemmasByFrequency(filteredLemmasByFrequency);

        List<PageModel> pages = findPagesByLemmasAndSite(sortedLemmasByFrequency, site);

        if (pages.isEmpty()) {
            return Collections.emptyList();
        }

        return processLemmas(sortedLemmasByFrequency, pages, site);

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
        Set<String> sortedLemmasByFrequency = new ConcurrentSkipListSet<>(Comparator.comparing(lemmaByFrequency::get));
        sortedLemmasByFrequency.addAll(lemmaByFrequency.keySet());
        return sortedLemmasByFrequency;
    }

    @Transactional
    public List<PageModel> findPagesByLemmasAndSite(Set<String> sortedLemmasByFrequency, String site) {
        if (sortedLemmasByFrequency.isEmpty()) {
            return Collections.emptyList();
        }
        String firstLemma = sortedLemmasByFrequency.iterator().next();
        return indexRepository.findPagesByLemmaAndSite(firstLemma, site);
    }

    @Transactional
    public List<PageModel> filteredInitialPagesByLemmas(List<PageModel> initialPages, String lemma, String site) {
        return initialPages.stream()
                .filter(page -> indexRepository.existsByPageAndLemma(page, lemma, site))
                .collect(Collectors.toList());
    }

    @Transactional
    public List<SearchResult> processLemmas(Set<String> sortedLemmasByFrequency, List<PageModel> initialPages, String site) {
        List<SearchResult> searchResults = new CopyOnWriteArrayList<>();
        List<PageModel> currentPages = new CopyOnWriteArrayList<>(initialPages);

        Map<PageModel, Float> relevanceMap = calculateRelevance(currentPages);

        for (String sortedLemma : sortedLemmasByFrequency) {
            currentPages = filteredInitialPagesByLemmas(currentPages, sortedLemma, site);

            if (currentPages.isEmpty()) {
                break;
            }

            for (PageModel page : currentPages) {
                float relevance = relevanceMap.get(page);
                SearchResult searchResult = createSearchResult(page, sortedLemma, relevance);
                if (!searchResult.getSnippet().isEmpty()) {
                    searchResults.add(searchResult);
                }
            }
        }

        return searchResults;
    }

    private SearchResult createSearchResult(PageModel page, String lemma, float relevance) {
        String snippet = getSnippet(page.getContent(), lemma);
        String title = extractTitleFromHtml(page.getContent());

        return new SearchResult(page.getSite().getUrl(), page.getSite().getName(), page.getPath(),
                title, snippet, relevance);
    }

    private String getSnippet(String content, String query) {
        String plainText = prepareText(content);

        String snippet = buildSnippet(plainText, query);
        log.info(snippet);

        return snippet;
    }

    private String prepareText(String content) {
        if (StringUtils.isBlank(content)) {
            return "";
        }

        return content.replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String buildSnippet(String plainText, String query) {
        if (StringUtils.isBlank(query) || StringUtils.isBlank(plainText)) {
            return "";
        }

        Pattern pattern = Pattern.compile("(?i)\\b" + Pattern.quote(query) + "\\b");
        Matcher matcher = pattern.matcher(plainText);

        if (!matcher.find()) {
            return "";
        }

        int queryStart = matcher.start();
        int queryEnd = matcher.end();

        int snippetLength = 100;
        int snippetStart = Math.max(0, queryStart - snippetLength);
        int snippetEnd = Math.min(plainText.length(), queryEnd + snippetLength);

        while (snippetStart > 0 && !Character.isWhitespace(plainText.charAt(snippetStart - 1))) {
            snippetStart--;
        }

        while (snippetEnd < plainText.length() && !Character.isWhitespace(plainText.charAt(snippetEnd))) {
            snippetEnd++;
        }

        StringBuilder snippetBuilder = new StringBuilder();

        if (snippetStart > 0) {
            snippetBuilder.append("...");
        }

        snippetBuilder.append(plainText, snippetStart, queryStart);
        snippetBuilder.append("<b>").append(plainText, queryStart, queryEnd).append("</b>");
        snippetBuilder.append(plainText, queryEnd, snippetEnd);

        if (snippetEnd < plainText.length()) {
            snippetBuilder.append("...");
        }

        return snippetBuilder.toString()
                .replaceAll("\\s+", " ")
                .trim();
    }

    @Transactional(readOnly = true)
    public Map<PageModel, Float> calculateRelevance(List<PageModel> pages) {
        Map<PageModel, Float> relevanceMap = pages.stream()
                .collect(Collectors.toMap(
                        page -> page,
                        indexRepository::sumRankByPage
                ));

        float maxRelevance = relevanceMap.values().stream()
                .max(Float::compareTo)
                .orElse(1.0F);

        relevanceMap.replaceAll((page, relevance) -> relevance / maxRelevance);

        return relevanceMap;
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
        return "Заголовок для данной страницы отсутствует";
    }
}
