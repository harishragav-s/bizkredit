package com.bizkredit.sme.client;

import com.bizkredit.sme.dto.ApiResponse;
import com.bizkredit.sme.dto.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;


@FeignClient(name = "auth-service", configuration = com.bizkredit.sme.config.FeignClientConfig.class)
public interface AuthServiceClient {

    @GetMapping("/api/users/role/{role}")
    ApiResponse<List<UserDTO>> getUsersByRole(@PathVariable("role") String role);
}
