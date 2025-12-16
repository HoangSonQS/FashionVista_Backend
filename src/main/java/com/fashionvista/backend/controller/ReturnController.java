package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.CreateReturnRequestRequest;
import com.fashionvista.backend.dto.ReturnRequestResponse;
import com.fashionvista.backend.service.ReturnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/returns")
@RequiredArgsConstructor
public class ReturnController {

    private final ReturnService returnService;

    @PostMapping
    public ReturnRequestResponse create(@RequestBody @Valid CreateReturnRequestRequest request) {
        return returnService.createReturnRequest(request);
    }

    @GetMapping
    public Page<ReturnRequestResponse> getMyReturns(Pageable pageable) {
        return returnService.getMyReturns(pageable);
    }
}


