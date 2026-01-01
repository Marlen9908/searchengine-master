package searchengine.services;

import org.springframework.stereotype.Service;
import searchengine.dto.statistics.*;
import searchengine.model.Site;
import searchengine.model.SiteStatus;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.util.ArrayList;
import java.util.List;

@Service
public class StatisticsServiceImpl implements StatisticsService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;

    public StatisticsServiceImpl(SiteRepository siteRepository,
                                 PageRepository pageRepository,
                                 LemmaRepository lemmaRepository) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
    }

    @Override
    public StatisticsResponse getStatistics() {

        List<Site> sites = siteRepository.findAll();

        int totalPages = 0;
        int totalLemmas = 0;
        boolean indexing = false;

        List<DetailedStatisticsItem> detailed = new ArrayList<>();

        for (Site site : sites) {
            int pages = pageRepository.findBySite(site).size();
            int lemmas = lemmaRepository.countBySite(site);

            totalPages += pages;
            totalLemmas += lemmas;

            if (site.getStatus() == SiteStatus.INDEXING) {
                indexing = true;
            }

            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setUrl(site.getUrl());
            item.setName(site.getName());
            item.setStatus(site.getStatus().name());
            item.setStatusTime(site.getStatusTime().toEpochSecond(java.time.ZoneOffset.UTC) * 1000);
            item.setError(site.getLastError());
            item.setPages(pages);
            item.setLemmas(lemmas);

            detailed.add(item);
        }

        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.size());
        total.setPages(totalPages);
        total.setLemmas(totalLemmas);
        total.setIndexing(indexing);

        StatisticsResponse response = new StatisticsResponse();
        response.setResult(true);
        response.setStatistics(new StatisticsData());
        response.getStatistics().setTotal(total);
        response.getStatistics().setDetailed(detailed);

        return response;
    }
}

