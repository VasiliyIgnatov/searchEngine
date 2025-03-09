package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.LemmaModel;
import searchengine.model.SiteModel;

import java.util.Optional;

public interface LemmaRepository extends JpaRepository<LemmaModel, Integer> {
   Optional<LemmaModel> findByLemmaAndSiteId(String lemma, int siteId);

   void deleteBySite(SiteModel siteModel);

   Integer countBySiteId(int siteId);

   long countByLemma(String lemma);

   LemmaModel findByLemmaAndSite(String lemma, SiteModel siteModel);

}
