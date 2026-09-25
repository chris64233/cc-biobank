package com.chris64233.cc.biobank.sample;

import com.chris64233.cc.biobank.aliquot.AliquotRepository;
import com.chris64233.cc.biobank.aliquot.dto.AliquotResponse;
import com.chris64233.cc.biobank.event.EventResponse;
import com.chris64233.cc.biobank.event.SampleEventRepository;
import com.chris64233.cc.biobank.sample.dto.LineageResponse;
import com.chris64233.cc.biobank.sample.dto.ReceiveSampleRequest;
import com.chris64233.cc.biobank.sample.dto.SampleResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/samples")
public class SampleController {

    private final SampleService sampleService;
    private final AliquotRepository aliquotRepository;
    private final SampleEventRepository eventRepository;

    public SampleController(SampleService sampleService, AliquotRepository aliquotRepository,
            SampleEventRepository eventRepository) {
        this.sampleService = sampleService;
        this.aliquotRepository = aliquotRepository;
        this.eventRepository = eventRepository;
    }

    @PostMapping
    public ResponseEntity<SampleResponse> receive(@Valid @RequestBody ReceiveSampleRequest request) {
        Sample sample = sampleService.receive(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(SampleResponse.from(sample));
    }

    @GetMapping("/{id}")
    public SampleResponse get(@PathVariable Long id) {
        return SampleResponse.from(sampleService.getById(id));
    }

    @GetMapping("/{id}/events")
    @Transactional(readOnly = true)
    public List<EventResponse> events(@PathVariable Long id) {
        sampleService.getById(id);
        return eventRepository.findBySampleIdOrderByIdAsc(id).stream()
                .map(EventResponse::from)
                .toList();
    }

    @GetMapping("/{id}/lineage")
    @Transactional(readOnly = true)
    public LineageResponse lineage(@PathVariable Long id) {
        Sample sample = sampleService.getById(id);
        List<AliquotResponse> aliquots = aliquotRepository.findBySampleIdOrderByIdAsc(id).stream()
                .map(AliquotResponse::from)
                .toList();
        List<EventResponse> events = eventRepository.findBySampleIdOrderByIdAsc(id).stream()
                .map(EventResponse::from)
                .toList();
        return new LineageResponse(SampleResponse.from(sample), aliquots, events);
    }
}
