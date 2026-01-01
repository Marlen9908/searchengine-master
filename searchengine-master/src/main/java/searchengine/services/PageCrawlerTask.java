package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.PageRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicBoolean;

public class PageCrawlerTask extends RecursiveAction {

    private final String url;
    private final Site site;
    private final PageRepository pageRepository;
    private final AtomicBoolean indexing;
    private final LemmaService lemmaService;


    private static final String USER_AGENT = "Mozilla/5.0";
    private static final String REFERRER = "https://www.google.com";

    public PageCrawlerTask(String url,
                           Site site,
                           PageRepository pageRepository,
                           LemmaService lemmaService,
                           AtomicBoolean indexing) {
        this.url = url;
        this.site = site;
        this.pageRepository = pageRepository;
        this.lemmaService = lemmaService;
        this.indexing = indexing;
    }


    @Override
    protected void compute() {

        if (!indexing.get()) {
            return;
        }

        String path = url.substring(site.getUrl().length());
        if (path.isEmpty()) {
            path = "/";
        }

        // защита от дублей
        Optional<Page> existing = pageRepository.findBySiteAndPath(site, path);
        if (existing.isPresent()) {
            return;
        }

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .referrer(REFERRER)
                    .ignoreHttpErrors(true)
                    .get();

            Page page = new Page();
            page.setSite(site);
            page.setPath(path);
            page.setCode(200);
            page.setContent(doc.html());
            pageRepository.save(page);

            Elements links = doc.select("a[href]");
            List<PageCrawlerTask> tasks = new ArrayList<>();

            for (Element link : links) {
                String absUrl = link.attr("abs:href");

                if (!isValidLink(absUrl)) {
                    continue;
                }

                tasks.add(new PageCrawlerTask(
                        absUrl,
                        site,
                        pageRepository,
                        lemmaService,
                        indexing
                ));
            }

            invokeAll(tasks);

        } catch (Exception ignored) {
        }
    }

    private boolean isValidLink(String link) {

        if (link == null || link.isEmpty()) {
            return false;
        }

        if (!link.startsWith(site.getUrl())) {
            return false;
        }

        if (link.contains("#")) {
            link = link.substring(0, link.indexOf('#'));
        }

        return !(
                link.endsWith(".pdf") ||
                        link.endsWith(".jpg") ||
                        link.endsWith(".png") ||
                        link.endsWith(".jpeg") ||
                        link.endsWith(".gif")
        );
    }
}

