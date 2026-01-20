package searchengine.dto.index;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RelevanceDto {

    private searchengine.model.Page page;
    private List<String> words = new ArrayList<>();
    private volatile float rang;

    public void increaseRang(float rang){
        synchronized (this){
            this.rang = this.rang + rang;
        }
    }
}
