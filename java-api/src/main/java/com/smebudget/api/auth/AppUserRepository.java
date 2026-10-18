package com.smebudget.api.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reads users from app_user. Emails are matched without caring about upper or lower case. */
public interface AppUserRepository extends JpaRepository<AppUser, String> {

    Optional<AppUser> findByEmailIgnoreCase(String email);
}
