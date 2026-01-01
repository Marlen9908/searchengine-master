package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.IndexEntity;
import searchengine.model.Lemma;
import searchengine.model.Page;

import java.util.List;
import java.util.Optional;

public interface IndexRepository extends JpaRepository<IndexEntity, Integer> {

    List<IndexEntity> findByLemma(Lemma lemma);

    Optional<IndexEntity> findByPageAndLemma(Page page, Lemma lemma);
}


