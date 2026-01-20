package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import searchengine.config.SiteFromProp;
import searchengine.config.SitesList;
import searchengine.dto.index.*;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexService {
    private final SitesList sites;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final WordService wordService;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private ForkJoinPool pool;

    public IndexResponse startIndexing() {
        IndexResponse response = new IndexResponse();

        if (pool != null && !pool.isTerminated()) {
            response.setError("Индексация уже запущена");
            return response;
        }

        response.setError("Индексация не выполнена, перезагрузите страницу");

        Map<Site, Map<String, Document> > mapOfResultMap = new HashMap<>();

        for (SiteFromProp siteFromProp : sites.getSites()){

            Optional<Site> siteFromRepo = siteRepository.findAllByUrl(siteFromProp.getUrl()).stream().findFirst();

            Site site;
            if (siteFromRepo.isPresent()) {
                siteFromRepo.get().getPages().clear();

                List<searchengine.model.Page> pagesForDelete = pageRepository.findAllBySite(siteFromRepo.get());

                for (searchengine.model.Page page : pagesForDelete){
                    List<Lemma> lemmasForDelete = deleteIndex(page);
                    deleteLemma(lemmasForDelete);
                    deletePage(page);
                }

                site = refreshSite(siteFromRepo.get(), Status.INDEXING);

            } else {

                site = createSite(siteFromProp, Status.INDEXING);
            }

            pool = new ForkJoinPool();
            log.info("Состояние объекта pool: " + pool.toString());
            log.info("Обход страниц сайта " + siteFromProp.getUrl() + " начался...");

            try {
                LinkFinder linkFinder = new LinkFinder(siteFromProp.getUrl(), site.getUrl());
                Map<String, Document> resultMapForkJoinOriginal = pool.invoke(linkFinder);
                pool.shutdown();
//                linkFinder.checkUrlSet = null; // очистка памяти

                log.info("Найдено страниц для индексации = " + resultMapForkJoinOriginal.size());
                if (resultMapForkJoinOriginal.size() < 2) continue;

                writeToFile(site.getName(), resultMapForkJoinOriginal.keySet()); // ************** сохранение в файлик
                //**************** Искусственно уменьшаем количество страниц до 3, чтобы не ждать выполнения целый день *******************************
                Map<String, Document> resultMapForkJoin = new HashMap<>();
                int count2 = 0;
                for (Map.Entry<String, Document> pair : resultMapForkJoinOriginal.entrySet()) {
                    System.out.println(pair.getKey() + ":");
                    System.out.println(wordService.deleteTagsFromContent(pair.getValue().html()));
                    resultMapForkJoin.put(pair.getKey(), pair.getValue());
                    ++count2;
                    if (count2 >= 2) break;
                }

                //***********************************************************************************
                log.info("Обход страниц сайта " + siteFromProp.getUrl() + " закончился");
                log.info("Состояние объекта pool: " + pool.toString()); //

                mapOfResultMap.put(site, resultMapForkJoin);

                response.setResult(true);
                response.setError("");

            } catch (CancellationException ex) {
                stopPool(site);
                response.setResult(false);
                response.setError("Индексация остановлена пользователем");
                break;
            }
        }
        //**********************************************
        mapOfResultMap.forEach((site, mapJoinPool) -> {

            for (Map.Entry<String, Document> pair : mapJoinPool.entrySet()) {

            try {
                log.info("Обработка страницы " + pair.getKey()); //
                String contentWithoutTag = wordService.deleteTagsFromContent(pair.getValue().html());

                String splitedText = wordService.splitTextIntoWords(contentWithoutTag);

                Map<String, Integer> lemmasMap = wordService.getLemmasMap(splitedText);
                log.info("Для страницы " + pair.getKey() + " найдено лемм = " + lemmasMap.size()); //

                HashMap<Lemma,Integer> newLemmas = updateLemmas(lemmasMap, site);

                searchengine.model.Page newPage = createPage(site, pair.getKey(), pair.getValue());

                updateIndex(newLemmas, newPage);

                log.info("Выполнена индексация страницы: " + pair.getKey());
            } catch (Exception e) {  }

        }
            site.setStatus(Status.INDEXED);
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
        });

        updateStatusOfSite(mapOfResultMap);

        log.info("Все страницы обновлены");
        pool = null;
        return response;
    }

    public IndexResponse stopIndexing() {
        IndexResponse response = new IndexResponse();
        response.setError("Индексация остановлена пользователем");

        if (pool == null || pool.isTerminated()) {
            response.setError("Обход страниц сайта не выполняется");
            return response;
        }
        pool.shutdownNow();
        pool = null;
        return response;
    }

    private void stopPool(Site site){
        log.info("Прерывание. Состояние объекта pool: " + pool==null? "" : pool.toString());

        site.setLastError("Индексация прервана");
        site.setStatus(Status.FAILED);
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);
        pageRepository.deleteAll(pageRepository.findAllBySite(site));
        pool = null;
    }

    private Site createSite(SiteFromProp siteFromProp, Status status){
        Site site = new Site();
        site.setStatus(status);
        site.setUrl(siteFromProp.getUrl());
        site.setName(siteFromProp.getName());
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);
        log.info("Сервис создал новую запись сайта " + site.getName());
        return site;
    }

    private Site refreshSite(Site site, Status status){
        site.setStatus(status);
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);
        log.info("Сервис обновил запись сайта " + site.getName());
        return site;
    }

    private searchengine.model.Page createPage(Site site, String path, Document value){
        searchengine.model.Page page = new searchengine.model.Page();
        page.setSite(site);
        page.setPath(path);
        page.setCode(200);
        page.setContent(value.html());

        site.getPages().add(page);

        pageRepository.save(page);
        return page;
    }

    private void updateStatusOfSite(Map<Site, Map<String, Document>> mapOfResultMap){

        List<Site> siteJoinPool = mapOfResultMap.keySet().stream().collect(Collectors.toList());

        for (SiteFromProp siteFromProp : sites.getSites()) {

            if (siteJoinPool.isEmpty()) {
                log.info("Все сайты не найдены в выполненных"); //
                    Optional<Site> siteFromRepo = siteRepository.findAllByUrl(siteFromProp.getUrl()).stream().findFirst();

                    if (siteFromRepo.isPresent()) {
                        stopPool(siteFromRepo.get());
                    }
            } else {

                if (!siteJoinPool.stream().map(Site::getUrl).collect(Collectors.toList()).contains(siteFromProp.getUrl())) {
                    log.info("Сайт не найден в выполненных: " + siteFromProp.getUrl()); //

                    Optional<Site> siteFromRepo = siteRepository.findAllByUrl(siteFromProp.getUrl()).stream().findFirst();

                    if (siteFromRepo.isPresent()) {
                        stopPool(siteFromRepo.get());
                    }
                }
            }
        }
    }

    private static void writeToFile(String name, Set<String> set) {
        String filePath = name+ ".txt";
        System.out.println("Сохраняем данные в файл `" +filePath+ "`. Статистика:");

        File file = new File(filePath);
        try (PrintWriter writer = new PrintWriter(file)) {
            getSortMap(set).forEach((k,v) -> {
                System.out.println("Уровень вложенности= " + k + " Количество ссылок= "+ v.size());
                v.forEach(link -> writer.write("\t".repeat(k) + link + "\n"));
            });
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private static Map<Integer, Set<String>> getSortMap(Set<String> set) {
        Map<Integer, Set<String>> map = new TreeMap<>();
        set.forEach(item -> {
            String subStr = item.replace("https://", "");
            String[] array = subStr.split("/");
            int level = array.length - 1;

            if (map.containsKey(level)) {
                map.get(level).add(item);
            } else {
                Set<String> newSet = new TreeSet<>();
                newSet.add(item);
                map.put(level, newSet);
            }
        });
        return map;
    }

    public IndexResponse startIndexingSinglePage(String url) {
        IndexResponse response = new IndexResponse();

        if (url == null){
            response.setError("Не корректный url");
            return response;
        }

        if (pool != null) {
            response.setError("Индексация уже запущена");
            return response;
        }

        SiteFromProp domainSite = null;
        String corrUrl = url.substring(url.indexOf("https://"));

        for (SiteFromProp siteFromProp : sites.getSites()){
            String corrUrlsiteFromProp = siteFromProp.getUrl().replace("https://www.", "https://");

            if (corrUrl.replace("https://www.", "https://").startsWith(corrUrlsiteFromProp)){
                domainSite = siteFromProp;
                log.info("Заданный " +  corrUrl + " найден в в конфигурационном файле " + domainSite.getUrl());
                break;
            }
        }

         if (domainSite == null){
             log.info("Заданный " +  corrUrl + " находится за пределами сайтов, указанных в конфигурационном файле!");
             response.setError("Данная страница находится за пределами сайтов, указанных в конфигурационном файле!");
             return response;
         }
         try {
             log.info("Обработка страницы " + corrUrl); //
             Site site = siteRepository.findByUrl(domainSite.getUrl());

             if (site == null) {
                 site = createSite(domainSite,  Status.INDEXING);
             } else {
                 site.setStatus(Status.INDEXING);
                 site.setStatusTime(LocalDateTime.now());
                 siteRepository.save(site);
             }

             Document document = Jsoup.connect(corrUrl).get();

             String contentWithoutTag = wordService.deleteTagsFromContent(document.html());

             String splitedText = wordService.splitTextIntoWords(contentWithoutTag);

             Map<String, Integer> lemmasMap = wordService.getLemmasMap(splitedText);
             log.info("Для страницы " +corrUrl + " найдено лемм = " + lemmasMap.size()); //

             Optional<searchengine.model.Page> page = pageRepository.findByPath(corrUrl);

             if (page.isPresent()) {
                 List<Lemma> lemmasForDelete = deleteIndex(page.get());

                 deleteLemma(lemmasForDelete);

                 deletePage(page.get());
             }

             searchengine.model.Page newPage = createPage(site, corrUrl, document);

             HashMap<Lemma,Integer> newLemmas =  updateLemmas(lemmasMap, site);

             updateIndex(newLemmas, newPage);

             log.info("Выполнена индексация страницы " + corrUrl);

             site.setStatus(Status.INDEXED);
             site.setStatusTime(LocalDateTime.now());
             siteRepository.save(site);

             response.setError("");
             response.setResult(true);

         } catch (Exception ignored) {
             response.setError("Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
         }
         return response;
    }

    private void deletePage(searchengine.model.Page page) {
        page.getIndexes().clear();
        pageRepository.delete(page);
        log.info("Удаление страницы выполнено");
    }

    private void deleteLemma(List<Lemma> lemmas) {
        lemmas.forEach(lemma -> {
            lemma.getIndexes().clear();
            synchronized (lemma) {
                if (lemma.getFrequency() > 1) {
                    lemma.decreaseFrequency();
                    lemmaRepository.save(lemma);
                } else {
                    lemmaRepository.delete(lemma);
                }
            }
        });
        log.info("Удаление лемм выполнено");
    }

    private List<Lemma> deleteIndex(searchengine.model.Page page) {
        List<Lemma> lemmasForDelete = new ArrayList<>();

        indexRepository.findAllByPage(page).forEach(index -> {

            lemmasForDelete.add(index.getLemma());

            indexRepository.delete(index);
        });
        log.info("Удаление индексов выполнено");
        return lemmasForDelete;
    }



    public HashMap<Lemma, Integer> updateLemmas(Map<String, Integer> lemmasMap , Site site){
        HashMap<Lemma, Integer> lemmasResult = new HashMap<>();

        List<Lemma> lemmasFromRepo = lemmaRepository.findAllBySiteAndLemmaIn(site, lemmasMap.keySet());

        for (Map.Entry<String, Integer> pair : lemmasMap.entrySet()) {

            try {
                Optional<Lemma> lemma = lemmasFromRepo.stream().filter(lemmaRepo -> lemmaRepo.getLemma().equalsIgnoreCase(pair.getKey())).findFirst();
                if (!lemma.isPresent()) {

                    Lemma newLemma = new Lemma();
                    newLemma.setLemma(pair.getKey());
                    newLemma.setFrequency(1);
                    newLemma.setSite(site);

                    lemmaRepository.save(newLemma);

                    lemmasResult.put(newLemma, pair.getValue());

                    site.getLemmas().add(newLemma);

                } else {
                    synchronized (lemma.get()){
                        lemma.get().increaseFrequency();
                        lemmaRepository.save(lemma.get());
                    }

                    lemmasResult.put(lemma.get(), pair.getValue());
                    site.getLemmas().add(lemma.get());
                }
            } catch (Throwable e) {
                log.error("Ошибка сохранения Леммы- " + pair.getKey() + ". Причина - " + e.getMessage());
            }
        }
        return lemmasResult;
    }

    public void updateIndex(HashMap<Lemma,Integer> lemmasMap, searchengine.model.Page page) {
        List<Index> indexList = new ArrayList<>();

        for (Map.Entry<Lemma, Integer> pair : lemmasMap.entrySet()) {

            Index index = new Index();
            index.setRang(Float.valueOf(pair.getValue()));
            index.setLemma(pair.getKey());
            index.setPage(page);

            indexList.add(index);
        }
        indexRepository.saveAll(indexList);

        page.setIndexes(indexList);
    }

    public SearchResponse search(SearchDto searchDto) {

        log.info("Query: " + searchDto.getQuery() + " ,Site: "+ searchDto.getSite() + " ,Offset: " + searchDto.getOffset() + " ,Limit: " + searchDto.getLimit());

        SearchResponse response = new SearchResponse();
        if (searchDto == null || searchDto.getQuery() == null || searchDto.getQuery().isBlank()) {
            response.setResult(false);
            response.setError("Задан пустой поисковый запрос");
            return response;
        }

        String splitedText = wordService.splitTextIntoWords(searchDto.getQuery());

        Map<String, Integer> lemmasMap = wordService.getLemmasMap(splitedText);

        int limit = searchDto.getLimit()==0? 20 : searchDto.getLimit();
        Pageable next = PageRequest.of(searchDto.getOffset(), limit);
        Site site;
        List<Lemma> lowFrequencyLemmaList;

        if (searchDto.getSite() == null) {
            lowFrequencyLemmaList = lemmaRepository.findLowFrequencyLemmaSortedAscAllSites(lemmasMap.keySet(), next).getContent();
        } else {
            site = siteRepository.findByUrl(searchDto.getSite());
            lowFrequencyLemmaList =  lemmaRepository.findLowFrequencyLemmaSortedAscOneSite(lemmasMap.keySet(), site, next).getContent();
        }

        log.info("lowFrequencyLemmaList: " + lowFrequencyLemmaList.size());

        if (lowFrequencyLemmaList.size() == 0) {
            log.info("Ничего не нашли");
            response.setResult(true);
            response.setError("Ничего не нашли");
            return response;
        }
        List<RelevanceDto> relevanceDtoList = new LinkedList<>();

        for (Lemma lemma : lowFrequencyLemmaList) {

            List<Index> indexList = indexRepository.findAllByLemma(lemma);

            indexList.forEach(index -> {

                        searchengine.model.Page page = index.getPage();

                        Optional<RelevanceDto> dto = getRelevanceDto(relevanceDtoList, page);

                            if (dto.isPresent()) {
                                dto.get().increaseRang(index.getRang());
                                dto.get().getWords().add(lemma.getLemma());
                            } else {
                                RelevanceDto relevanceDto = new RelevanceDto();
                                relevanceDto.setPage(page);
                                relevanceDto.setRang(index.getRang());
                                relevanceDto.getWords().add(lemma.getLemma());

                                relevanceDtoList.add(relevanceDto);
                            }
                    }
            );
        }

        Optional<RelevanceDto> maxRelevance = relevanceDtoList.stream().max(Comparator.comparing(RelevanceDto::getRang));
        float max =  maxRelevance.isPresent()? maxRelevance.get().getRang() : 1 ;

        List<RelevanceDto> sortedRelevantList =  relevanceDtoList.stream().sorted(Comparator.comparing(RelevanceDto::getRang).reversed()).collect(Collectors.toList());

        sortedRelevantList.forEach(item -> {

            DataResponse dataResponse = new DataResponse();
            dataResponse.setSite(item.getPage().getSite().getUrl());
            dataResponse.setSiteName(item.getPage().getSite().getName());
            dataResponse.setUri(item.getPage().getPath());

            Document document = Jsoup.parse(item.getPage().getContent());
            dataResponse.setTitle(document.select("title").text());

            dataResponse.setSnippet(createSnippet(document.body().text(), item.getWords()));

            dataResponse.setRelevance(item.getRang()/max);

            response.getData().add(dataResponse);
        });

        response.setResult(true);
        response.setCount(response.getData().size());

        return response;
    }

    public Optional<RelevanceDto> getRelevanceDto(List<RelevanceDto> relevanceDtoList, Page page){
      return  relevanceDtoList.stream().filter(item -> item.getPage() == page).findFirst();
    }

    public static String createSnippet(String content, List<String> keywords) {
        log.info("Составляем # сниппетов для слов " + keywords.size());

        int width = 100;
        StringBuilder sb = new StringBuilder();

        for (String word : keywords) {

            String regex = "\\b\\w?" + word + "\\W*\\b";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(content.toLowerCase());

            if (!matcher.find()) {
                log.info("Не нашел с 1 раза");

                if (word.length() < 3) continue;

                String trimWord = word.substring(0, word.length() - 1);
                regex = "\\b\\w?" + trimWord + "\\W*\\b";
                pattern = Pattern.compile(regex);
                matcher = pattern.matcher(content.toLowerCase());

                if (!matcher.find()) {
                    log.info("Не нашел со 2 раза");

                    if (word.length() < 4) continue;

                    trimWord = word.substring(0, word.length() - 2);
                    regex = "\\b\\w?" + trimWord + "\\W*\\b";
                    pattern = Pattern.compile(regex);
                    matcher = pattern.matcher(content.toLowerCase());

                    if (!matcher.find()) {
                        log.info("Не нашел с 3 раза. Пропускаем слово.");
                        continue;
                    }
                }
            }
            int wordIndex = matcher.start();

            String originalWord = content.substring(wordIndex, wordIndex + word.length());

            int snippetStart = Math.max(0, wordIndex - width);
            int snippetEnd = Math.min(content.length(), wordIndex + word.length() + width);

            String snippet = content.substring(snippetStart, snippetEnd);

            String originalSippet = snippet.replaceAll(originalWord, "<b>" + originalWord + "</b>");

            sb.append("...").append(originalSippet).append("...").append("<br>");
        }
        return sb.toString();

    }
}
