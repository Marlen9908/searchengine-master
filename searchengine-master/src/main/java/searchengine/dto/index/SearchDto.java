package searchengine.dto.index;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@AllArgsConstructor
@Data
@Builder
public class SearchDto {

    private String query;
    private String site;
    private int offset;
    private int limit;
}
