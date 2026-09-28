package com.chris64233.cc.biobank.disposition;

import com.chris64233.cc.biobank.disposition.dto.DispositionRequest;
import com.chris64233.cc.biobank.disposition.dto.DispositionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dispositions")
public class DispositionController {

    private final DispositionService dispositionService;

    public DispositionController(DispositionService dispositionService) {
        this.dispositionService = dispositionService;
    }

    @PostMapping
    public ResponseEntity<DispositionResponse> dispose(
            @Valid @RequestBody DispositionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dispositionService.dispose(request));
    }
}
