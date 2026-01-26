package searchengine.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.Lemma;
import searchengine.model.Site;

import java.util.List;
import java.util.Set;

public interface LemmaRepository extends JpaRepository<Lemma, Integer>{

    List<Lemma> findAllBySiteAndLemmaIn(Site site, Set<String> words);

    // БЫЛО: FROM lemmas
    // СТАЛО: FROM lemma
    @Query(value ="SELECT * FROM lemma WHERE lemma In(:words) AND frequency <= ((select MAX(frequency) FROM lemma) / 2) AND site_id = :site ORDER BY frequency", nativeQuery = true)
    Page<Lemma> findLowFrequencyLemmaSortedAscOneSite(@Param("words")  Set<String> words, @Param("site") Site site, Pageable page);

    // БЫЛО: FROM lemmas
    // СТАЛО: FROM lemma
    @Query(value ="SELECT * FROM lemma WHERE lemma In(:words) AND frequency <= ((select MAX(frequency) FROM lemma) / 2) ORDER BY frequency", nativeQuery = true)
    Page<Lemma> findLowFrequencyLemmaSortedAscAllSites(@Param("words")  Set<String> words, Pageable page);
}
