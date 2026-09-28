package com.chris64233.cc.biobank.withdrawal;

import com.chris64233.cc.biobank.withdrawal.dto.WithdrawConsentRequest;
import com.chris64233.cc.biobank.withdrawal.dto.WithdrawalResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/withdrawals")
public class WithdrawalController {

    private final WithdrawalService withdrawalService;

    public WithdrawalController(WithdrawalService withdrawalService) {
        this.withdrawalService = withdrawalService;
    }

    @PostMapping
    public ResponseEntity<WithdrawalResponse> withdraw(
            @Valid @RequestBody WithdrawConsentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(withdrawalService.withdraw(request));
    }
}
