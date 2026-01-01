package searchengine.services;

import searchengine.dto.statistics.ApiResponse;

public interface IndexingService {

    ApiResponse startIndexing();

    ApiResponse stopIndexing();

    ApiResponse indexPage(String url);
}

