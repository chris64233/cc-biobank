package com.chris64233.cc.biobank.sample.dto;

import com.chris64233.cc.biobank.aliquot.dto.AliquotResponse;
import com.chris64233.cc.biobank.event.EventResponse;
import java.util.List;

public record LineageResponse(
        SampleResponse sample,
        List<AliquotResponse> aliquots,
        List<EventResponse> events) {
}
