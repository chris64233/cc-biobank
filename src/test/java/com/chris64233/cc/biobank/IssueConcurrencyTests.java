package com.chris64233.cc.biobank;

import com.chris64233.cc.biobank.aliquot.Aliquot;
import com.chris64233.cc.biobank.aliquot.AliquotRepository;
import com.chris64233.cc.biobank.common.ApiException;
import com.chris64233.cc.biobank.common.ErrorCode;
import com.chris64233.cc.biobank.issue.IssueService;
import com.chris64233.cc.biobank.issue.dto.IssueRequest;
import com.chris64233.cc.biobank.issue.dto.IssueResponse;
import com.chris64233.cc.biobank.sample.Sample;
import com.chris64233.cc.biobank.sample.SampleService;
import com.chris64233.cc.biobank.sample.dto.ReceiveSampleRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class IssueConcurrencyTests {

    @Autowired
    private IssueService issueService;

    @Autowired
    private SampleService sampleService;

    @Autowired
    private AliquotRepository aliquotRepository;

    @Autowired
    private com.chris64233.cc.biobank.aliquot.AliquotService aliquotService;

    @Test
    void concurrentIssuesNeverGoNegativeOrLoseUpdates() throws Exception {
        Sample sample = sampleService.receive(new ReceiveSampleRequest(
                "EXT-" + UUID.randomUUID(), "BLOOD", new BigDecimal("100"),
                BigDecimal.ZERO, "FRIDGE-C3", null));
        var created = aliquotService.createAliquots(sample.getId(),
                new com.chris64233.cc.biobank.aliquot.dto.CreateAliquotsRequest(
                        null, null, List.of(new BigDecimal("100")), null));
        Long aliquotId = created.aliquots().get(0).id();

        int threads = 10;
        BigDecimal volumePerIssue = new BigDecimal("15");
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<IssueResponse> successes = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<ApiException> failures = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    IssueRequest request = new IssueRequest(UUID.randomUUID().toString(),
                            List.of(new IssueRequest.Item(aliquotId, volumePerIssue)));
                    successes.add(issueService.issue(request));
                } catch (ApiException ex) {
                    failures.add(ex);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(successes).hasSize(6);
        assertThat(failures).hasSize(4);
        assertThat(failures).allMatch(ex -> ex.code() == ErrorCode.INSUFFICIENT_STOCK);

        Aliquot aliquot = aliquotRepository.findById(aliquotId).orElseThrow();
        assertThat(aliquot.getRemainingVolume()).isEqualByComparingTo("10.000");
        assertThat(aliquot.getRemainingVolume().signum()).isGreaterThanOrEqualTo(0);
    }
}
