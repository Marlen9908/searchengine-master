package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;

import java.util.List;

public interface IndexRepository extends JpaRepository<Index, Integer>{

    List<Index> findAllByPage(Page page);

    List<Index> findAllByPageIn(List<Page> pages);

    List<Index> findAllByLemma(Lemma lemmas);

}
