package searchengine.dto.index;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SearchResponse {

    private boolean result;
    private int count;
    private List<DataResponse> data = new ArrayList<>();
    private String error;
}
