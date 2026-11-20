package com.stockms.repository;

import com.stockms.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Users are not company-filtered (see AppUser); always pass the company when listing or loading. */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    List<AppUser> findByCompanyIdOrderByUsernameAsc(Long companyId);

    Optional<AppUser> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsByRole(AppUser.Role role);

    long countByCompanyId(Long companyId);

    @Query("select b.id from AppUser u join u.branches b where u.id = :userId")
    Set<Long> findBranchIds(Long userId);
}
