package searchengine.service.statistics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.SiteModel;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class StatisticsServiceImpl implements StatisticsService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmasRepository;

    @Transactional(readOnly = true)
    @Override
    public StatisticsResponse getStatistics() {
        TotalStatistics total = new TotalStatistics();
        total.setSites(siteRepository.count());
        List<DetailedStatisticsItem> detailed = new CopyOnWriteArrayList<>();
        List<SiteModel> sitesList = siteRepository.findAll();

        long totalPages = sitesList.stream()
                .mapToLong(site -> pageRepository.countBySiteId(site.getId()))
                .sum();

        long totalLemmas = sitesList.stream()
                .mapToLong(site -> lemmasRepository.countBySiteId(site.getId()))
                .sum();

        total.setPages(totalPages);
        total.setLemmas(totalLemmas);
        total.setIndexing(true);

        log.info("Total sites: {}", total.getSites());
        log.info("Total pages: {}", total.getPages());
        log.info("Total lemmas: {}", total.getLemmas());

        sitesList.forEach(site -> {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(site.getName());
            item.setUrl(site.getUrl());
            int pages = pageRepository.countBySiteId(site.getId());
            int lemmas = lemmasRepository.countBySiteId(site.getId());
            item.setPages(pages);
            item.setLemmas(lemmas);
            item.setStatus(site.getStatus().toString());
            item.setError(site.getLastError());
            item.setStatusTime(Instant.now().toEpochMilli());
            detailed.add(item);
        });

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }
}
