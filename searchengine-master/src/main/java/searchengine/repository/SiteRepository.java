package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.Site;

import java.util.List;

public interface SiteRepository extends JpaRepository<Site, Integer>{

    List<Site> findAllByUrl(String url);

    Site findByUrl(String url);



}
