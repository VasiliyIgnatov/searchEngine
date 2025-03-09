package searchengine.config;

import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class LuceneMorphology {

    @Bean
    public org.apache.lucene.morphology.LuceneMorphology luceneMorphologyRu() throws IOException {
        return new RussianLuceneMorphology();
    }

    @Bean
    org.apache.lucene.morphology.LuceneMorphology luceneMorphologyEng() throws IOException {
        return new EnglishLuceneMorphology();
    }
}
