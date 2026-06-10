package com.nowait.domain.waiting.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "waiting_call_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WaitingCallLog {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "log_id")
	private Long logId;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "waiting_id", nullable = false)
	private Waiting waiting;
	
	@Column(name = "call_sequence", nullable = false)
	private Integer callSequence;
	
	@Column(name = "called_at", nullable = false, updatable = false)
	private LocalDateTime calledAt;
	
	
	@Builder
	public WaitingCallLog(Waiting waiting, Integer callSequence, LocalDateTime calledAt) {
		this.waiting = waiting;
		this.callSequence = (callSequence != null) ? callSequence : 1;
		this.calledAt = calledAt;
	}

}
