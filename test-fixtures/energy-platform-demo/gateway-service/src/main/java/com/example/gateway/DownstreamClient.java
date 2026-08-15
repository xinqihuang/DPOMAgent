package com.example.gateway;

import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;

/** 下游客户端：call 超时未重试控制，触发重试风暴（根因点）。 */
@Component
public class DownstreamClient {
    public void call() throws SocketTimeoutException {
        throw new SocketTimeoutException("timeout");
    }
}
