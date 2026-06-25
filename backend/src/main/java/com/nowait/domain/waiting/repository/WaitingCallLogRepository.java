package com.nowait.domain.waiting.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nowait.domain.waiting.entity.Waiting;
import com.nowait.domain.waiting.entity.WaitingCallLog;

public interface WaitingCallLogRepository extends JpaRepository<WaitingCallLog, Long> {
	
	int countByWaiting(Waiting waiting);
	
	List<WaitingCallLog> findAllByWaitingOrderByCallSequenceAsc(Waiting waiting);

}
