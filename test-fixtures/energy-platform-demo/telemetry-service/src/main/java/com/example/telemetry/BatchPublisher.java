package com.example.telemetry;

import org.springframework.stereotype.Service;

/** 遥测批处理发布器：flush 批量落盘时部分丢失（根因点）。 */
@Service
public class BatchPublisher {
    public void flush() {
        throw new IllegalStateException("batch lost");
    }
}
