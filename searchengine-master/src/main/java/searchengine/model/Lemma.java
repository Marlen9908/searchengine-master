package searchengine.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Data
@Table(name = "lemmas", indexes = {@javax.persistence.Index(name="lemma_id", columnList = "lemma")})
public class Lemma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

     @ManyToOne
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(columnDefinition = "VARCHAR(255) NOT NULL")
    private String lemma;

    @Column(columnDefinition = "INT NOT NULL")
    private volatile Integer frequency;

    @JsonIgnore
    @OneToMany(mappedBy = "lemma")
    private List<Index> indexes = new ArrayList<>();

    public void increaseFrequency(){++frequency;}

    public void decreaseFrequency(){--frequency;}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Lemma lemma1)) return false;
        return getId().equals(lemma1.getId())
                && getSite().equals(lemma1.getSite())
                && getLemma().equals(lemma1.getLemma())
                && getFrequency().equals(lemma1.getFrequency());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getSite(), getLemma(), getFrequency());
    }
}
