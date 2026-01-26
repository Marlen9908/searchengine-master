package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.index.IndexResponse;
import searchengine.dto.index.SearchDto;
import searchengine.dto.index.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexService;
import searchengine.services.StatisticsService;

import javax.servlet.http.HttpServletRequest;

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
    public SearchResponse search(HttpServletRequest request) {
        log.info("GET запрос Получение страницы search");

        // Безопасное получение offset (если нет - то 0)
        String offsetParam = request.getParameter("offset");
        int offset = (offsetParam == null) ? 0 : Integer.parseInt(offsetParam);

        // Безопасное получение limit (если нет - то 20)
        String limitParam = request.getParameter("limit");
        int limit = (limitParam == null) ? 20 : Integer.parseInt(limitParam);

        SearchDto searchDto = SearchDto.builder()
                .query(request.getParameter("query"))
                .site(request.getParameter("site"))
                .offset(offset)
                .limit(limit)
                .build();

        return indexService.search(searchDto);
    }
}
