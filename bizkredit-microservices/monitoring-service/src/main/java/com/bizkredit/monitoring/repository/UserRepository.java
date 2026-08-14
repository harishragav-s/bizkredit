package com.bizkredit.monitoring.repository;

import com.bizkredit.monitoring.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// Read access to the users table (owned/written by auth-service).
// monitoring-service uses this to attach notifications to a user.
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
}
