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

@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexService indexService;

    @GetMapping("/statistics")
    public StatisticsResponse statistics() { // Было: ResponseEntity<StatisticsResponse>
        return statisticsService.getStatistics(); // Было: return ResponseEntity.ok(...)
    }

    @GetMapping("/startIndexing")
    public IndexResponse startIndexing() {
        return indexService.startIndexing();
    }

    @GetMapping("/stopIndexing")
    public IndexResponse stopIndexing() {
        return indexService.stopIndexing();
    }

    @PostMapping("/indexPage")
    public IndexResponse indexPage(@RequestParam String url) {
        return indexService.startIndexingSinglePage(url);
    }

    @GetMapping("/search")
    public SearchResponse search(SearchDto searchDto) {
        return indexService.search(searchDto);
    }
}
