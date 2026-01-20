package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.RecursiveTask;


public class LinkFinder extends RecursiveTask<Map<String, Document>> {

    private String url;
    private final String rootUrl;
    protected static Set<String> checkUrlSet = new CopyOnWriteArraySet<>();

    public LinkFinder(String rootUrl, String url) {
        this.url = url;
        this.rootUrl = rootUrl.replace("https://www.", "https://");
    }

    @Override
    protected Map<String, Document> compute() {
            Map<String, Document> linksMap = new TreeMap<>();
            Set<LinkFinder> tasks = new HashSet<>();

            Document document;
            Elements elements;
            try {
                Thread.sleep(200);
                document = Jsoup.connect(url).get();

                linksMap.put(url, document);
                elements = document.select("a");
                elements.forEach(el ->  {
                    String item = el.attr("abs:href");
                    if (!item.isEmpty()
                            && !checkUrlSet.contains(item)
                            && !item.contains("#")
                            && !item.contains("?")
                            && checkUrl(item)
                            && item.replace("https://www.", "https://").startsWith(rootUrl)){

                        linksMap.put(item, document);
                        LinkFinder linkFinderTask = new LinkFinder(rootUrl, item);
                        linkFinderTask.fork();
                        tasks.add(linkFinderTask);
                        checkUrlSet.add(item);

                    }
                });
            } catch (InterruptedException | IOException ignored) {    }

            tasks.forEach(task -> linksMap.putAll(task.join()));

        tasks.forEach(task -> task.quietlyComplete());
            tasks.clear();

        return  linksMap;
    }

    private boolean checkUrl(String item) {
        String[] url = item.split("/");
        String lastUrl = url[url.length-1];

        return lastUrl.contains(".") && !lastUrl.endsWith(".html")? false : true;
    }

}
