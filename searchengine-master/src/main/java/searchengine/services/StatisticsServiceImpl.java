package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.SiteFromProp;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.IndexRepository;
import searchengine.repository.SiteRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private IndexRepository indexRepository;

    private final SitesList sites;


    @Override
    public StatisticsResponse getStatistics() {

        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setIndexing(true);

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        for(SiteFromProp siteFromProp : sites.getSites()) {
            Site site = siteRepository.findByUrl(siteFromProp.getUrl());

            if (site == null) continue;

            List<Page> pageList = site.getPages();  // либо из репозитория

            int pages = pageList.size();

            int lemmas = indexRepository.findAllByPageIn(pageList).size();

            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(site.getName());
            item.setUrl(site.getUrl());
            item.setPages(pages);
            item.setLemmas(lemmas);
            item.setStatus(site.getStatus().name());
            item.setError(site.getLastError() == null? "": site.getLastError());
            item.setStatusTime(site.getStatusTime());

            total.setPages(total.getPages() + pages);
            total.setLemmas(total.getLemmas() + lemmas);
            detailed.add(item);
        }

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }
}
