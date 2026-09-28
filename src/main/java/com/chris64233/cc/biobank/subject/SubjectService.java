package com.chris64233.cc.biobank.subject;

import com.chris64233.cc.biobank.common.ApiException;
import com.chris64233.cc.biobank.subject.dto.ConsentResponse;
import com.chris64233.cc.biobank.subject.dto.CreateSubjectRequest;
import com.chris64233.cc.biobank.subject.dto.RegisterConsentRequest;
import com.chris64233.cc.biobank.subject.dto.SubjectResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final ConsentRepository consentRepository;

    public SubjectService(SubjectRepository subjectRepository,
            ConsentRepository consentRepository) {
        this.subjectRepository = subjectRepository;
        this.consentRepository = consentRepository;
    }

    @Transactional
    public SubjectResponse create(CreateSubjectRequest request) {
        String code = request.subjectCode().trim();
        if (subjectRepository.existsBySubjectCode(code)) {
            throw new ApiException(com.chris64233.cc.biobank.common.ErrorCode.DUPLICATE_SUBJECT_CODE,
                    "受试者编号已存在: " + code);
        }
        Subject subject;
        try {
            subject = subjectRepository.saveAndFlush(new Subject(code));
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(com.chris64233.cc.biobank.common.ErrorCode.DUPLICATE_SUBJECT_CODE,
                    "受试者编号已存在: " + code);
        }
        return SubjectResponse.from(subject);
    }

    @Transactional
    public ConsentResponse registerConsent(String subjectCode, RegisterConsentRequest request) {
        Subject subject = getByCode(subjectCode);
        String version = request.versionCode().trim();
        if (consentRepository.findBySubjectIdAndVersionCode(subject.getId(), version).isPresent()) {
            throw new ApiException(com.chris64233.cc.biobank.common.ErrorCode.DUPLICATE_CONSENT_VERSION,
                    "同意版本已存在: " + subjectCode + "/" + version);
        }

        Instant validFrom = request.validFrom() != null ? request.validFrom() : Instant.now();
        Instant validUntil = request.validUntil();
        if (validUntil != null && !validUntil.isAfter(validFrom)) {
            throw ApiException.validation("同意截止时间必须晚于生效时间");
        }

        Consent consent = new Consent(subject, version, request.allowedPurposes(),
                validFrom, validUntil);
        try {
            consent = consentRepository.saveAndFlush(consent);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(com.chris64233.cc.biobank.common.ErrorCode.DUPLICATE_CONSENT_VERSION,
                    "同意版本已存在: " + subjectCode + "/" + version);
        }
        return ConsentResponse.from(consent);
    }

    @Transactional(readOnly = true)
    public Subject getByCode(String subjectCode) {
        return subjectRepository.findBySubjectCode(subjectCode)
                .orElseThrow(() -> ApiException.notFound("受试者不存在: " + subjectCode));
    }

    @Transactional(readOnly = true)
    public List<Consent> listConsents(Long subjectId) {
        return consentRepository.findBySubjectIdOrderByIdAsc(subjectId);
    }

    /**
     * 接收样本时解析同意版本：必须存在且在接收时刻有效（未撤回、在有效期窗口内）。
     * 返回的 Consent 版本号将作为样本的同意快照。
     */
    @Transactional(readOnly = true)
    public Consent resolveConsentForReceipt(Subject subject, String versionCode, Instant at) {
        Consent consent = consentRepository
                .findBySubjectIdAndVersionCode(subject.getId(), versionCode)
                .orElseThrow(() -> new ApiException(
                        com.chris64233.cc.biobank.common.ErrorCode.CONSENT_INVALID,
                        "同意版本不存在: " + subject.getSubjectCode() + "/" + versionCode));
        if (consent.isWithdrawn()) {
            throw new ApiException(com.chris64233.cc.biobank.common.ErrorCode.CONSENT_INVALID,
                    "同意已撤回，不能接收新样本: " + versionCode);
        }
        if (at.isBefore(consent.getValidFrom())) {
            throw new ApiException(com.chris64233.cc.biobank.common.ErrorCode.CONSENT_INVALID,
                    "同意尚未生效: " + versionCode);
        }
        if (consent.getValidUntil() != null && !at.isBefore(consent.getValidUntil())) {
            throw new ApiException(com.chris64233.cc.biobank.common.ErrorCode.CONSENT_INVALID,
                    "同意已过期: " + versionCode);
        }
        return consent;
    }
}
