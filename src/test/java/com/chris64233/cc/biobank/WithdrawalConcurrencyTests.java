package com.chris64233.cc.biobank;

import com.chris64233.cc.biobank.aliquot.Aliquot;
import com.chris64233.cc.biobank.aliquot.AliquotRepository;
import com.chris64233.cc.biobank.aliquot.AliquotService;
import com.chris64233.cc.biobank.aliquot.AliquotStatus;
import com.chris64233.cc.biobank.common.ApiException;
import com.chris64233.cc.biobank.event.EventType;
import com.chris64233.cc.biobank.event.SampleEvent;
import com.chris64233.cc.biobank.event.SampleEventRepository;
import com.chris64233.cc.biobank.issue.IssueRecord;
import com.chris64233.cc.biobank.issue.IssueRecordRepository;
import com.chris64233.cc.biobank.issue.IssueService;
import com.chris64233.cc.biobank.issue.dto.IssueRequest;
import com.chris64233.cc.biobank.sample.Sample;
import com.chris64233.cc.biobank.sample.SampleService;
import com.chris64233.cc.biobank.sample.dto.ReceiveSampleRequest;
import com.chris64233.cc.biobank.subject.Consent;
import com.chris64233.cc.biobank.subject.ConsentRepository;
import com.chris64233.cc.biobank.subject.Subject;
import com.chris64233.cc.biobank.subject.SubjectRepository;
import com.chris64233.cc.biobank.withdrawal.WithdrawalService;
import com.chris64233.cc.biobank.withdrawal.dto.WithdrawConsentRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class WithdrawalConcurrencyTests {

    @Autowired
    private IssueService issueService;

    @Autowired
    private WithdrawalService withdrawalService;

    @Autowired
    private SampleService sampleService;

    @Autowired
    private AliquotService aliquotService;

    @Autowired
    private AliquotRepository aliquotRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private ConsentRepository consentRepository;

    @Autowired
    private IssueRecordRepository issueRecordRepository;

    @Autowired
    private SampleEventRepository eventRepository;

    @Test
    void issueAndWithdrawalProduceExactlyOneCompleteOutcome() throws Exception {
        // 多轮运行提升并发交错覆盖面；每一轮的结果都必须满足不变式。
        int rounds = 10;
        int issueWins = 0;
        int withdrawalWins = 0;
        for (int round = 0; round < rounds; round++) {
            String subjectCode = "SUBJ-CONC-" + UUID.randomUUID();
            subjectRepository.save(new Subject(subjectCode));
            Subject subject = subjectRepository.findBySubjectCode(subjectCode).orElseThrow();
            consentRepository.save(new Consent(subject, "v1",
                    new HashSet<>(List.of("RESEARCH")), Instant.now().minusSeconds(3600), null));

            Sample sample = sampleService.receive(new ReceiveSampleRequest(subjectCode, "v1",
                    "EXT-" + UUID.randomUUID(), "BLOOD", new BigDecimal("100"),
                    BigDecimal.ZERO, "FREEZER-1", null));
            Long aliquotId = aliquotService.createAliquots(sample.getId(),
                    new com.chris64233.cc.biobank.aliquot.dto.CreateAliquotsRequest(
                            null, null, List.of(new BigDecimal("100")), null))
                    .aliquots().get(0).id();

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CyclicBarrier barrier = new CyclicBarrier(2);
            ConcurrentLinkedQueue<String> outcomes = new ConcurrentLinkedQueue<>();
            ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

            pool.submit(() -> {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                    issueService.issue(new IssueRequest(UUID.randomUUID().toString(), subjectCode,
                            "v1", "RESEARCH",
                            List.of(new IssueRequest.Item(aliquotId, new BigDecimal("40")))));
                    outcomes.add("ISSUED");
                } catch (ApiException ex) {
                    outcomes.add("ISSUE_REJECTED:" + ex.code().name());
                } catch (Throwable t) {
                    errors.add(t);
                }
            });
            pool.submit(() -> {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                    withdrawalService.withdraw(subjectCode, new WithdrawConsentRequest(
                            UUID.randomUUID().toString(), null, "test"));
                    outcomes.add("WITHDRAWN");
                } catch (Throwable t) {
                    errors.add(t);
                }
            });
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
            assertThat(errors).isEmpty();
            assertThat(outcomes).contains("WITHDRAWN");

            Aliquot aliquot = aliquotRepository.findById(aliquotId).orElseThrow();
            List<IssueRecord> issueRecords =
                    issueRecordRepository.findBySubjectCodeOrderByIdAsc(subjectCode);
            List<SampleEvent> issuedEvents = eventRepository
                    .findByAliquotIdOrderByIdAsc(aliquotId).stream()
                    .filter(e -> e.getEventType() == EventType.ALIQUOT_ISSUED)
                    .toList();
            List<SampleEvent> frozenEvents = eventRepository
                    .findByAliquotIdOrderByIdAsc(aliquotId).stream()
                    .filter(e -> e.getEventType() == EventType.ALIQUOT_FROZEN)
                    .toList();

            if (outcomes.contains("ISSUED")) {
                // 完整领用先提交（扣 40 剩 60），撤回随后冻结剩余库存：
                // 状态 FROZEN、体积 60，领用事件与幂等记录各恰好一条，冻结事件一条。
                issueWins++;
                assertThat(outcomes).noneMatch(o -> o.startsWith("ISSUE_REJECTED"));
                assertThat(aliquot.getStatus()).isEqualTo(AliquotStatus.FROZEN);
                assertThat(aliquot.getRemainingVolume()).isEqualByComparingTo("60.000");
                assertThat(issueRecords).hasSize(1);
                assertThat(issuedEvents).hasSize(1);
                assertThat(issuedEvents.get(0).getVolumeChange()).isEqualByComparingTo("-40.000");
                assertThat(issuedEvents.get(0).getResultingVolume())
                        .isEqualByComparingTo("60.000");
                assertThat(frozenEvents).hasSize(1);
                assertThat(frozenEvents.get(0).getResultingVolume())
                        .isEqualByComparingTo("60.000");
            } else {
                // 完整冻结先生效：领用整体失败回滚，体积保持 100，无任何领用痕迹。
                withdrawalWins++;
                assertThat(outcomes).anyMatch(o -> o.startsWith("ISSUE_REJECTED"));
                assertThat(aliquot.getStatus()).isEqualTo(AliquotStatus.FROZEN);
                assertThat(aliquot.getRemainingVolume()).isEqualByComparingTo("100.000");
                assertThat(issueRecords).isEmpty();
                assertThat(issuedEvents).isEmpty();
                assertThat(frozenEvents).hasSize(1);
                assertThat(frozenEvents.get(0).getResultingVolume())
                        .isEqualByComparingTo("100.000");
            }
        }
        // 每一轮都只允许完整领用或完整冻结；统计仅用于观察两种交错的覆盖情况，
        // 不对调度器的先后顺序做硬性断言以免在高负载 CI 上偶发失败。
        System.out.printf("issue-first=%d withdrawal-first=%d%n", issueWins, withdrawalWins);
    }
}
