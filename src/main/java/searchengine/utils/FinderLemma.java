package searchengine.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.LuceneMorphology;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tartarus.snowball.SnowballProgram;
import org.tartarus.snowball.ext.RussianStemmer;
import org.tartarus.snowball.ext.EnglishStemmer;
import searchengine.model.IndexModel;
import searchengine.model.LemmaModel;
import searchengine.model.PageModel;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
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
    private final RussianStemmer russianStemmer;
    private final EnglishStemmer englishStemmer;
    public static final String[] PARTICLES_NAMES = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ", "ЧАСТ", "МС",
            "ARTICLE", "CONJ", "PREP"};
    private static final int START_FREQUENCY = 1;
    public static final String REGEX_RU = "\\b[А-Яа-яЁё]+\\b";
    public static final String REGEX_ENG = "\\b[A-Za-z]+\\b";


    @Transactional
    public void processLemma(PageModel pageModel) {

        List<String> targetWordsRu = extractWordsFromContent(pageModel.getContent(), REGEX_RU);
        List<String> targetWordsEng = extractWordsFromContent(pageModel.getContent(), REGEX_ENG);
        Map<String, Integer> lemmaCountMap = mapLemmaAndCounts(targetWordsRu, luceneMorphologyRu, () -> russianStemmer);
        lemmaCountMap.putAll(mapLemmaAndCounts(targetWordsEng, luceneMorphologyEng, () -> englishStemmer));

        lemmaCountMap.forEach((lemma, rank) -> processLemmaEntry(lemma, rank, pageModel));
        log.info("Произведена запись лемм в количестве: {}", lemmaCountMap.size());

    }

    @Transactional
    public Map<String, Integer> mapLemmaAndCounts(List<String> words, LuceneMorphology luceneMorphology, Supplier<SnowballProgram> stemmer) {
        return words.stream()
                .filter(word -> !word.isBlank())
                .map(String::toLowerCase)
                .flatMap(word -> safeGetMorphInfo(luceneMorphology, word))
                .filter(morphInfo -> Arrays.stream(PARTICLES_NAMES).noneMatch(morphInfo::contains))
                .map(morphInfo -> morphInfo.split("\\|")[0])
                .filter(particle -> !particle.isBlank())
                .filter(word -> safeCheckString(luceneMorphology, word))
                .flatMap(normalForm -> safeGetNormalForms(luceneMorphology, normalForm, stemmer))
                .peek(lemma -> log.info("Изъяты леммы: {}", lemma))
                .collect(Collectors.toConcurrentMap(
                        lemma -> lemma,
                        lemma -> 1,
                        Integer::sum
                ));
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
        }
        log.info("Количество изъятых слов: {}", targetWords.size());
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
        log.info("Леммы в базе данных сохранены");
        saveIndexModel(pageModel, lemmaModel, count);
    }

    private void saveIndexModel(PageModel pageModel, LemmaModel lemmaModel, float count) {
        IndexModel indexModel = new IndexModel();
        indexModel.setPage(pageModel);
        indexModel.setLemma(lemmaModel);
        indexModel.setRank(count);
        indexRepository.save(indexModel);
        log.info("Индексы в базе данных сохранены");
    }

    private Stream<String> safeGetMorphInfo(LuceneMorphology morphology, String word) {
        try {
            return morphology.getMorphInfo(word).stream();
        } catch (Exception e) {
            log.warn("Слово '{}' не может быть обработано морфологией: {}", word, e.getMessage());
            return Stream.empty();
        }
    }

    private boolean safeCheckString(LuceneMorphology morphology, String word) {
        try {
            return morphology.checkString(word);
        } catch (Exception e) {
            log.warn("Слово '{}' не прошло проверку checkString: {}", word, e.getMessage());
            return false;
        }
    }

    private Stream<String> safeGetNormalForms(LuceneMorphology morphology, String word, Supplier<SnowballProgram> stemmer) {
        try {
            return morphology.getNormalForms(word).stream()
                    .map(lemma -> safeStem(lemma, stemmer));
        } catch (Exception e) {
            log.warn("Ошибка нормализации лемм '{}': {}", word, e.getMessage());
            return Stream.empty();
        }
    }

    private String safeStem(String lemma, Supplier<SnowballProgram> stemmer) {
        try {
            SnowballProgram stem = stemmer.get();
            stem.setCurrent(lemma);
            stem.stem();
            return stem.getCurrent();
        } catch (Exception e) {
            log.warn("Ошибка стемминга слова '{}': {}", lemma, e.getMessage());
            return lemma;
        }
    }
}

