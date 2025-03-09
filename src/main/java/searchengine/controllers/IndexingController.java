package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.indexing.UrlPage;
import searchengine.service.IndexingService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class IndexingController {

    private final IndexingService<IndexingResponse> indexingService;

    @GetMapping("/startIndexing")
    public ResponseEntity<IndexingResponse> startIndexing() {
        IndexingResponse indexingResponse = indexingService.startIndexing();
        return ResponseEntity.status(HttpStatus.OK).body(indexingResponse);
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<IndexingResponse> stopIndexing() {
        IndexingResponse indexingResponse = indexingService.stopIndexing();
        return ResponseEntity.status(HttpStatus.OK).body(indexingResponse);
    }

    @PostMapping("/indexPage")
    public ResponseEntity<IndexingResponse> indexPage(@ModelAttribute UrlPage urlPage) {
        IndexingResponse indexingResponse = indexingService.indexPage(urlPage);
        return ResponseEntity.status(HttpStatus.CREATED).body(indexingResponse);
    }
}
