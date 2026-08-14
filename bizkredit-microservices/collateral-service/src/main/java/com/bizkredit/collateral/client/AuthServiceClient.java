package com.bizkredit.collateral.client;

import com.bizkredit.collateral.dto.ApiResponse;
import com.bizkredit.collateral.dto.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;


@FeignClient(name = "auth-service", configuration = com.bizkredit.collateral.config.FeignClientConfig.class)
public interface AuthServiceClient {

    @GetMapping("/api/users/role/{role}")
    ApiResponse<List<UserDTO>> getUsersByRole(@PathVariable("role") String role);
}
