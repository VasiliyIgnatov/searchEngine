package searchengine.service.search;

import java.io.IOException;

public interface SearchService<T> {
    T search(String query, String site, int offset, int limit) throws IOException;
}
