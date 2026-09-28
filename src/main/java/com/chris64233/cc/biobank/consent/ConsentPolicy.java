package com.chris64233.cc.biobank.consent;

import com.chris64233.cc.biobank.common.ApiException;
import com.chris64233.cc.biobank.common.ErrorCode;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 同意校验：所有需要"同意支持"的写操作（接收、领用）都经由本组件，
 * 并在加锁的同意版本行上判断有效性，保证与撤回互斥。
 *
 * <p>使用 {@link Propagation#MANDATORY}：必须运行在调用方事务内，
 * 锁在整个业务事务（含库存扣减/冻结）期间保持。
 */
@Component
public class ConsentPolicy {

    private final SubjectRepository subjectRepository;
    private final ConsentVersionRepository consentVersionRepository;

    public ConsentPolicy(SubjectRepository subjectRepository,
            ConsentVersionRepository consentVersionRepository) {
        this.subjectRepository = subjectRepository;
        this.consentVersionRepository = consentVersionRepository;
    }

    /** 锁定受试者行：领用与撤回通过它串行化，互不重叠。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public Subject lockSubject(Long subjectId) {
        return subjectRepository.findByIdForUpdate(subjectId)
                .orElseThrow(() -> ApiException.notFound("受试者不存在: " + subjectId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Subject requireSubjectByCode(String subjectCode) {
        return subjectRepository.findBySubjectCode(subjectCode)
                .orElseThrow(() -> ApiException.notFound("受试者不存在: " + subjectCode));
    }

    /**
     * 锁定受试者及其指定同意版本，并在当前时刻校验该同意支持给定用途。
     * 不满足（不存在、未生效、已过期、已撤回或用途不匹配）一律抛 CONSENT_NOT_VALID。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ConsentVersion requireValidConsent(Long subjectId, String version, String purpose) {
        lockSubject(subjectId);
        ConsentVersion consent = consentVersionRepository
                .findBySubjectIdAndVersionForUpdate(subjectId, version)
                .orElseThrow(() -> new ApiException(ErrorCode.CONSENT_NOT_VALID,
                        "同意版本不存在: 受试者 " + subjectId + " 版本 " + version));
        if (!consent.supports(purpose, Instant.now())) {
            throw new ApiException(ErrorCode.CONSENT_NOT_VALID,
                    describe(consent, purpose));
        }
        return consent;
    }

    private String describe(ConsentVersion consent, String purpose) {
        if (consent.getWithdrawnAt() != null) {
            return "同意已撤回: 版本 " + consent.getVersion();
        }
        if (consent.getValidUntil() != null && consent.getValidUntil().isBefore(Instant.now())) {
            return "同意已过期: 版本 " + consent.getVersion();
        }
        if (consent.getValidFrom().isAfter(Instant.now())) {
            return "同意尚未生效: 版本 " + consent.getVersion();
        }
        return "同意用途不匹配: 版本 " + consent.getVersion() + " 不允许用途 " + purpose;
    }
}
