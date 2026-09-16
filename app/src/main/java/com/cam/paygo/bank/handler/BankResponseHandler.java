package com.cam.paygo.bank.handler;

import com.cam.paygo.bank.BankResponse;

public interface BankResponseHandler {
    void handle(BankResponse response);
}