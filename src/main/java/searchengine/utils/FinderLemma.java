package searchengine.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.LuceneMorphology;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.IndexModel;
import searchengine.model.LemmaModel;
import searchengine.model.PageModel;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class FinderLemma {
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final LuceneMorphology luceneMorphologyRu;
    private final LuceneMorphology luceneMorphologyEng;
    public static final String[] PARTICLES_NAMES = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ", "ЧАСТ", "МС",
            "ARTICLE", "CONJ", "PREP"};
    private static final int START_FREQUENCY = 1;
    public static final String REGEX_RU = "\\b[А-Яа-яЁё]+\\b";
    public static final String REGEX_ENG = "\\b[A-Za-z]+\\b";


    @Transactional
    public void processLemma(PageModel pageModel) {

        List<String> targetWordsRu = extractWordsFromContent(pageModel.getContent(), REGEX_RU);
        List<String> targetWordsEng = extractWordsFromContent(pageModel.getContent(), REGEX_ENG);
        Map<String, Integer> lemmaCountMap = mapLemmaAndCounts(targetWordsRu, luceneMorphologyRu);
        lemmaCountMap.putAll(mapLemmaAndCounts(targetWordsEng, luceneMorphologyEng));

        lemmaCountMap.forEach((lemma, rank) -> {
            processLemmaEntry(lemma, rank, pageModel);
            log.info("Произведена запись Lemma: {}, site: {}", lemma, pageModel.getSite().getId());
        });

    }

    @Transactional
    public Map<String, Integer> mapLemmaAndCounts(List<String> words, LuceneMorphology luceneMorphology) {
        Map<String, Integer> lemmaCountMap = new ConcurrentHashMap<>();

        List<String> filteredWords = words.stream()
                .filter(word -> !word.isBlank())
                .map(String::toLowerCase)
                .flatMap(word -> luceneMorphology.getMorphInfo(word).stream())
                .filter(morphInfo -> Arrays.stream(PARTICLES_NAMES).noneMatch(morphInfo::contains))
                .flatMap(morphInfo -> {
                    if (morphInfo.isBlank()) {
                        return Stream.empty();
                    }
                    List<String> clearForms = new CopyOnWriteArrayList<>();
                    clearForms.add(morphInfo.split("\\|")[0]);
                    return clearForms.stream();
                })
                .filter(luceneMorphology::checkString)
                .peek(normalForm -> log.debug("Слово: {}, Статус: {}", normalForm, true))
                .flatMap(normalForm -> {
                    try {
                        List<String> normalForms = luceneMorphology.getNormalForms(normalForm);
                        return normalForms != null ? normalForms.stream() : Stream.empty();
                    } catch (Exception e) {
                        log.warn("Ошибка при получении нормальных форм для '{}': {}", normalForm, e.getMessage());
                        return Stream.empty();
                    }
                })
                .collect(Collectors.toList());

        log.info("Леммы из слов изъяты: {}", filteredWords);

        filteredWords.forEach(lemma -> lemmaCountMap.merge(lemma, 1, Integer::sum));
        return lemmaCountMap;
    }

    public List<String> extractWordsFromContent(String content, String regex) {
        List<String> targetWords = new CopyOnWriteArrayList<>();
        if (content.isEmpty()) {
            log.warn("Слова отсутствуют: {}", content);
            return new CopyOnWriteArrayList<>() {
            };
        }
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            targetWords.add(matcher.group());
            log.info("Слова изъяты: {}", matcher.group());
        }
        return targetWords;
    }

    private void processLemmaEntry(String lemma, int count, PageModel pageModel) {
        Optional<LemmaModel> existingLemmaOpt = lemmaRepository.findByLemmaAndSiteId(lemma, pageModel.getSite().getId());
        LemmaModel lemmaModel;

        if (existingLemmaOpt.isPresent()) {
            lemmaModel = existingLemmaOpt.get();
            lemmaModel.setFrequency(lemmaModel.getFrequency() + 1);
        } else {
            lemmaModel = new LemmaModel();
            lemmaModel.setLemma(lemma);
            lemmaModel.setFrequency(START_FREQUENCY);
            lemmaModel.setSite(pageModel.getSite());
        }
        lemmaRepository.save(lemmaModel);
        log.info("Леммы в базе данных сохранены: {}", lemmaModel.getLemma());
        saveIndexModel(pageModel, lemmaModel, count);
    }

    private void saveIndexModel(PageModel pageModel, LemmaModel lemmaModel, float count) {
        IndexModel indexModel = new IndexModel();
        indexModel.setPage(pageModel);
        indexModel.setLemma(lemmaModel);
        indexModel.setRank(count);
        indexRepository.save(indexModel);
        log.info("Индексы в базе данных сохранены: {}", indexModel.getRank());
    }
}

