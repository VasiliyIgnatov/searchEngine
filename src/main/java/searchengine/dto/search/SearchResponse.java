package searchengine.dto.search;

import java.util.List;

public record SearchResponse(boolean result, int count, List<SearchResult> data) {
}
