package com.nowait.domain.waiting.dto;

import java.time.LocalDateTime;

import com.nowait.domain.waiting.entity.WaitingCallLog;

import lombok.Getter;

@Getter
public class WaitingCallLogResponse {
	
	private final Long logId;
	private final Integer callSequence;
	private final LocalDateTime calledAt;
	
	public WaitingCallLogResponse(WaitingCallLog log) {
		this.logId = log.getLogId();
		this.callSequence = log.getCallSequence();
		this.calledAt = log.getCalledAt();
	}

}
