package searchengine.repositories;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.IndexModel;
import searchengine.model.LemmaModel;
import searchengine.model.PageModel;

import java.util.List;
import java.util.Set;

public interface IndexRepository extends JpaRepository<IndexModel, Integer> {
    List<IndexModel> findByPage(PageModel pageModel);

    @Query("SELECT i.page FROM IndexModel i WHERE i.lemma.lemma = :lemma AND i.page.site.url = :url")
    List<PageModel> findPagesByLemmaAndSite(@Param("lemma") String lemma, @Param("url") String url);

    @Query("SELECT SUM(i.rank) FROM IndexModel i WHERE i.page = :page")
    Float sumRankByPage(@Param("page") PageModel page);

    @Query("SELECT COUNT(i) > 0 FROM IndexModel i WHERE i.page = :page AND i.lemma.lemma = :lemma AND i.page.site.url = :site")
    boolean existsByPageAndLemma(@Param("page") PageModel page, @Param("lemma") String lemma, @Param("site") String site);

    void deleteByPage(PageModel pageModel);

    Integer countByLemma(LemmaModel lemmaModel);

    @Query("SELECT i.page FROM IndexModel i WHERE i.lemma.lemma = :lemma")
    List<PageModel> findPagesByLemma(@Param("lemma") String lemma);

    @Query("SELECT i.page.id, SUM(i.rank) FROM IndexModel i WHERE i.page.id IN :pageIds GROUP BY i.page.id")
    List<Object[]> sumRankByPageIds(@Param("pageIds") List<Integer> pageIds);

    @Query("SELECT DISTINCT i.page.id from IndexModel i " +
            "WHERE i.lemma.lemma in :lemmas " +
            "and (:siteId is null or i.page.site.id = :siteId) " +
            "order by sum(i.rank) desc")
    List<Integer> findPageIdsByLemmas(@Param("lemmas") Set<String> lemmas, @Param("siteId") Integer siteId, Pageable pageable);

    @Query("select count (distinct i.page.id) from IndexModel  i " +
            "where i.lemma.lemma in :lemmas " +
            "and (:siteId is null or i.page.site.id = :sideId)")
    long countPagesByLemmas(@Param("lemmas") Set<String> lemmas, @Param("siteId") Integer siteId);

}
