package searchengine.utils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.dao.DataIntegrityViolationException;
import searchengine.model.PageModel;
import searchengine.model.SiteModel;
import searchengine.model.Status;
import searchengine.repositories.PageRepository;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicBoolean;


@RequiredArgsConstructor
@Getter
@Setter
@Slf4j
public class PageIndexingTask extends RecursiveAction {
    private final AtomicBoolean stopIndexing;
    private final String url;
    private final PageRepository pageRepository;
    private final SiteModel siteModel;
    private final Set<String> setUrls = ConcurrentHashMap.newKeySet();
    private final PageManager pageManager;
    private final String userAgent;
    private final String referrer;
    private final FinderLemma finderLemmaService;


    @Override
    protected void compute() {
        if (!stopIndexing.get()) {
            log.info("Индексация остановлена для url: {}", url);
            pageManager.updateSiteStatus(siteModel, Status.FAILED, "Индексация остановлена пользователем");
            return;
        }
        try {
            Connection connection = Jsoup.connect(url).userAgent(userAgent).referrer(referrer);
            Connection.Response response = connection.execute();
            String contentType = response.contentType();
            log.info("Тип контента для url: {} {}", url, contentType);

            if (contentType == null || !contentType.startsWith("text/")) {
                log.warn("Неподдерживаемый тип контента для url: {} {}", url, contentType);
                return;
            }
            parseTasks(response);

        } catch (ConstraintViolationException | DataIntegrityViolationException e) {
            log.warn("Ошибка при сохранении страницы. Дубликат страницы: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Ошибка при обработке URL: {}. Сообщение: {}", url, e.getMessage());
            pageManager.updateSiteStatus(siteModel, Status.FAILED, "Ошибка при обработке URL");
        }
    }

    public boolean isCheckUniquePage(Set<String> urlSet, String url) {
        return urlSet.isEmpty() || urlSet.stream()
                .anyMatch(s -> s.equals(url));
    }

    private void sleep() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void parseTasks(Connection.Response response) throws IOException {
        Document document = response.parse();
        int code = document.connection().response().statusCode();
        String content = document.html();
        sleep();

        if (!isCheckUniquePage(setUrls, url)) {
            log.error("Дубликат страницы: {}", url);
        } else {
            setUrls.add(url);
            PageModel pageModel = pageManager.createPageModel(code, content, url, siteModel);
            log.info("Произведена запись с данным url: {}", url);
            finderLemmaService.processLemma(pageModel);


            Elements elements = document.select("a[href]");
            List<PageIndexingTask> tasks = new CopyOnWriteArrayList<>();
            for (Element element : elements) {
                String absUrl = element.absUrl("href");
                if (absUrl.startsWith(siteModel.getUrl()) && !pageRepository.existsByPath(absUrl)) {

                    tasks.add(new PageIndexingTask(stopIndexing, absUrl, pageRepository,
                            siteModel, pageManager, userAgent, referrer, finderLemmaService));
                    log.info("Добавлен новый Task для URL: {}", absUrl);
                }
            }
            invokeAll(tasks);
        }
    }
}

