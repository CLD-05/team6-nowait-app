package com.nowait.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignUpRequest(

    @NotBlank(message = "이메일을 입력해주세요.") @Email(message = "올바른 이메일 형식이 아닙니다.") @Size(max = 100, message = "이메일은 100자 이하여야 합니다.") String email,

    @NotBlank(message = "비밀번호를 입력해주세요.") @Size(min = 8, max = 255, message = "비밀번호는 8자 이상 255자 이하여야 합니다.") String password,

    @NotBlank(message = "이름을 입력해주세요.") @Size(max = 50, message = "이름은 50자 이하여야 합니다.") String name) {
}
