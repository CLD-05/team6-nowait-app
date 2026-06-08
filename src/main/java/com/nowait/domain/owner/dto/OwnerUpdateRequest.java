package com.nowait.domain.owner.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OwnerUpdateRequest {
	
	@NotBlank(message = "비밀번호는 필수 입력 항목입니다.")
	private String password;
	
	private String phoneNumber;

}
