package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.SitesList;
import searchengine.dto.statistics.ApiResponse;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.SiteStatus;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class IndexingServiceImpl implements IndexingService {

    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaService lemmaService;


    private final AtomicBoolean indexing = new AtomicBoolean(false);

    public IndexingServiceImpl(SitesList sitesList,
                               SiteRepository siteRepository,
                               PageRepository pageRepository,
                               LemmaService lemmaService) {
        this.sitesList = sitesList;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaService = lemmaService;
    }


    // ================= START INDEXING =================

    @Override
    @Transactional
    public ApiResponse startIndexing() {

        if (indexing.get()) {
            return new ApiResponse(false, "Индексация уже запущена");
        }

        indexing.set(true);
        siteRepository.deleteAll();

        for (searchengine.config.Site siteConfig : sitesList.getSites()) {

            Site site = new Site();
            site.setUrl(siteConfig.getUrl());
            site.setName(siteConfig.getName());
            site.setStatus(SiteStatus.INDEXING);
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);

            new Thread(() -> {
                try {
                    startSiteIndexing(site);
                    site.setStatus(SiteStatus.INDEXED);
                } catch (Exception e) {
                    site.setStatus(SiteStatus.FAILED);
                    site.setLastError(e.getMessage());
                } finally {
                    site.setStatusTime(LocalDateTime.now());
                    siteRepository.save(site);
                }
            }).start();
        }

        return new ApiResponse(true);
    }

    // ================= STOP INDEXING =================

    @Override
    @Transactional
    public ApiResponse stopIndexing() {

        if (!indexing.get()) {
            return new ApiResponse(false, "Индексация не запущена");
        }

        indexing.set(false);

        siteRepository.findAll().forEach(site -> {
            if (site.getStatus() == SiteStatus.INDEXING) {
                site.setStatus(SiteStatus.FAILED);
                site.setLastError("Индексация остановлена пользователем");
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
            }
        });

        return new ApiResponse(true);
    }

    // ================= INDEX ONE PAGE =================

    @Override
    @Transactional
    public ApiResponse indexPage(String url) {

        if (url == null || url.isBlank()) {
            return new ApiResponse(false, "Не задан URL");
        }

        Optional<searchengine.config.Site> siteCfgOpt = sitesList.getSites().stream()
                .filter(s -> url.startsWith(s.getUrl()))
                .findFirst();

        if (siteCfgOpt.isEmpty()) {
            return new ApiResponse(false,
                    "Данная страница находится за пределами сайтов, указанных в конфигурации");
        }

        searchengine.config.Site siteCfg = siteCfgOpt.get();

        Site site = siteRepository.findByUrl(siteCfg.getUrl())
                .orElseGet(() -> {
                    Site s = new Site();
                    s.setUrl(siteCfg.getUrl());
                    s.setName(siteCfg.getName());
                    s.setStatus(SiteStatus.INDEXING);
                    s.setStatusTime(LocalDateTime.now());
                    return siteRepository.save(s);
                });

        String path = url.substring(site.getUrl().length());
        if (path.isEmpty()) {
            path = "/";
        }

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .referrer("https://www.google.com")
                    .get();

            pageRepository.findBySiteAndPath(site, path)
                    .ifPresent(pageRepository::delete);

            Page page = new Page();
            page.setSite(site);
            page.setPath(path);
            page.setCode(200);
            page.setContent(doc.html());
            pageRepository.save(page);

            site.setStatus(SiteStatus.INDEXED);
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);

            return new ApiResponse(true);

        } catch (Exception e) {
            site.setStatus(SiteStatus.FAILED);
            site.setLastError(e.getMessage());
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);

            return new ApiResponse(false, "Ошибка индексации страницы");
        }
    }

    // ================= FORK JOIN =================

    private void startSiteIndexing(Site site) {

        ForkJoinPool pool = new ForkJoinPool();
        pool.invoke(new PageCrawlerTask(
                site.getUrl(),
                site,
                pageRepository,
                lemmaService,
                indexing
        ));
        pool.shutdown();
    }
}


