package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.index.IndexResponse;
import searchengine.dto.index.SearchDto;
import searchengine.dto.index.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexService;
import searchengine.services.StatisticsService;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexService indexService;

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        log.info("Получение страницы statistics");
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<IndexResponse> startIndexing() {
        log.info("Получение страницы startIndexing");
        return ResponseEntity.ok(indexService.startIndexing());
//        return ResponseEntity.ok(new IndexResponse());
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<IndexResponse> stopIndexing() {
        log.info("Получение страницы stopIndexing");
        return ResponseEntity.ok(indexService.stopIndexing());
    }

    @PostMapping("/indexPage")
    public ResponseEntity<IndexResponse> lemma(String url) throws UnsupportedEncodingException {
        log.info("Выполнить индексацию одной страницы");
        return ResponseEntity.ok(indexService.startIndexingSinglePage(URLDecoder.decode(url, "UTF-8")));
    }


    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(HttpServletRequest request) {
        log.info("GET запрос Получение страницы search");
        SearchDto searchDto = SearchDto.builder()
                .query(request.getParameter("query"))
                .site(request.getParameter("site"))
                .offset(Integer.valueOf(request.getParameter("offset")))
                .limit(Integer.valueOf(request.getParameter("limit")))
                .build();

        return ResponseEntity.ok(indexService.search(searchDto));
    }

    @PostMapping("/search")
    public ResponseEntity<SearchResponse> search2(HttpServletRequest request) {
        log.info("POST запрос Получение страницы search");
        SearchDto searchDto = SearchDto.builder()
                .query(request.getParameter("query"))
                .site(request.getParameter("site"))
                .offset(request.getParameter("offset") !=null?  Integer.valueOf(request.getParameter("offset")) : 0)
                .limit(request.getParameter("limit") !=null?  Integer.valueOf(request.getParameter("limit")) : 20)
                .build();

        return ResponseEntity.ok(indexService.search(searchDto));
    }
}
