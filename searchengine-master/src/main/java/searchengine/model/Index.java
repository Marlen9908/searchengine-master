package searchengine.model;

import lombok.Data;

import javax.persistence.*;
import java.util.Objects;

@Entity
@Data
@Table(name = "search_index")
public class Index {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

       @ManyToOne
    @JoinColumn(name = "page_id", nullable = false)
    private Page page;

       @ManyToOne
    @JoinColumn(name = "lemma_id", nullable = false)
    private Lemma lemma;

    @Column(columnDefinition = "FLOAT NOT NULL")
    private Float rang;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Index index)) return false;
        return getId().equals(index.getId())
                && getPage().equals(index.getPage())
                && getLemma().equals(index.getLemma())
                && getRang().equals(index.getRang());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getPage(), getLemma(), getRang());
    }

}
