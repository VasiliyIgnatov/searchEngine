package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.PageModel;
import searchengine.model.SiteModel;

import java.util.List;


public interface PageRepository extends JpaRepository<PageModel, Integer> {
    Boolean existsByPath(String path);

    @Query("SELECT COUNT(i) FROM PageModel i WHERE i.site.url = :url")
    int countBySiteUrl(@Param("url") String url);
    PageModel findByPathAndSite(String path, SiteModel siteModel);

    void deleteBySite(SiteModel site);

    List<PageModel> findBySite(SiteModel site);

    Integer countBySiteId(int siteId);

}
