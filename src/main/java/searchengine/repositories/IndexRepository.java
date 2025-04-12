package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.IndexModel;
import searchengine.model.LemmaModel;
import searchengine.model.PageModel;

import java.util.List;

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
}
