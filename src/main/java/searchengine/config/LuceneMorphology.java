package searchengine.config;

import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.tartarus.snowball.ext.EnglishStemmer;
import org.tartarus.snowball.ext.RussianStemmer;

import java.io.IOException;

@Configuration
public class LuceneMorphology {

    @Bean
    public RussianLuceneMorphology luceneMorphologyRu() throws IOException {
        return new RussianLuceneMorphology();
    }

    @Bean
    EnglishLuceneMorphology luceneMorphologyEng() throws IOException {
        return new  EnglishLuceneMorphology();
    }

    @Bean
    RussianStemmer russianStemmer() {
        return new RussianStemmer();
    }

    @Bean
    EnglishStemmer englishStemmer() {
        return new EnglishStemmer();
    }
}
