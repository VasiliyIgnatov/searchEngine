package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.search.SearchResponse;
import searchengine.service.search.SearchService;

import java.io.IOException;

@RestController
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@RequestMapping("/api")
public class SearchController {
    private final SearchService<SearchResponse> searchService;

    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(@RequestParam(required = false) String query,
                                                 @RequestParam(required = false) String site,
                                                 @RequestParam(defaultValue = "0") int offset,
                                                 @RequestParam(defaultValue = "20") int limit) throws IOException {
        SearchResponse response = searchService.search(query, site, offset, limit);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
