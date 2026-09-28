package com.chris64233.cc.biobank.disposal;

import com.chris64233.cc.biobank.disposal.dto.DisposeRequest;
import com.chris64233.cc.biobank.disposal.dto.DisposalItemResponse;
import com.chris64233.cc.biobank.disposal.dto.DisposalResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subjects/{subjectCode}/disposals")
public class DisposalController {

    private final DisposalService disposalService;
    private final DisposalItemRepository disposalItemRepository;

    public DisposalController(DisposalService disposalService,
            DisposalItemRepository disposalItemRepository) {
        this.disposalService = disposalService;
        this.disposalItemRepository = disposalItemRepository;
    }

    @PostMapping
    public ResponseEntity<DisposalResponse> dispose(@PathVariable String subjectCode,
            @Valid @RequestBody DisposeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(disposalService.dispose(subjectCode, request));
    }

    /** 处置进度：受试者维度逐条返回每个样本/分装的处置结果。 */
    @GetMapping
    public List<DisposalItemResponse> progress(@PathVariable String subjectCode) {
        return disposalItemRepository.findBySubjectCodeOrderByIdAsc(subjectCode).stream()
                .map(DisposalItemResponse::from)
                .toList();
    }
}
