package searchengine.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import javax.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Table(name = "site")
public class Site {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "status", columnDefinition = "ENUM('INDEXING', 'INDEXED', 'FAILED') NOT NULL")
    @Enumerated(value = EnumType.STRING)
    private Status status;

    @Column(name = "status_time", columnDefinition = "TIMESTAMP NOT NULL")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy hh:mm:ss")
    private LocalDateTime statusTime;

    @Column(name = "last_error", columnDefinition = "VARCHAR(500)")
    private String lastError;

    @Column(name = "url", columnDefinition = "VARCHAR(255) NOT NULL")
    private String url;

    @Column(name = "name", columnDefinition = "VARCHAR(255) NOT NULL")
    private String name ;

    @JsonIgnore
    @OneToMany(mappedBy = "site")
    private List<Page> pages = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "site")
    private List<Lemma> lemmas = new ArrayList<>();

}
