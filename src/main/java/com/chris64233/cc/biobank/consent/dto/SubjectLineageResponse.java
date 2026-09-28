package com.chris64233.cc.biobank.consent.dto;

import com.chris64233.cc.biobank.aliquot.dto.AliquotResponse;
import com.chris64233.cc.biobank.event.EventResponse;
import com.chris64233.cc.biobank.sample.dto.SampleResponse;
import java.util.List;

/**
 * 受试者维度的样本谱系：受试者、同意版本、全部原始样本、全部后代分装与按序事件。
 */
public record SubjectLineageResponse(
        SubjectResponse subject,
        List<ConsentVersionResponse> consents,
        List<SampleResponse> samples,
        List<AliquotResponse> aliquots,
        List<EventResponse> events) {
}
