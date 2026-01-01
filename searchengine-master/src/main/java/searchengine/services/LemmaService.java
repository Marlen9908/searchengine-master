package searchengine.services;

import searchengine.model.Page;
import searchengine.model.Site;

public interface LemmaService {

    void processPage(Page page, Site site);
}

