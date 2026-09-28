package com.chris64233.cc.biobank.withdrawal;

import com.chris64233.cc.biobank.withdrawal.dto.WithdrawConsentRequest;
import com.chris64233.cc.biobank.withdrawal.dto.WithdrawalResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subjects/{subjectCode}/withdrawals")
public class WithdrawalController {

    private final WithdrawalService withdrawalService;
    private final WithdrawalRecordRepository withdrawalRecordRepository;

    public WithdrawalController(WithdrawalService withdrawalService,
            WithdrawalRecordRepository withdrawalRecordRepository) {
        this.withdrawalService = withdrawalService;
        this.withdrawalRecordRepository = withdrawalRecordRepository;
    }

    @PostMapping
    public ResponseEntity<WithdrawalResponse> withdraw(@PathVariable String subjectCode,
            @Valid @RequestBody WithdrawConsentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(withdrawalService.withdraw(subjectCode, request));
    }

    /** 受试者维度的撤回与冻结范围记录。 */
    @GetMapping
    public List<WithdrawalResponse> list(@PathVariable String subjectCode) {
        return withdrawalRecordRepository.findBySubjectCodeOrderByIdAsc(subjectCode).stream()
                .map(record -> withdrawalService.parseResponse(record.getResponseBody()))
                .toList();
    }
}
