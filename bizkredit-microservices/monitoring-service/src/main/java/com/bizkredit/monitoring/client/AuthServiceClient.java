package com.bizkredit.monitoring.client;

import com.bizkredit.monitoring.dto.ApiResponse;
import com.bizkredit.monitoring.dto.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;


@FeignClient(name = "auth-service", configuration = com.bizkredit.monitoring.config.FeignClientConfig.class)
public interface AuthServiceClient {

    @GetMapping("/api/users/{id}")
    ApiResponse<UserDTO> getUser(@PathVariable("id") Long id);

    @GetMapping("/api/users/role/{role}")
    ApiResponse<List<UserDTO>> getUsersByRole(@PathVariable("role") String role);
}
