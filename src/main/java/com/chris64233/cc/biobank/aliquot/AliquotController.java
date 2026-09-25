package com.chris64233.cc.biobank.aliquot;

import com.chris64233.cc.biobank.aliquot.dto.AliquotResponse;
import com.chris64233.cc.biobank.aliquot.dto.CreateAliquotsRequest;
import com.chris64233.cc.biobank.aliquot.dto.CreateAliquotsResponse;
import com.chris64233.cc.biobank.event.EventResponse;
import com.chris64233.cc.biobank.event.SampleEventRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AliquotController {

    private final AliquotService aliquotService;
    private final SampleEventRepository eventRepository;

    public AliquotController(AliquotService aliquotService, SampleEventRepository eventRepository) {
        this.aliquotService = aliquotService;
        this.eventRepository = eventRepository;
    }

    @PostMapping("/api/samples/{sampleId}/aliquots")
    public ResponseEntity<CreateAliquotsResponse> create(@PathVariable Long sampleId,
            @RequestBody CreateAliquotsRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(aliquotService.createAliquots(sampleId, request));
    }

    @GetMapping("/api/aliquots/{id}")
    public AliquotResponse get(@PathVariable Long id) {
        return AliquotResponse.from(aliquotService.getById(id));
    }

    @GetMapping("/api/aliquots/{id}/events")
    @Transactional(readOnly = true)
    public List<EventResponse> events(@PathVariable Long id) {
        aliquotService.getById(id);
        return eventRepository.findByAliquotIdOrderByIdAsc(id).stream()
                .map(EventResponse::from)
                .toList();
    }
}
