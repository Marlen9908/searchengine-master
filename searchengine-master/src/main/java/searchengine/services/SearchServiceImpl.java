package searchengine.services;

import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.search.SearchResultItem;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchServiceImpl implements SearchService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final RussianLuceneMorphology morphology;

    public SearchServiceImpl(SiteRepository siteRepository,
                             PageRepository pageRepository,
                             LemmaRepository lemmaRepository,
                             IndexRepository indexRepository) throws Exception {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.morphology = new RussianLuceneMorphology();
    }

    @Override
    public SearchResponse search(String query, String siteUrl, int offset, int limit) {

        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Задан пустой поисковый запрос");
        }

        List<Site> sites = siteUrl == null
                ? siteRepository.findAll()
                : siteRepository.findByUrl(siteUrl).map(List::of).orElse(List.of());

        List<String> lemmas = extractLemmas(query);
        if (lemmas.isEmpty()) {
            return emptyResponse();
        }

        List<Lemma> lemmaEntities = lemmas.stream()
                .flatMap(l -> lemmaRepository.findByLemma(l).stream())
                .filter(l -> sites.contains(l.getSite()))
                .sorted(Comparator.comparingInt(Lemma::getFrequency))
                .collect(Collectors.toList());

        if (lemmaEntities.isEmpty()) {
            return emptyResponse();
        }

        Set<Page> pages = findPagesContainingAllLemmas(lemmaEntities);

        Map<Page, Float> relevanceMap = calculateRelevance(pages, lemmaEntities);

        List<SearchResultItem> data = relevanceMap.entrySet().stream()
                .sorted(Map.Entry.<Page, Float>comparingByValue().reversed())
                .skip(offset)
                .limit(limit)
                .map(e -> buildResultItem(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        SearchResponse response = new SearchResponse();
        response.setResult(true);
        response.setCount(relevanceMap.size());
        response.setData(data);

        return response;
    }

    private Set<Page> findPagesContainingAllLemmas(List<Lemma> lemmas) {

        Set<Page> result = null;

        for (Lemma lemma : lemmas) {
            List<IndexEntity> indices = indexRepository.findByLemma(lemma);
            Set<Page> pages = indices.stream()
                    .map(IndexEntity::getPage)
                    .collect(Collectors.toSet());

            if (result == null) {
                result = pages;
            } else {
                result.retainAll(pages);
            }
        }

        return result == null ? Set.of() : result;
    }

    private Map<Page, Float> calculateRelevance(Set<Page> pages, List<Lemma> lemmas) {

        Map<Page, Float> relevance = new HashMap<>();

        for (Page page : pages) {
            float sum = 0;

            for (Lemma lemma : lemmas) {
                Optional<IndexEntity> indexOpt =
                        indexRepository.findByPageAndLemma(page, lemma);

                if (indexOpt.isPresent()) {
                    sum += indexOpt.get().getRank();
                }
            }

            relevance.put(page, sum);
        }

        float max = relevance.values().stream()
                .max(Float::compare)
                .orElse(1f);

        relevance.replaceAll((p, v) -> v / max);
        return relevance;
    }


    private SearchResultItem buildResultItem(Page page, float relevance) {

        Site site = page.getSite();
        Document doc = Jsoup.parse(page.getContent());

        SearchResultItem item = new SearchResultItem();
        item.setSite(site.getUrl());
        item.setSiteName(site.getName());
        item.setUri(page.getPath());
        item.setTitle(doc.title());
        item.setSnippet(buildSnippet(doc.text()));
        item.setRelevance(relevance);

        return item;
    }

    private String buildSnippet(String text) {

        if (text.length() < 200) return text;

        return text.substring(0, 200) + "...";
    }

    private List<String> extractLemmas(String query) {

        return Arrays.stream(query.toLowerCase().split("\\s+"))
                .filter(w -> w.length() > 2)
                .map(w -> morphology.getNormalForms(w))
                .filter(l -> !l.isEmpty())
                .map(l -> l.get(0))
                .distinct()
                .collect(Collectors.toList());
    }

    private SearchResponse emptyResponse() {

        SearchResponse r = new SearchResponse();
        r.setResult(true);
        r.setCount(0);
        r.setData(List.of());
        return r;
    }
}

