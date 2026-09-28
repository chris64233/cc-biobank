package com.chris64233.cc.biobank.consent;

import com.chris64233.cc.biobank.common.ApiException;
import com.chris64233.cc.biobank.common.ErrorCode;
import com.chris64233.cc.biobank.consent.dto.ConsentVersionResponse;
import com.chris64233.cc.biobank.consent.dto.CreateSubjectRequest;
import com.chris64233.cc.biobank.consent.dto.RegisterConsentRequest;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsentService {

    private final SubjectRepository subjectRepository;
    private final ConsentVersionRepository consentVersionRepository;

    public ConsentService(SubjectRepository subjectRepository,
            ConsentVersionRepository consentVersionRepository) {
        this.subjectRepository = subjectRepository;
        this.consentVersionRepository = consentVersionRepository;
    }

    @Transactional
    public Subject createSubject(CreateSubjectRequest request) {
        if (subjectRepository.existsBySubjectCode(request.subjectCode())) {
            throw new ApiException(ErrorCode.DUPLICATE_EXTERNAL_ID,
                    "受试者编号已存在: " + request.subjectCode());
        }
        try {
            return subjectRepository.saveAndFlush(new Subject(request.subjectCode()));
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(ErrorCode.DUPLICATE_EXTERNAL_ID,
                    "受试者编号已存在: " + request.subjectCode());
        }
    }

    @Transactional(readOnly = true)
    public Subject getSubject(Long id) {
        return subjectRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("受试者不存在: " + id));
    }

    @Transactional(readOnly = true)
    public Subject getSubjectByCode(String code) {
        return subjectRepository.findBySubjectCode(code)
                .orElseThrow(() -> ApiException.notFound("受试者不存在: " + code));
    }

    @Transactional
    public ConsentVersion register(RegisterConsentRequest request) {
        Subject subject = getSubjectByCode(request.subjectCode());
        Instant validFrom = request.validFrom() != null ? request.validFrom() : Instant.now();
        if (request.validUntil() != null && !request.validUntil().isAfter(validFrom)) {
            throw ApiException.validation("同意失效时间必须晚于生效时间");
        }
        if (consentVersionRepository
                .findBySubjectIdAndVersion(subject.getId(), request.version()).isPresent()) {
            throw new ApiException(ErrorCode.DUPLICATE_CONSENT,
                    "同意版本已存在: " + subject.getSubjectCode() + "/" + request.version());
        }
        String purposes = request.allowedPurposes().stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(","));
        try {
            return consentVersionRepository.saveAndFlush(
                    new ConsentVersion(subject, request.version(), purposes, validFrom,
                            request.validUntil()));
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(ErrorCode.DUPLICATE_CONSENT,
                    "同意版本已存在: " + subject.getSubjectCode() + "/" + request.version());
        }
    }

    @Transactional(readOnly = true)
    public List<ConsentVersionResponse> listConsents(Long subjectId) {
        getSubject(subjectId);
        return consentVersionRepository.findBySubjectIdOrderByIdAsc(subjectId).stream()
                .map(ConsentVersionResponse::from)
                .toList();
    }
}
