package com.bizkredit.credit.client;

import com.bizkredit.credit.dto.ApiResponse;
import com.bizkredit.credit.dto.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * Declarative HTTP client for auth-service.
 *
 * Replaces the local UserRef cross-schema read used to broadcast
 * notifications to every user holding a given role.
 */
@FeignClient(name = "auth-service", configuration = com.bizkredit.credit.config.FeignClientConfig.class)
public interface AuthServiceClient {

    @GetMapping("/api/users/role/{role}")
    ApiResponse<List<UserDTO>> getUsersByRole(@PathVariable("role") String role);
}
