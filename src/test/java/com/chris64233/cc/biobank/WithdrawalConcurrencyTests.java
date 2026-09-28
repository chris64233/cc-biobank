package com.chris64233.cc.biobank;

import com.chris64233.cc.biobank.aliquot.Aliquot;
import com.chris64233.cc.biobank.aliquot.AliquotRepository;
import com.chris64233.cc.biobank.aliquot.AliquotStatus;
import com.chris64233.cc.biobank.common.ApiException;
import com.chris64233.cc.biobank.consent.ConsentVersionRepository;
import com.chris64233.cc.biobank.consent.Subject;
import com.chris64233.cc.biobank.consent.SubjectRepository;
import com.chris64233.cc.biobank.issue.IssueRecordRepository;
import com.chris64233.cc.biobank.issue.IssueService;
import com.chris64233.cc.biobank.issue.dto.IssueRequest;
import com.chris64233.cc.biobank.sample.Sample;
import com.chris64233.cc.biobank.sample.SampleRepository;
import com.chris64233.cc.biobank.sample.SampleService;
import com.chris64233.cc.biobank.sample.dto.ReceiveSampleRequest;
import com.chris64233.cc.biobank.withdrawal.WithdrawalService;
import com.chris64233.cc.biobank.withdrawal.dto.WithdrawConsentRequest;
import com.chris64233.cc.biobank.withdrawal.dto.WithdrawalResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 正在审批的领用与撤回并发时，每一轮只能得到"完整领用"或"完整冻结"之一，
 * 不允许出现部分领用 + 冻结、负库存或丢失更新。
 */
@SpringBootTest
class WithdrawalConcurrencyTests {

    @Autowired
    private IssueService issueService;
    @Autowired
    private WithdrawalService withdrawalService;
    @Autowired
    private SampleService sampleService;
    @Autowired
    private SubjectRepository subjectRepository;
    @Autowired
    private ConsentVersionRepository consentVersionRepository;
    @Autowired
    private AliquotRepository aliquotRepository;
    @Autowired
    private SampleRepository sampleRepository;
    @Autowired
    private IssueRecordRepository issueRecordRepository;
    @Autowired
    private com.chris64233.cc.biobank.aliquot.AliquotService aliquotService;

    private record Fixture(String code, Long aliquotId) {
    }

    private Fixture setup() {
        String code = "SUBJ-" + UUID.randomUUID();
        Subject subject = subjectRepository.save(new Subject(code));
        consentVersionRepository.save(new com.chris64233.cc.biobank.consent.ConsentVersion(
                subject, "v1", "RESEARCH_STORAGE,RESEARCH_USE",
                Instant.now().minusSeconds(60), null));
        Sample sample = sampleService.receive(new ReceiveSampleRequest(
                "EXT-" + UUID.randomUUID(), code, "v1", "BLOOD", new BigDecimal("100"),
                BigDecimal.ZERO, "FRIDGE-C3", null));
        var created = aliquotService.createAliquots(sample.getId(),
                new com.chris64233.cc.biobank.aliquot.dto.CreateAliquotsRequest(
                        null, null, List.of(new BigDecimal("100")), null));
        return new Fixture(code, created.aliquots().get(0).id());
    }

    @Test
    void issueAndWithdrawalAreMutuallyExclusive() throws Exception {
        int rounds = 24;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        AtomicInteger completeIssues = new AtomicInteger();
        AtomicInteger completeFreezes = new AtomicInteger();

        try {
            for (int round = 0; round < rounds; round++) {
                Fixture fixture = setup();
                CyclicBarrier barrier = new CyclicBarrier(2);

                Future<Boolean> issueFuture = pool.submit(() -> {
                    barrier.await();
                    try {
                        issueService.issue(new IssueRequest(UUID.randomUUID().toString(),
                                fixture.code(), "RESEARCH_USE", "v1",
                                List.of(new IssueRequest.Item(fixture.aliquotId(),
                                        new BigDecimal("100")))));
                        return true;
                    } catch (ApiException ex) {
                        // 唯一可接受的失败原因：撤回抢先完成（同意已撤回或分装已冻结）。
                        assertThat(ex.code().name())
                                .isIn("SAMPLE_FROZEN", "CONSENT_NOT_VALID");
                        return false;
                    }
                });

                Future<WithdrawalResponse> withdrawalFuture = pool.submit(() -> {
                    barrier.await();
                    return withdrawalService.withdraw(new WithdrawConsentRequest(
                            "WD-" + UUID.randomUUID(), fixture.code(), "concurrent"));
                });

                boolean issued = issueFuture.get();
                WithdrawalResponse withdrawal = withdrawalFuture.get();
                Aliquot aliquot = aliquotRepository.findById(fixture.aliquotId()).orElseThrow();

                if (issued) {
                    // 完整领用：分装耗尽，撤回冻结不到任何在库分装。
                    completeIssues.incrementAndGet();
                    assertThat(aliquot.getStatus()).isEqualTo(AliquotStatus.DEPLETED);
                    assertThat(aliquot.getRemainingVolume()).isEqualByComparingTo("0");
                    assertThat(withdrawal.frozenAliquotIds()).isEmpty();
                    assertThat(issueRecordRepository.findBySubjectIdOrderByIdAsc(
                            withdrawal.subjectId())).hasSize(1);
                } else {
                    // 完整冻结：分装保持 100 且 FROZEN，没有任何领用落库。
                    completeFreezes.incrementAndGet();
                    assertThat(aliquot.getStatus()).isEqualTo(AliquotStatus.FROZEN);
                    assertThat(aliquot.getRemainingVolume()).isEqualByComparingTo("100");
                    assertThat(withdrawal.frozenAliquotIds())
                            .containsExactly(fixture.aliquotId());
                    assertThat(issueRecordRepository.findBySubjectIdOrderByIdAsc(
                            withdrawal.subjectId())).isEmpty();
                }

                // 原始样本无论哪条分支都应被冻结（领用完后仍随撤回冻结，不影响历史领用）。
                assertThat(sampleRepository.findBySubjectIdOrderByIdAsc(
                        withdrawal.subjectId()).get(0).getStatus().name())
                        .isEqualTo("FROZEN");
            }
        } finally {
            pool.shutdown();
        }

        // 两种结果都应实际出现，且每轮恰为其一。
        assertThat(completeIssues.get() + completeFreezes.get()).isEqualTo(rounds);
        assertThat(completeIssues.get()).isGreaterThan(0);
        assertThat(completeFreezes.get()).isGreaterThan(0);
    }
}
