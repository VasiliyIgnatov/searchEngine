package searchengine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.indexing.UrlPage;
import searchengine.exceptions.IndexingException;
import searchengine.exceptions.StartIndexingException;
import searchengine.exceptions.StopIndexingException;
import searchengine.model.*;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;


@Service
@Slf4j
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class IndexingServiceImpl implements IndexingService<IndexingResponse> {

    private final SitesList sitesList;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final SiteDataService siteDataService;
    private final AtomicBoolean isIndexing = new AtomicBoolean(false);
    private final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() / 4);
    private final IndexingPagesService pageService;
    private final FinderLemmaService finderLemmaService;
    private final String userAgent = "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:92.0) Gecko/20100101 Firefox/92.0";
    private final String referrer = "https://www.google.com";
    private final ConcurrentLinkedQueue<UrlPage> pageQueue = new ConcurrentLinkedQueue<>();

    @Transactional
    @Override
    public IndexingResponse startIndexing() {
        log.info("StartIndexing = {}", isIndexing);
        if (isIndexing.get()) {
            throw new StartIndexingException("Индексация уже запущена");
        }
        isIndexing.set(true);
        log.info("StartIndexing = {}", isIndexing);
        List<Site> sites = sitesList.getSites();
        sites.forEach(site -> executorService.submit(() -> indexSite(site)));

        return createSuccessResponse();
    }

    @Transactional
    @Override
    public IndexingResponse stopIndexing() {
        if (isIndexing.get()) {
            isIndexing.set(false);
            return createSuccessResponse();
        }
        throw new StopIndexingException("Индексация не запущена");
    }

    @Transactional
    @Override
    public IndexingResponse indexPage(UrlPage url) {
        String error = "Данная страница находится за пределами сайтов" +
                " указанных в конфигурационном файле";

        Optional<Site> matchingSite = sitesList.getSites().stream()
                .filter(site -> url.url().startsWith(site.getUrl()))
                .findFirst();

        if (matchingSite.isPresent()) {
            Site site = matchingSite.get();
            try {
                URL linkUrl = new URL(url.url());
                String path = linkUrl.getPath();

                if (site.getUrl().equals(url.url())) {
                    log.error("Передан сайт {}, а не страница: ", site.getUrl());
                    throw new IndexingException(error);
                }

                SiteModel siteModel = siteRepository.findByUrl(site.getUrl());
                PageModel pageModel = pageRepository.findByPathAndSite(path, siteModel);

                if (siteModel == null) {
                    siteModel = new SiteModel();
                    siteDataService.saveSiteModel(site, siteModel);
                }
                return handlePageQueue(url, siteModel, pageModel, path);

            } catch (IOException e) {
                log.info("Ошибка индексации страницы: {}", e.getMessage());
                throw new IndexingException(error);
            }
        }
        log.error("Page not found = {}", url);
        throw new IndexingException(error);
    }

    @Transactional
    public void indexSite(Site site) {
        try {
            if (!isIndexing.get()) {
                log.info("IndexSite isIndexing value: {}", isIndexing.get());
                return;
            }
            SiteModel siteModel = siteRepository.findByUrl(site.getUrl());
            if (siteModel != null) {
                siteDataService.deleteData(siteModel);
            }

            siteModel = new SiteModel();
            siteDataService.saveSiteModel(site, siteModel);

            pagesIndexing(siteModel, site.getUrl());

            if (isIndexing.get()) {
                siteModel.setStatus(Status.INDEXED);
            } else {
                siteModel.setStatus(Status.FAILED);
                siteModel.setLastError("Индексация остановлена пользователем");
            }
            siteRepository.save(siteModel);
        } catch (Exception e) {
            SiteModel siteModel = siteRepository.findByUrl(site.getUrl());
            log.info("siteException = {}", siteModel);
            if (siteModel != null) {
                siteModel.setStatus(Status.FAILED);
                siteModel.setLastError("Ошибка: " + e.getMessage());
                log.error("Error: = {}", e.getMessage());
                siteRepository.save(siteModel);
            } else {
                System.out.println("Сайт не найден по URL = " + site.getUrl());
                log.error("Site not found URL = {}", e.getMessage());
            }
        }
    }

    @Transactional
    public void pagesIndexing(SiteModel siteModel, String url) {
        if (!isIndexing.get())
            return;
        ForkJoinPool pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors() / 4);
        try {
            pool.invoke(new IndexingPages(isIndexing, url, pageRepository,
                    siteModel, pageService, userAgent, referrer, finderLemmaService));
        } finally {
            pool.shutdown();
        }
    }

    @Transactional
    public void processIndexPage(SiteModel siteModel, String url, String userAgent, String referrer) throws IOException {
        Document doc = Jsoup.connect(url).userAgent(userAgent).referrer(referrer).get();
        int code = doc.connection().response().statusCode();
        String content = doc.html();
        PageModel pageModel = pageService.createPageModel(code, content, url, siteModel);
        finderLemmaService.processLemma(pageModel);
    }

    @Transactional
    public IndexingResponse handlePageQueue(UrlPage url, SiteModel siteModel, PageModel pageModel, String path) throws IOException {
        if (!pageQueue.contains(url)) {
            pageQueue.add(url);
            log.info("Страница добавлена в очередь для проверки: {}", path);
            return createSuccessResponse();
        } else {
            pageQueue.remove(url);

            siteDataService.deletePage(pageModel);
            processIndexPage(siteModel, url.url(), userAgent, referrer);
            pageService.updateSiteStatus(siteModel, Status.INDEXED, null);

            log.info("Страница проиндексирована: {}", path);
        }
        return createSuccessResponse();
    }

    private IndexingResponse createSuccessResponse() {
        return new IndexingResponse(true);
    }
}


