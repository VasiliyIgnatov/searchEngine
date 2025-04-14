package searchengine.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import searchengine.config.Site;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class SitePersistenceService {
    private final LemmaRepository lemmaRepository;
    private final SiteRepository siteRepository;
    private final IndexRepository indexRepository;
    private final PageRepository pageRepository;

    @Transactional
    public void saveSiteModel(Site site, SiteModel siteModel) {
        logIfTransactionInactive(siteModel);
        siteModel.setUrl(site.getUrl());
        siteModel.setName(site.getName());
        siteModel.setStatus(Status.INDEXING);
        siteRepository.save(siteModel);
        log.info("Создан новый SiteModel: {}", siteModel);
    }

    @Transactional
    public void deletePage(PageModel pageModel) {
        if (pageModel != null) {
            log.info("Удаляем страницу для повторной индексации: {}", pageModel.getPath());
            logIfTransactionInactive(pageModel.getSite());

            List<IndexModel> indexes = indexRepository.findByPage(pageModel);
            indexes.forEach(index -> {
                LemmaModel lemma = index.getLemma();
                indexRepository.delete(index);

                if (indexRepository.countByLemma(lemma) == 0) {
                    lemmaRepository.delete(lemma);
                }
            });

            pageRepository.delete(pageModel);
            pageRepository.flush();
            log.info("Страница успешно удалена: {}", pageModel.getPath());
        } else {
            log.warn("Страница не найдена для удаления");
        }
    }

    @Transactional
    public void deleteData(SiteModel siteModel) {
        try {
            List<PageModel> pages = pageRepository.findBySite(siteModel);
            for (PageModel page : pages) {
                indexRepository.deleteByPage(page);
            }
            pageRepository.deleteBySite(siteModel);
            lemmaRepository.deleteBySite(siteModel);
            siteRepository.delete(siteModel);
            log.info("Сайты удалены для повторной индексации: {}", siteModel);
        } catch (Exception e) {
            log.error("Ошибка при удалении данных для сайта {}", siteModel);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public void logIfTransactionInactive(SiteModel siteModel) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            log.info("Не найдена активная транзакция! = {}", siteModel);
        }
    }
}
