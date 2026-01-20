package searchengine.model;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Data
@Table(name = "pages")
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

//    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @ManyToOne
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(nullable = false, unique = true, length = 500)
    private String path;

    @Column(columnDefinition = "INT NOT NULL")
    private Integer code;

    @Column(columnDefinition = "LONGTEXT NOT NULL")
    private String content;

    @JsonIgnore
    @OneToMany(mappedBy = "page")
    private List<Index> indexes = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Page page)) return false;
        return getId().equals(page.getId())
                && getSite().equals(page.getSite())
                && getPath().equals(page.getPath())
                && getCode().equals(page.getCode())
                && getContent().equals(page.getContent());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getSite(), getPath(), getCode(), getContent());
    }
}
