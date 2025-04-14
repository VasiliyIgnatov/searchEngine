package searchengine.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.*;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class PageManager {

    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;

    @Transactional
    public void updateSiteStatus(SiteModel siteModel, Status status, String errorMessage) {
        siteModel.setStatus(status);
        siteModel.setStatusTime(LocalDateTime.now());
        siteModel.setLastError(errorMessage);
        siteRepository.save(siteModel);
    }

    @Transactional
    public PageModel createPageModel(int code, String content, String url, SiteModel siteModel) throws MalformedURLException {
        URL linkUrl = new URL(url);
        String path = linkUrl.getPath();

        if (path.isEmpty()) {
            path = path + "/";
        }
        PageModel pageModel = new PageModel();
        pageModel.setPath(path);
        pageModel.setSite(siteModel);
        pageModel.setCode(code);
        pageModel.setContent(content);
        pageRepository.save(pageModel);
        log.info("Страница сохранена: {}", pageModel.getPath());
        return pageModel;
    }
}


