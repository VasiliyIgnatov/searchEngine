package searchengine.service.indexing;

import searchengine.dto.indexing.UrlPage;

public interface IndexingService<T> {
    T startIndexing();
    T stopIndexing();
    T indexPage(UrlPage url);
}
